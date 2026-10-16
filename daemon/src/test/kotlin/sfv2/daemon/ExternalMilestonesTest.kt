package sfv2.daemon

import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
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
import sfv2.core.ExternalRef
import sfv2.core.ProjectPolicies
import sfv2.core.TextHash
import sfv2.ports.ExternalText
import sfv2.ports.FakeTracker
import sfv2.ports.Tracker
import sfv2.ports.TrackerItem
import sfv2.ports.TrackerItemState
import sfv2.store.CardEventStore
import sfv2.store.Database

class ExternalMilestonesTest {
    @TempDir
    lateinit var dir: Path

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: EventRecorder
    private val tracker = FakeTracker()
    private val logged = mutableListOf<Pair<String, Map<String, String>>>()
    private val imported = item("Title", "imported body")
    private val cardId = "card-1"

    private fun item(title: String, body: String): TrackerItem =
        TrackerItem.of("7", ExternalText(title), ExternalText(body), emptyList(), "https://github.com/acme/alpha/issues/7", TrackerItemState.OPEN)

    private fun policies(vararg keys: String): ProjectPolicies =
        ProjectPolicies.from(mapOf("milestones" to keys.toList()))

    /** Looks production types up by name so a missing one fails an assertion, not the compilation. */
    private fun production(name: String): Class<*> {
        val found = runCatching { Class.forName(name) }.getOrNull()
        assertThat(found).describedAs("class $name exists").isNotNull()
        return requireNotNull(found)
    }

    private class Subject(private val instance: Any, private val milestones: Class<*>) {
        fun reached(cardId: String, milestone: String, detail: String? = null) {
            val constant = milestones.enumConstants.single { (it as Enum<*>).name == milestone }
            val method = instance.javaClass.methods.single { it.name == "reached" && it.parameterCount == 3 }
            try {
                method.invoke(instance, cardId, constant, detail)
            } catch (e: InvocationTargetException) {
                throw requireNotNull(e.cause)
            }
        }
    }

    private fun milestones(policies: ProjectPolicies, with: Tracker = tracker): Subject {
        val type = production("sfv2.daemon.ExternalMilestones")
        val constructor = type.constructors.single { it.parameterCount == 6 }
        val trackerOf: (Any?) -> Tracker = { with }
        val policiesOf: (Any?) -> ProjectPolicies = { policies }
        val instance = constructor.newInstance(store, recorder, trackerOf, policiesOf, clock, DaemonLog { event, fields -> synchronized(logged) { logged.add(event to fields) } })
        return Subject(instance, production("sfv2.core.Milestone"))
    }

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("milestones.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
        tracker.seed(imported)
        val ref = ExternalRef("github", "7", imported.url, clock.instant(), TextHash(imported.textHash))
        val seed = Event(EventId("seed"), clock.instant(), Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Title"))
        recorder.record(cardId, Card.create("Title", Column.CODING).withReference(ref), seed)
    }

    @AfterEach
    fun close() {
        stream.close()
        database.close()
    }

    private fun eventsOf(type: String): List<Event> = store.events(cardId).filter { it.type.name == type }

    @Test
    fun `AC-1 a started milestone is written once even when the trigger repeats`() {
        val subject = milestones(policies("started"))

        subject.reached(cardId, "STARTED")
        subject.reached(cardId, "STARTED")

        assertThat(tracker.milestones["7"]).containsExactly("started")
        val written = eventsOf("MILESTONE_WRITTEN").single()
        assertThat(written.payload["milestone"]).isEqualTo("started")
        assertThat(written.payload["externalId"]).isEqualTo("7")
        assertThat(written.actor).isEqualTo(Actor.Adapter("github"))
    }

    @Test
    fun `AC-2 an opened PR writes its link to the tracker once`() {
        val subject = milestones(policies("pr_opened"))

        subject.reached(cardId, "PR_OPENED", "12")
        subject.reached(cardId, "PR_OPENED", "12")

        assertThat(tracker.milestones["7"]).containsExactly("https://github.com/acme/alpha/pull/12")
        assertThat(eventsOf("MILESTONE_WRITTEN")).hasSize(1)
    }

    @Test
    fun `AC-4 a milestone that is off writes nothing to the tracker`() {
        val subject = milestones(policies("pr_opened"))
        val defaults = milestones(ProjectPolicies.DEFAULT)

        subject.reached(cardId, "STARTED")
        defaults.reached(cardId, "STARTED")
        defaults.reached(cardId, "PR_OPENED", "12")

        assertThat(tracker.milestones).isEmpty()
        assertThat(tracker.calls["writeMilestone"]).isNull()
        assertThat(eventsOf("MILESTONE_WRITTEN")).isEmpty()
    }

    @Test
    fun `AC-5 a tracker failure does not block the card and the milestone is retried on the next trigger`() {
        val subject = milestones(policies("started"))
        tracker.failNext("writeMilestone")

        subject.reached(cardId, "STARTED")

        assertThat(eventsOf("MILESTONE_WRITTEN")).isEmpty()
        assertThat(tracker.milestones).isEmpty()
        assertThat(logged.any { it.first == "milestone_failed" && it.second["card"] == cardId }).isTrue()
        assertThat(requireNotNull(store.card(cardId)).column).isEqualTo(Column.CODING)

        subject.reached(cardId, "STARTED")

        assertThat(tracker.milestones["7"]).containsExactly("started")
        assertThat(eventsOf("MILESTONE_WRITTEN")).hasSize(1)
    }

    @Test
    fun `AC-7 changed external text warns once per new hash and never carries the text`() {
        val subject = milestones(policies("started", "pr_opened"))
        val changed = item("Leaky title", "leaky body")
        tracker.seed(changed)

        subject.reached(cardId, "STARTED")
        subject.reached(cardId, "PR_OPENED", "12")

        val warning = eventsOf("EXTERNAL_TEXT_CHANGED").single()
        assertThat(warning.payload["externalId"]).isEqualTo("7")
        assertThat(warning.payload["previousHash"]).isEqualTo(imported.textHash)
        assertThat(warning.payload["newHash"]).isEqualTo(changed.textHash)
        assertThat(warning.payload.values.joinToString(" ")).doesNotContain("Leaky", "leaky")
        assertThat(logged.flatMap { it.second.values }.joinToString(" ")).doesNotContain("Leaky", "leaky")
        assertThat(tracker.milestones["7"]).hasSize(2)

        val again = item("Another title", "another body")
        tracker.seed(again)
        milestones(policies("started", "pr_opened")).reached(cardId, "STARTED")

        assertThat(eventsOf("EXTERNAL_TEXT_CHANGED").map { it.payload["newHash"] })
            .containsExactly(changed.textHash, again.textHash)
    }

    @Test
    fun `AC-7 unchanged external text emits no warning`() {
        milestones(policies("started")).reached(cardId, "STARTED")

        assertThat(eventsOf("EXTERNAL_TEXT_CHANGED")).isEmpty()
    }

    @Test
    fun `AC-8 two simultaneous triggers of the same milestone make one write and one event`() {
        val writes = AtomicInteger()
        val hold = CountDownLatch(1)
        val slow = object : Tracker by tracker {
            override fun writeMilestone(id: String, milestone: String) {
                writes.incrementAndGet()
                hold.await(300, TimeUnit.MILLISECONDS)
                tracker.writeMilestone(id, milestone)
            }
        }
        val subject = milestones(policies("started"), slow)
        val start = CyclicBarrier(2)
        val threads = List(2) {
            Thread {
                start.await()
                subject.reached(cardId, "STARTED")
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        assertThat(writes.get()).isEqualTo(1)
        assertThat(tracker.milestones["7"]).containsExactly("started")
        assertThat(eventsOf("MILESTONE_WRITTEN")).hasSize(1)
    }

    @Test
    fun `AC-9 an unexpected error never escapes and leaves the card as if the milestone were off`() {
        val before = store.events(cardId)
        val failingWrite = object : Tracker by tracker {
            override fun writeMilestone(id: String, milestone: String): Unit = throw IllegalStateException("boom")
        }
        val failingFetch = object : Tracker by tracker {
            override fun fetch(id: String): TrackerItem? = throw IllegalStateException("boom")
        }

        milestones(policies("started"), failingWrite).reached(cardId, "STARTED")
        milestones(policies("started"), failingFetch).reached(cardId, "STARTED")

        assertThat(store.events(cardId)).isEqualTo(before)
        assertThat(tracker.milestones).isEmpty()
        assertThat(logged.count { it.second["card"] == cardId }).isGreaterThanOrEqualTo(2)
        assertThat(requireNotNull(store.card(cardId)).column).isEqualTo(Column.CODING)
    }
}
