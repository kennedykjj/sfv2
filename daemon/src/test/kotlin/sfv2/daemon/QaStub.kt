package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets the acceptance tests compile before the feature exists; the implementer deletes this file.
class CardCommands(val store: CardEventStore, val clock: Clock)

fun SocketServer.Companion.start(path: Path, commands: CardCommands): SocketServer = start(path)
