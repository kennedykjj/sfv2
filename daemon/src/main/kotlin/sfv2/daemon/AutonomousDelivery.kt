package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.DeliveryMode
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.ProjectPolicies
import sfv2.ports.ChecksState
import sfv2.ports.Forge
import sfv2.ports.ForgeException
import sfv2.ports.MergeResult
import sfv2.ports.PullRequestRef
import sfv2.ports.PullRequestState
import sfv2.store.CardEventStore

sealed interface DeliveryOutcome {
    /** The merge is on main, its CI is green and the card moved to Done. */
    data object Done : DeliveryOutcome

    /** The card went back to Staged with [reason] recorded; the owner decides again. */
    data class ReturnedToOwner(val reason: String) : DeliveryOutcome

    /** Nothing to do: wrong column, a project that does not deliver autonomously, or an interrupted wait. */
    data object Skipped : DeliveryOutcome
}

/** Hands a card in Ready for Prod to the delivery; must return quickly. */
fun interface DeliveryTrigger {
    fun trigger(cardId: String)
}

/**
 * Merges the head the owner approved and closes the card only on a green main. The merge is recorded as `PR_MERGED`
 * before its CI is followed, so a restart or a new approval never merges twice.
 */
class AutonomousDelivery(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val forge: (String) -> Forge,
    private val policies: (String) -> ProjectPolicies,
    private val pause: CiPause,
    private val maxPolls: Int,
    private val clock: Clock,
    private val log: DaemonLog,
    private val conflicts: ConflictResolver? = null,
    private val publisher: BranchPublisher? = null,
    private val worktreeOf: ((String) -> Path)? = null,
) {
    init {
        require(maxPolls > 0) { "maxPolls must be positive, got $maxPolls" }
    }

    private sealed interface Merge {
        data class Ready(val commit: String) : Merge

        data class Stop(val outcome: DeliveryOutcome) : Merge
    }

    fun deliver(cardId: String): DeliveryOutcome {
        val card = store.card(cardId)
        if (card == null || card.column != Column.READY_FOR_PROD) return DeliveryOutcome.Skipped
        val projectPolicies = policies(cardId)
        if (projectPolicies.deliveryMode != DeliveryMode.AUTONOMOUS) return DeliveryOutcome.Skipped
        val events = store.events(cardId)
        val approvedHead = approvedHead(events)
            ?: return returnToOwner(cardId, card, "no_approved_head", emptyMap())
        val recorded = events.lastOrNull { it.type == EventType.PR_MERGED }?.payload?.get("merge_commit")
        val commit = if (recorded != null) {
            recorded
        } else {
            val branch = WorktreeManager.branchName(projectPolicies.conventions.branchFormat, cardId, card.title)
            when (val merge = merge(cardId, card, branch, approvedHead)) {
                is Merge.Stop -> return merge.outcome
                is Merge.Ready -> merge.commit
            }
        }
        return followMain(cardId, card, commit)
    }

    private fun approvedHead(events: List<Event>): String? = events
        .lastOrNull {
            it.type == EventType.CARD_MOVED && it.actor == Actor.Factory(VALIDATE) &&
                it.payload["to"] == Column.STAGED.name && it.payload["ci"] == "green" && !it.payload["head"].isNullOrEmpty()
        }
        ?.payload?.get("head")

    private fun merge(cardId: String, card: Card, branch: String, approvedHead: String): Merge {
        val forge = forge(cardId)
        var pr: PullRequestRef? = null
        try {
            pr = forge.findOpenPullRequest(branch)
                ?: return stop(returnToOwner(cardId, card, "pr_not_open", emptyMap()))
            return when (val result = forge.merge(pr, approvedHead)) {
                is MergeResult.Merged -> {
                    record(cardId, card, EventType.PR_MERGED, mapOf("pr" to pr.number.toString(), "merge_commit" to result.mergeCommit, "head" to approvedHead))
                    Merge.Ready(result.mergeCommit)
                }
                is MergeResult.HeadMismatch ->
                    stop(returnToOwner(cardId, card, "head_mismatch expected=$approvedHead actual=${result.actualHead}", emptyMap()))
                MergeResult.Conflict -> stop(resolveConflict(cardId, card, branch))
                is MergeResult.NotOpen -> stop(returnToOwner(cardId, card, "pr_not_open", emptyMap()))
            }
        } catch (e: ForgeException) {
            val merged = pr?.let { stateOf(forge, it) } == PullRequestState.Merged
            return stop(returnToOwner(cardId, card, if (merged) "merge_unrecorded" else "forge_failed", emptyMap()))
        }
    }

    /**
     * A resolved conflict is a new head: it is pushed and the card goes back to Validation, because the owner's approval
     * and the green gates belong to the old head.
     */
    private fun resolveConflict(cardId: String, card: Card, branch: String): DeliveryOutcome {
        val resolver = conflicts
        val worktree = worktreeOf?.invoke(cardId)
        val publish = publisher
        if (resolver == null || worktree == null || publish == null) return returnToOwner(cardId, card, "merge_conflict", emptyMap())
        return when (val outcome = resolver.resolve(cardId, worktree)) {
            is ConflictOutcome.Resolved -> when (publish.push(worktree, branch)) {
                PushResult.Pushed -> {
                    val round = maxOf(store.events(cardId).count { ConflictResolution.isRound(it) }, 1)
                    move(cardId, card, Column.VALIDATION, mapOf("reason" to "conflict_round", "round" to round.toString(), "head" to outcome.head))
                    log.log("delivery_conflict_resolved", mapOf("card" to cardId, "round" to round.toString()))
                    DeliveryOutcome.Skipped
                }
                PushResult.Refused, PushResult.Failed -> returnToOwner(cardId, card, "push_failed", emptyMap())
            }
            ConflictOutcome.OverLimit, ConflictOutcome.Failed -> returnToOwner(cardId, card, "merge_conflict", emptyMap())
            ConflictOutcome.RoundsExhausted -> {
                val rounds = store.events(cardId).count { ConflictResolution.isRound(it) }
                block(cardId, card, "conflict_rounds_exhausted rounds=$rounds")
            }
        }
    }

    private fun block(cardId: String, card: Card, reason: String): DeliveryOutcome {
        record(cardId, card, EventType.CARD_BLOCKED, mapOf("reason" to reason))
        log.log("delivery_blocked", mapOf("card" to cardId, "reason" to reason.substringBefore(' ')))
        return DeliveryOutcome.Skipped
    }

    private fun stateOf(forge: Forge, pr: PullRequestRef): PullRequestState? =
        try {
            forge.state(pr)
        } catch (e: ForgeException) {
            null
        }

    private fun stop(outcome: DeliveryOutcome) = Merge.Stop(outcome)

    private fun followMain(cardId: String, card: Card, commit: String): DeliveryOutcome {
        val forge = forge(cardId)
        var result: ChecksState = ChecksState.Pending
        try {
            for (poll in 1..maxPolls) {
                result = forge.checks(commit)
                if (result != ChecksState.Pending || poll == maxPolls) break
                pause.await()
                if (Thread.currentThread().isInterrupted) return DeliveryOutcome.Skipped
            }
        } catch (e: ForgeException) {
            return returnToOwner(cardId, card, "forge_failed", mapOf("merge_commit" to commit))
        }
        return when (val state = result) {
            ChecksState.Passed -> {
                move(cardId, card, Column.DONE, mapOf("merge_commit" to commit, "ci" to "green"))
                log.log("delivery_done", mapOf("card" to cardId))
                DeliveryOutcome.Done
            }
            is ChecksState.Failed ->
                returnToOwner(cardId, card, redReason(state), mapOf("merge_commit" to commit))
            ChecksState.Pending -> returnToOwner(cardId, card, "main_ci_timeout", mapOf("merge_commit" to commit))
        }
    }

    private fun redReason(state: ChecksState.Failed): String {
        val names = state.failing.joinToString(",") { it.name }
        val summaries = state.failing.joinToString("\n") { "${it.name}: ${it.summary}" }
        return "main_ci_red checks=$names\n$summaries"
    }

    private fun returnToOwner(cardId: String, card: Card, reason: String, extra: Map<String, String>): DeliveryOutcome.ReturnedToOwner {
        val capped = truncateUtf8(reason, MAX_REASON_BYTES)
        move(cardId, card, Column.STAGED, extra + ("reason" to capped))
        log.log("delivery_returned", mapOf("card" to cardId, "reason" to capped.substringBefore(' ').substringBefore('\n')))
        return DeliveryOutcome.ReturnedToOwner(capped)
    }

    private fun move(cardId: String, card: Card, to: Column, extra: Map<String, String>) {
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory(DELIVER),
            type = EventType.CARD_MOVED,
            payload = mapOf("from" to card.column.name, "to" to to.name) + extra,
        )
        recorder.record(cardId, Card.create(card.title, to), event)
    }

    private fun record(cardId: String, card: Card, type: EventType, payload: Map<String, String>) {
        val event = Event(EventId(UUID.randomUUID().toString()), clock.instant(), Actor.Factory(DELIVER), type, payload)
        recorder.record(cardId, card, event)
    }

    private companion object {
        const val VALIDATE = "validate"
        const val DELIVER = "deliver"
        const val MAX_REASON_BYTES = 2048
    }
}

/**
 * Runs the deliveries on one thread so the CI polling never stalls the dispatcher cycle; a card already being
 * delivered is not queued again.
 */
class AutonomousDeliveryTrigger(
    private val delivery: AutonomousDelivery,
    private val log: DaemonLog,
) : DeliveryTrigger, AutoCloseable {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "autonomous-delivery").apply { isDaemon = true }
    }
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun trigger(cardId: String) {
        if (!inFlight.add(cardId)) return
        try {
            executor.execute {
                try {
                    delivery.deliver(cardId)
                } catch (e: RuntimeException) {
                    log.log("delivery_failed", mapOf("card" to cardId, "error" to e.javaClass.simpleName))
                } finally {
                    inFlight.remove(cardId)
                }
            }
        } catch (e: RejectedExecutionException) {
            inFlight.remove(cardId)
        }
    }

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(CLOSE_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val CLOSE_SECONDS = 5L
    }
}
