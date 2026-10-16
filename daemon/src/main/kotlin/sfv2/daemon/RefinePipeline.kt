package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult
import sfv2.store.CardEventStore

data class GateResult(val name: String, val passed: Boolean, val reason: String?)

/** Judges the worktree after a refine run; the verdict is the gates', never the agent's report. */
fun interface RefineGates {
    fun evaluate(cardId: String, worktree: Path): List<GateResult>
}

/** Spec test plan, spec weight and scope (only spec and plan files may change) over the card's worktree. */
class SpecScopeRefineGates(
    private val project: Project,
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val changedPaths: ChangedPaths,
    private val clock: Clock,
    private val baseRevision: String = "main",
) : RefineGates {

    override fun evaluate(cardId: String, worktree: Path): List<GateResult> {
        val changed = (changedPaths.between(worktree, baseRevision) + changedPaths.untracked(worktree)).distinct()
        val results = specGates(worktree, changed) + scopeGate(changed)
        val card = store.card(cardId)
        if (card != null) results.forEach { record(cardId, card, it) }
        return results
    }

    private fun specGates(worktree: Path, changed: List<String>): List<GateResult> {
        val specPath = changed.filter { it.startsWith(SPEC_DIR) && it.endsWith(".md") }
            .maxByOrNull { Files.getLastModifiedTime(worktree.resolve(it)).toMillis() }
        if (specPath == null) {
            return listOf(GateResult("test-plan", false, "no spec file written under $SPEC_DIR"), GateResult("weight", true, null))
        }
        val layers = project.layers.map { SpecLayer(it.name, it.path, listOf("${it.path.trimEnd('/')}/**")) }
        val check = SpecGates.check(Files.readString(worktree.resolve(specPath)), layers)
        return SpecGate.entries.map { gate ->
            val findings = check.findings.filter { it.gate == gate }
            val name = gate.name.lowercase().replace('_', '-')
            GateResult(name, findings.isEmpty(), findings.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.message })
        }
    }

    private fun scopeGate(changed: List<String>): GateResult =
        when (val verdict = ScopeGate.check(project, changed, emptySet())) {
            ScopeVerdict.Pass -> GateResult("scope", true, null)
            is ScopeVerdict.Violations ->
                GateResult("scope", false, "forbidden=${verdict.forbidden.size} outside=${verdict.outsideLayers.size}")
        }

    private fun record(cardId: String, card: Card, result: GateResult) {
        val payload = buildMap {
            put("gate", result.name)
            put("verdict", if (result.passed) "PASS" else "FAIL")
            result.reason?.let { put("summary", it) }
        }
        recorder.record(
            cardId,
            card,
            Event(EventId(UUID.randomUUID().toString()), clock.instant(), Actor.Factory("gate"), EventType.GATE_FINISHED, payload),
        )
    }

    private companion object {
        const val SPEC_DIR = "docs/superpowers/specs/"
    }
}

/** One pass of the refine stage: runs the agent, judges it with gates, and moves the card on the gates' word. */
@Suppress("LongParameterList")
class RefinePipeline(
    private val project: Project,
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val worktrees: WorktreeManager,
    private val sessions: McpSessions,
    private val mcpPort: Int,
    private val runtime: Runtime,
    private val costRecorder: RunCostRecorder,
    private val gates: RefineGates,
    private val configDir: Path,
    private val clock: Clock,
    private val model: String,
    private val maxRounds: Int = 3,
    private val log: DaemonLog = StderrDaemonLog,
    private val runtimeName: String = "claude",
) {
    init {
        require(maxRounds > 0) { "maxRounds must be positive, got $maxRounds" }
    }

    fun run(cardId: String) {
        val card = store.card(cardId) ?: throw GateCardNotFoundException(cardId)
        when (card.column) {
            Column.READY_FOR_REFINEMENT -> move(cardId, Column.REFINEMENT)
            Column.REFINEMENT -> Unit
            else -> {
                log.log("refine_skipped", mapOf("card" to cardId, "column" to card.column.name))
                return
            }
        }
        val worktree = worktrees.ensure(project, cardId, card.title)
        var failed: List<GateResult> = emptyList()
        for (round in 1..maxRounds) {
            runSession(cardId, worktree.path)
            failed = gates.evaluate(cardId, worktree.path).filter { !it.passed }
            log.log("refine_round", mapOf("card" to cardId, "round" to round.toString(), "failed" to failed.size.toString()))
            if (failed.isEmpty()) {
                move(cardId, Column.SPEC_REVIEW)
                return
            }
        }
        block(cardId, failed)
    }

    private fun runSession(cardId: String, worktree: Path) {
        val token = sessions.open(project.repo, "refine", cardId)
        val config = writeConfig(token)
        try {
            val result = runtime.launch(SessionRequest("refine", worktree, model, config))
            recordCost(cardId, result)
        } finally {
            runCatching { Files.deleteIfExists(config) }
            sessions.close(token)
        }
    }

    private fun writeConfig(token: String): Path {
        Files.createDirectories(configDir)
        val file = configDir.resolve("mcp-${UUID.randomUUID()}.json")
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        val json = buildJsonObject {
            putJsonObject("mcpServers") {
                putJsonObject("sfv2") {
                    put("type", "http")
                    put("url", "http://127.0.0.1:$mcpPort/mcp")
                    putJsonObject("headers") { put("Authorization", "Bearer $token") }
                }
            }
        }
        Files.writeString(file, json.toString())
        return file
    }

    private fun recordCost(cardId: String, result: SessionResult) {
        costRecorder.record(cardId, UUID.randomUUID().toString(), runtimeName, "refine", model, result)
    }

    private fun move(cardId: String, to: Column) {
        val current = store.card(cardId) ?: throw GateCardNotFoundException(cardId)
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory("refine"),
            type = EventType.CARD_MOVED,
            payload = mapOf("from" to current.column.name, "to" to to.name),
        )
        recorder.record(cardId, Card.create(current.title, to), event)
        log.log("refine_moved", mapOf("card" to cardId, "to" to to.name))
    }

    private fun block(cardId: String, failed: List<GateResult>) {
        val current = store.card(cardId) ?: throw GateCardNotFoundException(cardId)
        val reason = "gates_failed: " + failed.joinToString(",") { it.name }
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory("refine"),
            type = EventType.CARD_BLOCKED,
            payload = mapOf("reason" to reason),
        )
        recorder.record(cardId, current, event)
        log.log("refine_blocked", mapOf("card" to cardId, "reason" to reason))
    }
}

/** Starts the refine pipeline on its own thread; the card's pool lock is released whatever the outcome. */
class RefineStageLauncher(
    private val pipelineFor: (String) -> RefinePipeline,
    private val pool: RunPool,
    private val log: DaemonLog = StderrDaemonLog,
    private val onRunFinished: (String) -> Unit = {},
) : StageLauncher {

    override fun launch(stage: DispatchStage, cardId: String): LaunchedRun {
        require(stage == DispatchStage.REFINE) { "RefineStageLauncher cannot launch stage $stage (card $cardId)" }
        val thread = Thread({ execute(cardId) }, "refine-$cardId").apply { isDaemon = true }
        thread.start()
        val handle = ProcessHandle.current()
        return LaunchedRun(handle.pid(), handle.info().startInstant().orElse(Instant.now()))
    }

    private fun execute(cardId: String) {
        try {
            awaitLock(cardId)
            pipelineFor(cardId).run(cardId)
        } catch (e: Exception) {
            log.log("refine_failed", mapOf("card" to cardId, "error" to e.javaClass.simpleName))
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
