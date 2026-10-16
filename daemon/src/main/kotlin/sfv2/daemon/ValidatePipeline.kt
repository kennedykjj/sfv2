package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.store.CardEventStore

/** Judges the card's worktree before the review; the verdict is the gates', never the agent's report. */
fun interface ValidateGates {
    fun evaluate(cardId: String, worktree: Path): ValidateGateResults
}

/**
 * One pass of the validate stage: syncs the branch with main, reruns the gates, runs the review and moves the card on
 * the word of the gates and of the review's verdict line.
 */
@Suppress("LongParameterList")
class ValidatePipeline(
    private val project: Project,
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val worktrees: WorktreeManager,
    private val sessions: McpSessions,
    private val mcpPort: Int,
    private val runtime: Runtime,
    private val costRecorder: RunCostRecorder,
    private val branchSync: BranchSync,
    private val gates: ValidateGates,
    private val configDir: Path,
    private val clock: Clock,
    private val model: String,
    private val maxRounds: Int = DEFAULT_VALIDATE_MAX_ROUNDS,
    private val log: DaemonLog = StderrDaemonLog,
    private val runtimeName: String = "claude",
    private val delivery: PullRequestDelivery? = null,
    private val changedPaths: ChangedPaths = GitChangedPaths(),
) {
    private val outcome = ValidationOutcome(store, recorder, project.layers.map { it.name }.toSet(), clock)

    init {
        require(maxRounds > 0) { "maxRounds must be positive, got $maxRounds" }
    }

    fun run(cardId: String) {
        val card = store.card(cardId) ?: throw GateCardNotFoundException(cardId)
        if (card.column != Column.VALIDATION) {
            log.log("validate_skipped", mapOf("card" to cardId, "column" to card.column.name))
            return
        }
        val worktree = worktrees.ensure(project, cardId, card.title)
        when (branchSync.mergeMain(worktree.path)) {
            MergeResult.Merged -> Unit
            MergeResult.Conflict -> return block(cardId, "merge_conflict")
            MergeResult.Failed -> return block(cardId, "git_failed")
        }
        for (round in 1..maxRounds) {
            val results = gates.evaluate(cardId, worktree.path)
            val report = review(cardId, worktree.path)
            val decision = try {
                report?.let { decide(cardId, worktree, it, results.state) } ?: ValidationDecision.Malformed
            } catch (e: PushRefusedException) {
                return block(cardId, "push_refused")
            }
            log.log("validate_round", mapOf("card" to cardId, "round" to round.toString(), "decision" to decision.javaClass.simpleName))
            when (decision) {
                ValidationDecision.Staged, is ValidationDecision.Returned -> return
                is ValidationDecision.CiUnresolved -> return block(cardId, decision.reason)
                ValidationDecision.Held -> return block(cardId, "gates_failed: " + results.failedNames.joinToString(","))
                ValidationDecision.Malformed -> Unit
            }
        }
        block(cardId, "review_malformed")
    }

    private fun decide(cardId: String, worktree: CardWorktree, report: String, gates: GatesState): ValidationDecision =
        outcome.apply(
            cardId, report, gates,
            ci = { deliver(cardId, worktree) },
            head = { delivery?.headOf(worktree.path) },
            redLayer = { layerWithMostChanges(worktree.path) },
        )

    /** Without a delivery wired (tests of the earlier stages) there is no CI step and the approval stands on the gates. */
    private fun deliver(cardId: String, worktree: CardWorktree): CiResult {
        val title = store.card(cardId)?.title ?: cardId
        return delivery?.deliver(cardId, worktree.branch, title, "Card $cardId", worktree.path) ?: CiResult.Green
    }

    private fun layerWithMostChanges(worktree: Path): String {
        val changed = try {
            changedPaths.between(worktree, "origin/main")
        } catch (e: ChangedPathsException) {
            emptyList()
        }
        val layers = project.layers
        return layers.maxByOrNull { layer -> changed.count { it.startsWith(layer.path) } }?.name ?: layers.first().name
    }

    /** Runs one review session and returns the report the agent wrote during it, or null when it wrote none. */
    private fun review(cardId: String, worktree: Path): String? {
        val before = store.lastSeq(cardId, EventType.AGENT_REPORTED) ?: 0L
        val token = sessions.open(project.repo, "validate", cardId)
        val config = writeMcpConfig(configDir, mcpPort, token)
        try {
            val result = runtime.launch(SessionRequest("validate", worktree, model, config))
            costRecorder.record(cardId, UUID.randomUUID().toString(), runtimeName, "validate", model, result)
        } finally {
            runCatching { Files.deleteIfExists(config) }
            sessions.close(token)
        }
        return store.eventsAfter(before)
            .lastOrNull { it.cardId == cardId && it.event.type == EventType.AGENT_REPORTED }
            ?.event?.payload?.get("summary")
    }

    private fun block(cardId: String, reason: String) {
        val current = store.card(cardId) ?: throw GateCardNotFoundException(cardId)
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory(STAGE),
            type = EventType.CARD_BLOCKED,
            payload = mapOf("reason" to reason),
        )
        recorder.record(cardId, current, event)
        log.log("validate_blocked", mapOf("card" to cardId, "reason" to reason))
    }

    private companion object {
        const val STAGE = "validate"
    }
}

/** Starts the validate pipeline on its own thread; the card's pool lock is released whatever the outcome. */
class ValidateStageLauncher(
    private val pipelineFor: (String) -> ValidatePipeline,
    private val pool: RunPool,
    private val log: DaemonLog = StderrDaemonLog,
    private val onRunFinished: (String) -> Unit = {},
) : StageLauncher {

    override fun launch(stage: DispatchStage, cardId: String): LaunchedRun {
        require(stage == DispatchStage.VALIDATE) { "ValidateStageLauncher cannot launch stage $stage (card $cardId)" }
        val thread = Thread({ execute(cardId) }, "validate-$cardId").apply { isDaemon = true }
        thread.start()
        val handle = ProcessHandle.current()
        return LaunchedRun(handle.pid(), handle.info().startInstant().orElse(Instant.now()))
    }

    private fun execute(cardId: String) {
        try {
            awaitLock(cardId)
            pipelineFor(cardId).run(cardId)
        } catch (e: Exception) {
            log.log("validate_failed", mapOf("card" to cardId, "error" to e.javaClass.simpleName))
        } finally {
            try {
                onRunFinished(cardId)
            } catch (e: Exception) {
                log.log("run_finished_hook_failed", mapOf("card" to cardId, "error" to e.javaClass.simpleName))
            } finally {
                pool.release(cardId)
            }
        }
    }

    /** The dispatcher takes the lock right after [launch] returns; releasing before that would leak it. */
    private fun awaitLock(cardId: String) {
        val deadline = System.nanoTime() + LOCK_WAIT_NANOS
        while (!pool.isLocked(cardId) && System.nanoTime() < deadline) Thread.sleep(LOCK_POLL_MILLIS)
    }

    private companion object {
        const val LOCK_WAIT_NANOS = 5_000_000_000L
        const val LOCK_POLL_MILLIS = 5L
    }
}
