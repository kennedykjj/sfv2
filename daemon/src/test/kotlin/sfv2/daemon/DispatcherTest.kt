package sfv2.daemon

import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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

object DispatcherFixtures {
    val start: Instant = Instant.parse("2026-10-16T12:00:00Z")

    /** A clock the test moves by hand. */
    class MutableClock(private var current: Instant = start) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(by: Duration) {
            current = current.plus(by)
        }
    }

    /** Starts "runs" that stay alive in [probe] until the test lets them die. */
    class FakeLauncher(private val probe: RunPoolFixtures.FakeProbe? = null) : StageLauncher {
        val launched = mutableListOf<Pair<DispatchStage, String>>()
        val failing = mutableSetOf<String>()
        val attempts = mutableListOf<String>()
        var nextPid = 1000L
        var processStart: Instant = Instant.parse("2026-10-16T11:59:30Z")

        override fun launch(stage: DispatchStage, cardId: String): LaunchedRun {
            attempts.add(cardId)
            if (cardId in failing) throw IllegalStateException("binary missing secret-detail")
            launched.add(stage to cardId)
            val pid = nextPid++
            probe?.alive?.add(pid)
            return LaunchedRun(pid, processStart)
        }
    }

    /**
     * Builds a Dispatcher restricted to [stages] through its `stages` constructor parameter (last, after `launchGuard`).
     * Reflective so the missing parameter shows as a failed assertion instead of a compile error.
     */
    fun dispatcherFor(
        stages: Set<DispatchStage>,
        store: CardEventStore,
        pool: RunPool,
        launcher: StageLauncher,
        pause: PauseSwitch,
        schedule: WorkSchedule,
        clock: Clock,
        log: DaemonLog,
        guard: LaunchGuard = LaunchGuard.Open,
    ): Dispatcher {
        val constructor = Dispatcher::class.java.constructors.firstOrNull { c ->
            c.parameterTypes.size == 11 && c.parameterTypes.last() == Set::class.java
        }
        assertThat(constructor).`as`("Dispatcher has a `stages: Set<DispatchStage>` constructor parameter").isNotNull()
        return requireNotNull(constructor).newInstance(
            store, pool, launcher, pause, schedule, DispatchLimits(emptyMap()), clock, Duration.ofHours(1), log, guard, stages,
        ) as Dispatcher
    }

    class FakePause(@Volatile var paused: Boolean = false) : PauseSwitch {
        override fun isPaused(): Boolean = paused
    }

    class FakeSchedule(@Volatile var open: Boolean = true) : WorkSchedule {
        val asked = mutableListOf<Instant>()
        val firstAsk = CountDownLatch(1)
        override fun isWorkTime(now: Instant): Boolean {
            synchronized(asked) { asked.add(now) }
            firstAsk.countDown()
            return open
        }
    }

    class RecordingLog : DaemonLog {
        val lines = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        override fun log(event: String, fields: Map<String, String>) {
            lines.add(event to fields)
        }
    }
}

class DispatcherTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: EventRecorder
    private val clock = DispatcherFixtures.MutableClock()
    private val pause = DispatcherFixtures.FakePause()
    private val schedule = DispatcherFixtures.FakeSchedule()
    private val log = DispatcherFixtures.RecordingLog()
    private val probe = RunPoolFixtures.FakeProbe()
    private val launcher = DispatcherFixtures.FakeLauncher(probe)
    private val toClose = mutableListOf<AutoCloseable>()

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("dispatcher.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
    }

    @AfterEach
    fun close() {
        toClose.forEach { it.close() }
        stream.close()
        database.close()
    }

    private fun card(id: String, column: Column) {
        val event = Event(EventId("seed-$id"), DispatcherFixtures.start, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to id))
        store.recordChange(id, Card.create("Card $id", column), event)
    }

    private fun pool(capacity: Int = 10, tracker: ProcessProbe = probe) =
        RunPool(capacity, store, clock, tracker, log, recorder = recorder)

    private fun dispatcher(
        pool: RunPool,
        limits: DispatchLimits = DispatchLimits(emptyMap()),
        tick: Duration = Duration.ofHours(1),
        guard: LaunchGuard = LaunchGuard.Open,
        stages: Set<DispatchStage> = DispatchStage.entries.toSet(),
    ): Dispatcher =
        if (stages == DispatchStage.entries.toSet()) {
            Dispatcher(store, pool, launcher, pause, schedule, limits, clock, tick, log, launchGuard = guard)
        } else {
            DispatcherFixtures.dispatcherFor(stages, store, pool, launcher, pause, schedule, clock, log, guard)
        }.also { toClose.add(it) }

    private fun budget(limits: CostLimits): CostBudget = CostBudget(store, recorder, limits, clock, ZoneOffset.UTC, log)

    private fun cost(cardId: String, runId: String, usd: String) {
        val event = Event(
            EventId("cost-$runId"),
            clock.instant(),
            Actor.Factory("cost"),
            EventType.RUN_COST_RECORDED,
            mapOf("run_id" to runId, "status" to "KNOWN", "cost_usd" to usd, "estimate" to "true"),
        )
        recorder.record(cardId, Card.create("Card $cardId", Column.READY_FOR_DEV), event)
    }

    private fun moved(cardId: String, column: Column) {
        val event = Event(EventId("move-$cardId-$column"), clock.instant(), Actor.Owner, EventType.CARD_MOVED, mapOf("to" to column.name))
        recorder.record(cardId, Card.create("Card $cardId", column), event)
    }

    @Test
    fun `a run that ended frees its slot on the next cycle even while paused, and the next card starts`() {
        card("a", Column.READY_FOR_DEV)
        card("b", Column.READY_FOR_DEV)
        val pool = pool(capacity = 1)
        val dispatcher = dispatcher(pool)
        assertThat(dispatcher.runCycle().map { it.cardId }).containsExactly("a")
        assertThat(dispatcher.runCycle()).isEmpty()
        assertThat(pool.active().map { it.cardId }).containsExactly("a")

        moved("a", Column.CODING)
        probe.alive.clear()
        pause.paused = true
        assertThat(dispatcher.runCycle()).isEmpty()
        assertThat(pool.active()).isEmpty()
        pause.paused = false

        assertThat(dispatcher.runCycle().map { it.cardId }).containsExactly("b")
        assertThat(store.events("a").map { it.type.name }).contains("LOCK_RELEASED")
    }

    @Test
    fun `AC-1 picks the first eligible card in column order`() {
        card("a", Column.READY_FOR_DEV)
        card("b", Column.READY_FOR_DEV)
        card("c", Column.READY_FOR_DEV)
        val dispatcher = dispatcher(pool(capacity = 1))

        val started = dispatcher.runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "a"))
        assertThat(launcher.launched).containsExactly(DispatchStage.DEVELOP to "a")
    }

    @Test
    fun `AC-2 a stage at its WIP limit starts nothing new while other stages go on`() {
        card("d1", Column.READY_FOR_DEV)
        card("d2", Column.READY_FOR_DEV)
        card("r1", Column.READY_FOR_REFINEMENT)
        val dispatcher = dispatcher(pool(), DispatchLimits(mapOf(DispatchStage.DEVELOP to 1)))

        val first = dispatcher.runCycle()
        val second = dispatcher.runCycle()

        assertThat(first).containsExactly(DispatchedRun(DispatchStage.REFINE, "r1"), DispatchedRun(DispatchStage.DEVELOP, "d1"))
        assertThat(second).isEmpty()
        assertThat(launcher.launched.map { it.second }).doesNotContain("d2")
    }

    @Test
    fun `AC-3 no card starts while PAUSE is active`() {
        card("a", Column.READY_FOR_DEV)
        pause.paused = true
        val dispatcher = dispatcher(pool())

        assertThat(dispatcher.runCycle()).isEmpty()
        assertThat(launcher.launched).isEmpty()

        pause.paused = false
        assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "a"))
    }

    @Test
    fun `AC-4 no card starts outside work hours, asking the schedule for the clock instant`() {
        card("a", Column.READY_FOR_DEV)
        schedule.open = false
        val dispatcher = dispatcher(pool())

        assertThat(dispatcher.runCycle()).isEmpty()

        assertThat(launcher.launched).isEmpty()
        assertThat(schedule.asked).containsExactly(clock.instant())
    }

    @Test
    fun `AC-5 a recorded card move wakes the dispatcher without waiting for the tick`() {
        card("a", Column.INBOX)
        schedule.open = false
        val launchedLatch = CountDownLatch(1)
        val signalling = StageLauncher { stage, id -> launcher.launch(stage, id).also { launchedLatch.countDown() } }
        val dispatcher = Dispatcher(
            store, pool(), signalling, pause, schedule, DispatchLimits(emptyMap()), clock, Duration.ofHours(1), log,
        ).also { toClose.add(it) }
        stream.addListener(dispatcher::onEvent)
        dispatcher.start()
        // the first cycle ran (and was refused by the closed schedule); the next one is a whole tick away
        assertThat(schedule.firstAsk.await(5, TimeUnit.SECONDS)).isTrue()
        schedule.open = true

        moved("a", Column.READY_FOR_DEV)

        assertThat(launchedLatch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(launcher.launched).containsExactly(DispatchStage.DEVELOP to "a")
    }

    @Test
    fun `AC-6 a locked card or a full pool starts nothing and creates no duplicate run`() {
        card("a", Column.READY_FOR_DEV)
        card("b", Column.READY_FOR_DEV)
        val pool = pool(capacity = 2)
        val held = (pool.tryStart("a", 77, DispatcherFixtures.start) as StartDecision.Started).lock
        probe.alive.add(77)
        val dispatcher = dispatcher(pool)

        val started = dispatcher.runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "b"))
        assertThat(pool.active().map { it.cardId }).containsExactlyInAnyOrder("a", "b")
        assertThat(pool.active()).contains(held)

        card("c", Column.READY_FOR_DEV)
        assertThat(dispatcher.runCycle()).isEmpty()
        assertThat(launcher.launched.map { it.second }).containsExactly("b")
        assertThat(pool.active()).hasSize(2)
    }

    @Test
    fun `AC-7 a chosen card goes through the stage port and its run stays locked until released`() {
        card("a", Column.VALIDATION)
        val pool = pool()
        val dispatcher = dispatcher(pool)

        dispatcher.runCycle()

        assertThat(launcher.launched).containsExactly(DispatchStage.VALIDATE to "a")
        assertThat(pool.isLocked("a")).isTrue()
        assertThat(pool.active().single().pid).isEqualTo(1000L)

        pool.release("a")
        assertThat(pool.isLocked("a")).isFalse()
        assertThat(pool.hasRoom()).isTrue()
    }

    @Test
    fun `AC-8 a launch that throws is logged by ids only, holds no lock, spares other cards and backs off`() {
        card("a", Column.READY_FOR_DEV)
        card("b", Column.READY_FOR_DEV)
        launcher.failing.add("a")
        val pool = pool()
        val dispatcher = dispatcher(pool)

        val started = dispatcher.runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "b"))
        assertThat(pool.isLocked("a")).isFalse()
        assertThat(log.lines.any { it.second["card"] == "a" && it.second["stage"] == DispatchStage.DEVELOP.name }).isTrue()
        assertThat(log.lines.flatMap { it.second.values } + log.lines.map { it.first }).noneMatch { it.contains("secret-detail") }

        // backoff: 60 s, then doubling, capped at 30 min; the clock is injected
        fun attemptsOnA() = launcher.attempts.count { it == "a" }
        var expected = attemptsOnA()
        assertThat(expected).isEqualTo(1)
        listOf(60L, 120L, 240L, 480L, 960L, 1800L, 1800L).forEach { delay ->
            clock.advance(Duration.ofSeconds(delay - 1))
            dispatcher.runCycle()
            assertThat(attemptsOnA()).`as`("no retry before $delay s").isEqualTo(expected)
            clock.advance(Duration.ofSeconds(1))
            dispatcher.runCycle()
            expected += 1
            assertThat(attemptsOnA()).`as`("retry after $delay s").isEqualTo(expected)
        }
    }

    @Test
    fun `AC-9 wake and the event listener return at once while a cycle holds the dispatcher`() {
        card("a", Column.READY_FOR_DEV)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = StageLauncher { stage, id ->
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            launcher.launch(stage, id)
        }
        val dispatcher = Dispatcher(
            store, pool(), blocking, pause, schedule, DispatchLimits(emptyMap()), clock, Duration.ofHours(1), log,
        ).also { toClose.add(it) }
        stream.addListener(dispatcher::onEvent)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val cycle = executor.submit { dispatcher.runCycle() }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()

            val wake = executor.submit { dispatcher.wake() }
            val record = executor.submit { moved("a", Column.READY_FOR_DEV) }

            assertThatCode { wake.get(2, TimeUnit.SECONDS) }.doesNotThrowAnyException()
            assertThatCode { record.get(2, TimeUnit.SECONDS) }.doesNotThrowAnyException()
            assertThat(cycle.isDone).isFalse()
        } finally {
            release.countDown()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `AC-9 a throwing listener does not stop the other subscribers nor fail the recording`() {
        card("a", Column.INBOX)
        val seen = mutableListOf<StoredEvent>()
        stream.addListener { throw IllegalStateException("listener broke") }
        stream.addListener { seen.add(it) }

        assertThatCode { moved("a", Column.READY_FOR_DEV) }.doesNotThrowAnyException()

        assertThat(seen.map { it.event.type }).containsExactly(EventType.CARD_MOVED)
        assertThat(store.events("a").map { it.type }).contains(EventType.CARD_MOVED)
    }

    @Test
    fun `AC-10 the lock carries the process start reported by the launcher so a live run survives sweep`() {
        card("a", Column.READY_FOR_DEV)
        val processStart = Instant.parse("2026-10-16T11:58:12Z")
        launcher.processStart = processStart
        launcher.nextPid = 4242
        val matchingProbe = ProcessProbe { pid, startedAt -> pid == 4242L && startedAt == processStart }
        val pool = pool(tracker = matchingProbe)
        val dispatcher = dispatcher(pool)

        dispatcher.runCycle()

        val lock = pool.active().single()
        assertThat(lock.startedAt).isEqualTo(processStart).isNotEqualTo(clock.instant())
        assertThat(pool.sweep()).isEmpty()
        assertThat(pool.isLocked("a")).isTrue()
    }

    @Test
    fun `AC-2 a card blocked by its cost limit starts no new run while other cards go on`() {
        card("a", Column.READY_FOR_DEV)
        card("b", Column.READY_FOR_DEV)
        val budget = budget(CostLimits(perCardUsd = BigDecimal("5"), perDayUsd = null))
        cost("a", "run-1", "6.00")
        budget.onRunFinished("a")
        val dispatcher = dispatcher(pool(), guard = budget)

        val started = dispatcher.runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "b"))
        assertThat(launcher.attempts).doesNotContain("a")
    }

    @Test
    fun `AC-3 with the day limit reached no run starts and no card is blocked`() {
        card("a", Column.READY_FOR_DEV)
        val budget = budget(CostLimits(perCardUsd = null, perDayUsd = BigDecimal("10")))
        cost("a", "run-1", "10.00")
        val dispatcher = dispatcher(pool(), guard = budget)

        val started = dispatcher.runCycle()

        assertThat(started).isEmpty()
        assertThat(launcher.attempts).isEmpty()
        assertThat(budget.isBlocked("a")).isFalse()
        assertThat(store.cards().single { it.id == "a" }.card.column).isEqualTo(Column.READY_FOR_DEV)
        assertThat(store.eventsAfter(0).map { it.event.type.name }).doesNotContain("CARD_BLOCKED")
    }

    @Test
    fun `AC-4 on the next day runs start again and the day cost counts from zero`() {
        card("a", Column.READY_FOR_DEV)
        val budget = budget(CostLimits(perCardUsd = null, perDayUsd = BigDecimal("10")))
        cost("a", "run-1", "10.00")
        val dispatcher = dispatcher(pool(), guard = budget)
        assertThat(dispatcher.runCycle()).isEmpty()

        clock.advance(Duration.ofHours(24))
        val started = dispatcher.runCycle()

        assertThat(budget.dayLimitReached()).isFalse()
        assertThat(started).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "a"))
    }

    @Test
    fun `AC-6 with no limits configured cost never restricts a run`() {
        card("a", Column.READY_FOR_DEV)
        val budget = budget(CostLimits.NONE)
        cost("a", "run-1", "100000.00")
        budget.onRunFinished("a")
        val dispatcher = dispatcher(pool(), guard = budget)

        val started = dispatcher.runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "a"))
        assertThat(store.eventsAfter(0).map { it.event.type.name }).doesNotContain("CARD_BLOCKED")
    }

    @Test
    fun `AC-6 refine starts no run without a free slot, for a blocked card or for a card in Spec Review`() {
        val refine = setOf(DispatchStage.REFINE)
        card("held", Column.READY_FOR_REFINEMENT)
        card("waiting", Column.READY_FOR_REFINEMENT)
        val full = pool(capacity = 1)
        probe.alive.add(77)
        full.tryStart("other", 77, DispatcherFixtures.start)
        assertThat(dispatcher(full, stages = refine).runCycle()).isEmpty()

        val budget = budget(CostLimits(perCardUsd = BigDecimal("5"), perDayUsd = null))
        val block = Event(EventId("block-held"), clock.instant(), Actor.Factory("cost"), EventType.CARD_BLOCKED, mapOf("reason" to "card_cost_limit"))
        recorder.record("held", Card.create("Card held", Column.READY_FOR_REFINEMENT), block)
        assertThat(budget.isBlocked("held")).isTrue()
        card("review", Column.SPEC_REVIEW)
        val started = dispatcher(pool(), guard = budget, stages = refine).runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.REFINE, "waiting"))
        assertThat(launcher.attempts).doesNotContain("held", "review")
    }

    @Test
    fun `AC-6 stages outside the configured set are ignored`() {
        card("r", Column.READY_FOR_REFINEMENT)
        card("d", Column.READY_FOR_DEV)
        card("v", Column.VALIDATION)

        val started = dispatcher(pool(), stages = setOf(DispatchStage.REFINE)).runCycle()

        assertThat(started).containsExactly(DispatchedRun(DispatchStage.REFINE, "r"))
        assertThat(launcher.launched).containsExactly(DispatchStage.REFINE to "r")
    }

    @Test
    fun `AC-7 a run in progress survives the day limit being crossed while no new run starts`() {
        card("a", Column.READY_FOR_DEV)
        val budget = budget(CostLimits(perCardUsd = null, perDayUsd = BigDecimal("10")))
        val pool = pool()
        val dispatcher = dispatcher(pool, guard = budget)
        assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "a"))

        card("b", Column.READY_FOR_DEV)
        cost("a", "run-1", "50.00")
        val second = dispatcher.runCycle()

        assertThat(second).isEmpty()
        assertThat(pool.isLocked("a")).isTrue()
        assertThat(launcher.launched.map { it.second }).containsExactly("a")
    }
}
