package sfv2.daemon

import java.time.Clock
import java.time.Instant
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore

data class RunLock(val cardId: String, val pid: Long, val startedAt: Instant)

sealed interface StartDecision {
    data class Started(val lock: RunLock) : StartDecision
    data class PoolFull(val capacity: Int) : StartDecision
    data class CardLocked(val holder: RunLock) : StartDecision
}

/** Bounded set of run slots with one lock per card; locks of dead owners are released by [sweep]. */
class RunPool(
    private val capacity: Int,
    private val store: CardEventStore,
    private val clock: Clock,
    private val probe: ProcessProbe,
    private val log: DaemonLog = StderrDaemonLog,
    private val recorder: EventRecorder,
) {
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
        dead.forEach { lock ->
            locks.remove(lock.cardId)
            recordRelease(lock)
        }
        return dead
    }

    @Synchronized
    fun active(): List<RunLock> = locks.values.toList()

    private fun recordRelease(lock: RunLock) {
        val card = store.card(lock.cardId)
        if (card == null) {
            log.log("lock_release_orphan_card", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
            return
        }
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory("pool"),
            type = EventType.LOCK_RELEASED,
            payload = mapOf("reason" to "owner_dead", "pid" to lock.pid.toString()),
        )
        recorder.record(lock.cardId, card, event)
        log.log("lock_released", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
    }
}
