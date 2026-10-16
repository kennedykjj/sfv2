package sfv2.daemon

import java.time.Clock
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.Milestone
import sfv2.core.ProjectPolicies
import sfv2.ports.Tracker
import sfv2.ports.TrackerException
import sfv2.store.CardEventStore

/**
 * Writes the optional milestones (policy `milestones`) of a card to its external issue, once each.
 * Never throws: an optional milestone must not break the pipeline that triggers it. Both lookups take the card id.
 */
class ExternalMilestones(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val trackerOf: (String) -> Tracker,
    private val policiesOf: (String) -> ProjectPolicies,
    private val projectOf: (String) -> Project,
    private val prLinkOf: (Project, String) -> String?,
    private val clock: Clock,
    private val log: DaemonLog,
) {
    /** Serialized so the "already written" check and the write cannot race for the same card. */
    @Synchronized
    fun reached(cardId: String, milestone: Milestone, detail: String? = null) {
        try {
            write(cardId, milestone, detail)
        } catch (e: TrackerException) {
            log.log("milestone_failed", mapOf("card" to cardId, "milestone" to milestone.key))
        } catch (e: RuntimeException) {
            log.log(
                "milestone_failed",
                mapOf("card" to cardId, "milestone" to milestone.key, "error" to e.javaClass.simpleName),
            )
        }
    }

    private fun write(cardId: String, milestone: Milestone, detail: String?) {
        if (milestone !in policiesOf(cardId).milestones) return
        val card = store.card(cardId) ?: return
        val project = projectOf(cardId)
        val provider = project.policies.tracker.provider
        val ref = card.references.firstOrNull { it.provider == provider } ?: return
        val tracker = trackerOf(cardId)
        val item = tracker.fetch(ref.externalId)
        if (item != null) {
            TextChangeWarning.warnIfChanged(
                store, recorder, clock, cardId, card, ref.externalId, ref.textHash.value, item.textHash, provider,
            )
        }
        if (store.events(cardId).any { it.type == EventType.MILESTONE_WRITTEN && it.payload["milestone"] == milestone.key }) return
        val text = textOf(milestone, project, detail) ?: return
        tracker.writeMilestone(ref.externalId, text)
        record(cardId, card, milestone, ref.externalId, provider)
        log.log("milestone_written", mapOf("card" to cardId, "milestone" to milestone.key))
    }

    private fun textOf(milestone: Milestone, project: Project, detail: String?): String? = when (milestone) {
        Milestone.STARTED -> "started"
        Milestone.PR_OPENED -> detail?.let { prLinkOf(project, it) }
    }

    private fun record(cardId: String, card: Card, milestone: Milestone, externalId: String, provider: String) {
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Adapter(provider),
            type = EventType.MILESTONE_WRITTEN,
            payload = mapOf("milestone" to milestone.key, "externalId" to externalId),
        )
        recorder.record(cardId, card, event)
    }
}

/** The "external text changed" warning, shared by the reimport and the milestones; carries ids and hashes only. */
internal object TextChangeWarning {
    fun warnIfChanged(
        store: CardEventStore,
        recorder: EventRecorder,
        clock: Clock,
        cardId: String,
        card: Card,
        externalId: String,
        previousHash: String,
        newHash: String,
        provider: String,
    ) {
        if (previousHash == newHash) return
        val already = store.events(cardId).any { it.type == EventType.EXTERNAL_TEXT_CHANGED && it.payload["newHash"] == newHash }
        if (already) return
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Adapter(provider),
            type = EventType.EXTERNAL_TEXT_CHANGED,
            payload = mapOf("externalId" to externalId, "previousHash" to previousHash, "newHash" to newHash),
        )
        recorder.record(cardId, card, event)
    }
}
