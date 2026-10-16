package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import sfv2.core.AssistantStateRules
import sfv2.store.CardEventStore

// Red-step stubs: signatures only, neutral bodies. The engineer replaces them with the real code and empties this file.

class FactoryPulse(
    store: CardEventStore,
    pool: RunPool,
    poolCapacity: () -> Int,
    clock: Clock,
    rules: AssistantStateRules,
    speaker: AssistantSpeaker,
) {
    fun handle(request: Request): Response = Response.Error("factory pulse is not available")
}

fun SocketServer.Companion.start(path: Path, pulse: FactoryPulse): SocketServer = start(path)
