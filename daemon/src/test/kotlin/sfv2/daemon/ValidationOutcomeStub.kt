package sfv2.daemon

import java.time.Clock
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets the red gate compile; the implementer deletes this file and adds the real ValidationOutcome.

enum class GatesState { GREEN, RED }

sealed interface ValidationDecision {
    data class Returned(val layer: String, val reason: String) : ValidationDecision
    data object Staged : ValidationDecision
    data object Held : ValidationDecision
    data object Malformed : ValidationDecision
}

class ValidationOutcome(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val layers: Set<String>,
    private val clock: Clock,
) {
    fun apply(cardId: String, report: String, gates: GatesState): ValidationDecision = ValidationDecision.Malformed
}
