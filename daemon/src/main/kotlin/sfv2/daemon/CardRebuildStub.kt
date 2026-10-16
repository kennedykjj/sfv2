package sfv2.daemon

import java.time.Clock
import sfv2.ports.Forge
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets the acceptance tests compile; the implementer deletes this file and writes CardRebuild.kt.
@Suppress("UNUSED_PARAMETER")
class CardRebuild(
    store: CardEventStore,
    recorder: EventRecorder,
    registry: ProjectRegistry,
    forge: Forge,
    ownerProof: OwnerProof,
    clock: Clock,
    log: DaemonLog,
) {
    fun handle(request: Request): Response = Response.Error("not implemented")
}
