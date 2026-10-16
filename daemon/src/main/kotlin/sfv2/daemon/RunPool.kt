package sfv2.daemon

import java.time.Clock
import java.time.Instant
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

data class RunLock(val cardId: String, val pid: Long, val startedAt: Instant)

sealed interface StartDecision {
    data class Started(val lock: RunLock) : StartDecision
    data class PoolFull(val capacity: Int) : StartDecision
    data class CardLocked(val holder: RunLock) : StartDecision
}

/** What the pool needs from the event store to record a lock release. */
interface LockEventRecorder {
    fun card(id: String): Card?
    fun recordChange(id: String, card: Card, event: Event): Long
}

private class StoreLockEventRecorder(private val store: CardEventStore) : LockEventRecorder {
    override fun card(id: String): Card? = store.card(id)
    override fun recordChange(id: String, card: Card, event: Event): Long = store.recordChange(id, card, event)
}

/** Bounded set of run slots with one lock per card; locks of dead owners are released by [sweep]. */
class RunPool(
    private val capacity: Int,
    private val store: LockEventRecorder,
    private val clock: Clock,
    private val probe: ProcessProbe,
    private val log: DaemonLog = StderrDaemonLog,
    private val onEvent: (StoredEvent) -> Unit = {},
) {
    constructor(
        capacity: Int,
        store: CardEventStore,
        clock: Clock,
        probe: ProcessProbe,
        log: DaemonLog = StderrDaemonLog,
        onEvent: (StoredEvent) -> Unit = {},
    ) : this(capacity, StoreLockEventRecorder(store), clock, probe, log, onEvent)

    private val locks = linkedMapOf<String, RunLock>()

    init {
        require(capacity > 0) { "pool capacity must be positive, got $capacity" }
    }

    @Synchronized
    fun tryStart(cardId: String, pid: Long, startedAt: Instant): StartDecision {
        locks[cardId]?.let { return StartDecision.CardLocked(it) }
        if (locks.size >= capacity) return StartDecision.PoolFull(capacity)
        val lock = RunLock(cardId, pid, startedAt)
        locks[cardId] = lock
        return StartDecision.Started(lock)
    }

    @Synchronized
    fun release(cardId: String) {
        locks.remove(cardId)
    }

    @Synchronized
    fun sweep(): List<RunLock> {
        val dead = locks.values.filter { !probe.isAlive(it.pid, it.startedAt) }
        return dead.filter { lock ->
            val outcome = try {
                recordRelease(lock)
            } catch (e: RuntimeException) {
                log.log("lock_release_failed", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
                return@filter false
            }
            locks.remove(lock.cardId)
            outcome?.let(onEvent)
            true
        }
    }

    @Synchronized
    fun active(): List<RunLock> = locks.values.toList()

    private fun recordRelease(lock: RunLock): StoredEvent? {
        val card = store.card(lock.cardId)
        if (card == null) {
            log.log("lock_release_orphan_card", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
            return null
        }
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory("pool"),
            type = EventType.LOCK_RELEASED,
            payload = mapOf("reason" to "owner_dead", "pid" to lock.pid.toString()),
        )
        val seq = store.recordChange(lock.cardId, card, event)
        log.log("lock_released", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
        return StoredEvent(seq, lock.cardId, event)
    }
}
