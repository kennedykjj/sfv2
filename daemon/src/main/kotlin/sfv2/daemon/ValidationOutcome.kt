package sfv2.daemon

import java.time.Clock
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.MoveDecision
import sfv2.core.MoveRules
import sfv2.store.CardEventStore

/** State of the gates the daemon ran on the card; the agent's verdict never replaces it. */
enum class GatesState { GREEN, RED }

sealed interface ValidationDecision {
    /** The card went back to Coding; [layer] names the engineer to call and [reason] is untrusted text. */
    data class Returned(val layer: String, val reason: String) : ValidationDecision

    data object Staged : ValidationDecision

    /** Approved by the agent but the gates are red: the card stays in Validation. */
    data object Held : ValidationDecision

    /** The report does not follow the verdict format: nothing is written. */
    data object Malformed : ValidationDecision
}

/** Turns the validate agent's report into a card move. Only the first line of the report decides. */
class ValidationOutcome(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val layers: Set<String>,
    private val clock: Clock,
) {

    fun apply(cardId: String, report: String, gates: GatesState): ValidationDecision {
        val card = store.card(cardId) ?: return ValidationDecision.Malformed
        val verdict = report.lineSequence().first().trim()
        return when {
            verdict == APPROVED -> approve(cardId, card, gates)
            verdict.startsWith(BLOCKED_PREFIX) -> block(cardId, card, verdict.removePrefix(BLOCKED_PREFIX).trim(), reason(report))
            else -> ValidationDecision.Malformed
        }
    }

    private fun approve(cardId: String, card: Card, gates: GatesState): ValidationDecision {
        if (gates == GatesState.RED) return ValidationDecision.Held
        if (!move(cardId, card, Column.STAGED, emptyMap())) return ValidationDecision.Malformed
        return ValidationDecision.Staged
    }

    private fun block(cardId: String, card: Card, layer: String, reason: String): ValidationDecision {
        if (layer !in layers || reason.isEmpty() || reason.toByteArray(Charsets.UTF_8).size > MAX_REASON_BYTES) {
            return ValidationDecision.Malformed
        }
        if (!move(cardId, card, Column.CODING, mapOf("layer" to layer, "reason" to reason))) return ValidationDecision.Malformed
        return ValidationDecision.Returned(layer, reason)
    }

    private fun reason(report: String): String = report.lines().drop(1).joinToString("\n").trim()

    private fun move(cardId: String, card: Card, to: Column, extra: Map<String, String>): Boolean {
        if (card.column != Column.VALIDATION) return false
        val actor = Actor.Factory(STAGE)
        if (MoveRules.check(card.column, to, actor) is MoveDecision.Refused) return false
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = actor,
            type = EventType.CARD_MOVED,
            payload = mapOf("from" to card.column.name, "to" to to.name) + extra,
        )
        recorder.record(cardId, Card.create(card.title, to), event)
        return true
    }

    private companion object {
        const val STAGE = "validate"
        const val APPROVED = "VERDICT: APPROVED"
        const val BLOCKED_PREFIX = "VERDICT: BLOCKED layer="
        const val MAX_REASON_BYTES = 2048
    }
}
