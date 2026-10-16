package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore
import sfv2.store.Database
import sfv2.store.StoredEvent

object RunPoolFixtures {
    val now: Instant = Instant.parse("2026-10-16T10:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    /** A probe whose live set the test controls. */
    class FakeProbe : ProcessProbe {
        val alive = mutableSetOf<Long>()
        override fun isAlive(pid: Long, startedAt: Instant): Boolean = pid in alive
    }

    class RecordingLog : DaemonLog {
        val lines = mutableListOf<Pair<String, Map<String, String>>>()
        override fun log(event: String, fields: Map<String, String>) {
            lines.add(event to fields)
        }
    }

    fun seed(store: CardEventStore, id: String) {
        val event = Event(EventId("seed-$id"), now, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Seeded"))
        store.recordChange(id, Card.create("Seeded", Column.values().first()), event)
    }
}

class RunPoolTest {
    @TempDir
    lateinit var dir: Path

    private val probe = RunPoolFixtures.FakeProbe()
    private val log = RunPoolFixtures.RecordingLog()
    private val published = mutableListOf<StoredEvent>()

    private fun withPool(capacity: Int, block: (RunPool, CardEventStore) -> Unit) {
        Database.open(dir.resolve("pool.db")).use { db ->
            val store = CardEventStore(db)
            RunPoolFixtures.seed(store, "c1")
            RunPoolFixtures.seed(store, "c2")
            RunPoolFixtures.seed(store, "c3")
            val pool = RunPool(capacity, store, RunPoolFixtures.clock, probe, log, onEvent = { published.add(it) })
            block(pool, store)
        }
    }

    private fun started(decision: StartDecision): RunLock {
        assertThat(decision).isInstanceOf(StartDecision.Started::class.java)
        return (decision as StartDecision.Started).lock
    }

    @Test
    fun `AC-1 refuses a new run when every slot is taken and creates no lock`() {
        withPool(2) { pool, _ ->
            val first = started(pool.tryStart("c1", 101, RunPoolFixtures.now))
            val second = started(pool.tryStart("c2", 102, RunPoolFixtures.now))

            val decision = pool.tryStart("c3", 103, RunPoolFixtures.now)

            assertThat(decision).isEqualTo(StartDecision.PoolFull(2))
            assertThat(pool.active()).containsExactlyInAnyOrder(first, second)
        }
    }

    @Test
    fun `AC-2 refuses a second run on a locked card and keeps the original owner`() {
        withPool(3) { pool, _ ->
            val original = started(pool.tryStart("c1", 101, RunPoolFixtures.now))

            val decision = pool.tryStart("c1", 202, RunPoolFixtures.now.plusSeconds(5))

            assertThat(decision).isEqualTo(StartDecision.CardLocked(original))
            assertThat(pool.active()).containsExactly(original)
        }
    }

    @Test
    fun `AC-3 sweep releases a lock whose owner died, frees the slot and records a factory event`() {
        withPool(1) { pool, store ->
            val lock = started(pool.tryStart("c1", 101, RunPoolFixtures.now))
            // pid 101 is not in the alive set: the owner died

            val released = pool.sweep()

            assertThat(released).containsExactly(lock)
            assertThat(pool.active()).isEmpty()
            assertThat(pool.tryStart("c2", 102, RunPoolFixtures.now)).isInstanceOf(StartDecision.Started::class.java)
            val event = store.events("c1").single { it.type.name == "LOCK_RELEASED" }
            assertThat(event.actor).isEqualTo(Actor.Factory("pool"))
            assertThat(event.payload).containsEntry("reason", "owner_dead").containsEntry("pid", "101")
            assertThat(published.map { it.event }).containsExactly(event)
            assertThat(log.lines).contains("lock_released" to mapOf("card" to "c1", "pid" to "101"))
        }
    }

    @Test
    fun `AC-4 sweep keeps the lock and the slot of a live owner and records nothing`() {
        withPool(1) { pool, store ->
            val lock = started(pool.tryStart("c1", 101, RunPoolFixtures.now))
            probe.alive.add(101)

            val released = pool.sweep()

            assertThat(released).isEmpty()
            assertThat(pool.active()).containsExactly(lock)
            assertThat(pool.tryStart("c2", 102, RunPoolFixtures.now)).isEqualTo(StartDecision.PoolFull(1))
            assertThat(store.events("c1").map { it.type.name }).doesNotContain("LOCK_RELEASED")
            assertThat(published).isEmpty()
        }
    }

    /** Delegates to the real store but fails recordChange for the cards in [failing]. */
    private class FlakyRecorder(val store: CardEventStore) : LockEventRecorder {
        val failing = mutableSetOf<String>()
        override fun card(id: String): Card? = store.card(id)
        override fun recordChange(id: String, card: Card, event: Event): Long {
            if (id in failing) throw IllegalStateException("db down secret-detail")
            return store.recordChange(id, card, event)
        }
    }

    private fun withFlakyPool(block: (RunPool, FlakyRecorder, CardEventStore) -> Unit) {
        Database.open(dir.resolve("flaky.db")).use { db ->
            val store = CardEventStore(db)
            RunPoolFixtures.seed(store, "c1")
            RunPoolFixtures.seed(store, "c2")
            val recorder = FlakyRecorder(store)
            val pool = RunPool(3, recorder, RunPoolFixtures.clock, probe, log, onEvent = { published.add(it) })
            block(pool, recorder, store)
        }
    }

    private fun releasedCount(store: CardEventStore, id: String) =
        store.events(id).count { it.type.name == "LOCK_RELEASED" }

    @Test
    fun `AC-1 (129) sweep keeps the lock when recording fails and logs only the card id`() {
        withFlakyPool { pool, recorder, store ->
            val lock = started(pool.tryStart("c1", 101, RunPoolFixtures.now))
            recorder.failing.add("c1")

            val released = pool.sweep()

            assertThat(released).isEmpty()
            assertThat(pool.active()).containsExactly(lock)
            assertThat(releasedCount(store, "c1")).isZero()
            val failure = log.lines.single { it.first == "lock_release_failed" }
            assertThat(failure.second).containsEntry("card", "c1")
            assertThat(failure.second.values).noneMatch { it.contains("secret-detail") }
        }
    }

    @Test
    fun `AC-2 (129) sweep releases the other orphan locks when the first recording fails`() {
        withFlakyPool { pool, recorder, store ->
            val first = started(pool.tryStart("c1", 101, RunPoolFixtures.now))
            val second = started(pool.tryStart("c2", 102, RunPoolFixtures.now))
            recorder.failing.add("c1")

            val released = pool.sweep()

            assertThat(released).containsExactly(second)
            assertThat(pool.active()).containsExactly(first)
            assertThat(releasedCount(store, "c2")).isEqualTo(1)
            assertThat(published.map { it.cardId }).containsExactly("c2")
        }
    }

    @Test
    fun `AC-3 (129) next sweep releases the pending lock once the failure is resolved`() {
        withFlakyPool { pool, recorder, store ->
            val lock = started(pool.tryStart("c1", 101, RunPoolFixtures.now))
            recorder.failing.add("c1")
            pool.sweep()

            recorder.failing.clear()
            val released = pool.sweep()
            val again = pool.sweep()

            assertThat(released).containsExactly(lock)
            assertThat(again).isEmpty()
            assertThat(pool.active()).isEmpty()
            assertThat(releasedCount(store, "c1")).isEqualTo(1)
        }
    }

    @Test
    fun `AC-5 releasing a finished run returns the slot and lets the card be taken again`() {
        withPool(1) { pool, _ ->
            started(pool.tryStart("c1", 101, RunPoolFixtures.now))

            pool.release("c1")

            assertThat(pool.active()).isEmpty()
            val again = pool.tryStart("c1", 103, RunPoolFixtures.now)
            assertThat(started(again)).isEqualTo(RunLock("c1", 103, RunPoolFixtures.now))
        }
    }
}
