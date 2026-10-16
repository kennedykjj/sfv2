package sfv2.daemon

import java.time.Clock
import java.time.Instant
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

// shipfloor:qa-stub — only so the red gate compiles; the implementer deletes this file.

fun interface ProcessProbe {
    fun isAlive(pid: Long, startedAt: Instant): Boolean
}

data class RunLock(val cardId: String, val pid: Long, val startedAt: Instant)

sealed interface StartDecision {
    data class Started(val lock: RunLock) : StartDecision
    data class PoolFull(val capacity: Int) : StartDecision
    data class CardLocked(val holder: RunLock) : StartDecision
}

@Suppress("UNUSED_PARAMETER")
class RunPool(
    capacity: Int,
    store: CardEventStore,
    clock: Clock,
    probe: ProcessProbe,
    log: DaemonLog = StderrDaemonLog,
    onEvent: (StoredEvent) -> Unit = {},
) {
    fun tryStart(cardId: String, pid: Long, startedAt: Instant): StartDecision = StartDecision.PoolFull(0)
    fun release(cardId: String) {}
    fun sweep(): List<RunLock> = emptyList()
    fun active(): List<RunLock> = emptyList()
}
