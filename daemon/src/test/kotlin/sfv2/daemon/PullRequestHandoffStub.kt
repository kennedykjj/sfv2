package sfv2.daemon

import java.time.Clock
import sfv2.core.ProjectPolicies
import sfv2.ports.Forge
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets the red gate compile; the implementer deletes this file and adds the real class in main.
enum class HandoffOutcome { Waiting, Merged, ClosedUnmerged, ForgeFailed }

class PullRequestHandoff(
    @Suppress("unused") private val store: CardEventStore,
    @Suppress("unused") private val recorder: EventRecorder,
    @Suppress("unused") private val forge: (String) -> Forge,
    @Suppress("unused") private val policies: (String) -> ProjectPolicies,
    @Suppress("unused") private val clock: Clock,
    @Suppress("unused") private val log: DaemonLog,
) {
    fun approve(cardId: String) = Unit

    fun reconcile(cardId: String): HandoffOutcome = HandoffOutcome.Waiting
}
