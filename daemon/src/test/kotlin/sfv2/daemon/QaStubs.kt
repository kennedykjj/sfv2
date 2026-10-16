package sfv2.daemon

import java.time.Clock
import sfv2.core.ProjectPolicies
import sfv2.ports.Forge
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets the acceptance tests of #46 compile before the implementation exists.
// The implementer deletes this file and writes the real AutonomousDelivery.kt, DeliveryOutcome and DeliveryTrigger.

sealed interface DeliveryOutcome {
    data object Done : DeliveryOutcome

    data class ReturnedToOwner(val reason: String) : DeliveryOutcome

    data object Skipped : DeliveryOutcome
}

fun interface DeliveryTrigger {
    fun trigger(cardId: String)
}

@Suppress("UNUSED_PARAMETER", "unused")
class AutonomousDelivery(
    store: CardEventStore,
    recorder: EventRecorder,
    forge: (String) -> Forge,
    policies: (String) -> ProjectPolicies,
    pause: CiPause,
    maxPolls: Int,
    clock: Clock,
    log: DaemonLog,
) {
    fun deliver(cardId: String): DeliveryOutcome = DeliveryOutcome.Skipped
}
