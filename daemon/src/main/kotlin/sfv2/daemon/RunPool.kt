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

/**
 * Bounded set of run slots with one lock per card; locks of dead owners are released by [sweep].
 * Every release is written through an [EventRecorder] (the single event writer), so subscribers see it in order.
 */
class RunPool private constructor(
    private val capacity: Int,
    private val lookup: (String) -> Card?,
    private val write: (String, Card, Event) -> StoredEvent,
    private val clock: Clock,
    private val probe: ProcessProbe,
    private val log: DaemonLog,
) {
    /** The production wiring: the card comes from [store] and the release goes through [recorder]. */
    constructor(
        capacity: Int,
        store: CardEventStore,
        clock: Clock,
        probe: ProcessProbe,
        log: DaemonLog = StderrDaemonLog,
        recorder: EventRecorder,
    ) : this(capacity, store::card, recorder::record, clock, probe, log)

    /**
     * Test seam: the writes go through [events], which a test makes fail; [onEvent] sees each stored release.
     * Production code uses the [EventRecorder] constructor, so nothing here publishes.
     */
    constructor(
        capacity: Int,
        events: LockEventRecorder,
        clock: Clock,
        probe: ProcessProbe,
        log: DaemonLog = StderrDaemonLog,
        onEvent: (StoredEvent) -> Unit = {},
    ) : this(capacity, events::card, { id, card, event -> events.store(id, card, event).also(onEvent) }, clock, probe, log)

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
            try {
                recordRelease(lock)
            } catch (e: RuntimeException) {
                log.log("lock_release_failed", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
                return@filter false
            }
            locks.remove(lock.cardId)
            true
        }
    }

    @Synchronized
    fun active(): List<RunLock> = locks.values.toList()

    private fun recordRelease(lock: RunLock) {
        val card = lookup(lock.cardId)
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
        write(lock.cardId, card, event)
        log.log("lock_released", mapOf("card" to lock.cardId, "pid" to lock.pid.toString()))
    }
}
