package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import sfv2.core.TextHash
import sfv2.ports.ExternalText
import sfv2.ports.FakeTracker
import sfv2.ports.Tracker
import sfv2.ports.TrackerItem
import sfv2.ports.TrackerItemState
import sfv2.store.CardEventStore
import sfv2.store.Database

class CardImportTest {
    @TempDir
    lateinit var dir: Path

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: EventRecorder
    private lateinit var registry: ProjectRegistry
    private lateinit var proof: OwnerProof
    private lateinit var token: String
    private lateinit var alpha: String
    private lateinit var beta: String
    private val tracker = FakeTracker()
    private var sequence = 0

    /** Looks a production class up by name so a missing one fails an assertion, not the compilation. */
    private fun production(name: String): Class<*> {
        val found = runCatching { Class.forName(name) }.getOrNull()
        assertThat(found).describedAs("class $name exists").isNotNull()
        return requireNotNull(found)
    }

    private fun importer(): (String) -> Response {
        val constructor = production("sfv2.daemon.CardImport").constructors.single { it.parameterCount == 7 }
        val trackerArg: Any = if (Tracker::class.java.isAssignableFrom(constructor.parameterTypes[3])) tracker else { _: Project -> tracker as Tracker }
        val instance = constructor.newInstance(store, recorder, registry, trackerArg, proof, clock, DaemonLog { _, _ -> })
        val handle = instance.javaClass.methods.single { it.name == "handle" }
        return { line -> handle.invoke(instance, parseRequest(line)) as Response }
    }

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("import.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
        registry = ProjectRegistry(object : ProjectConfigSource {
            override fun read(repo: Path): String =
                "project:\n  name: ${repo.fileName}\nlayers:\n  - name: daemon\n    path: daemon/\n"
        })
        alpha = register("alpha")
        beta = register("beta")
        proof = OwnerProof.create(dir.resolve("run").resolve("owner.token"), SecureRandom())
        token = Files.readString(dir.resolve("run").resolve("owner.token"))
    }

    private fun register(name: String): String {
        val path = Files.createDirectories(dir.resolve(name))
        Files.createDirectories(path.resolve(".git"))
        return registry.register(path).repo
    }

    @AfterEach
    fun close() {
        stream.close()
        database.close()
    }

    private fun item(
        id: String,
        title: String = "Issue title",
        body: String = "body",
        state: TrackerItemState = TrackerItemState.OPEN,
    ): TrackerItem =
        TrackerItem.of(id, ExternalText(title), ExternalText(body), emptyList(), "https://github.com/acme/alpha/issues/$id", state)

    private fun importById(id: String, project: String = "alpha", proofText: String = token, actor: String = "owner"): Response =
        importer()("""{"cmd":"card.import","actor":"$actor","project":"$project","id":"$id","proof":"$proofText"}""")

    private fun importQuery(query: String, project: String = "alpha"): Response =
        importer()("""{"cmd":"card.import","actor":"owner","project":"$project","query":"$query","proof":"$token"}""")

    private fun cardsOf(response: Response): List<CardView> = (response as Response.CardsResult).cards

    private fun withRef(externalId: String): List<String> =
        store.cards().filter { c -> c.card.references.any { it.provider == "github" && it.externalId == externalId } }.map { it.id }

    private fun reimports(cardId: String): List<Event> = store.events(cardId).filter { it.type.name == "CARD_REIMPORTED" }

    private fun seedCard(external: TrackerItem, column: Column, title: String = "Local title"): String {
        val id = "card-${sequence++}"
        val ref = ExternalRef("github", external.id, external.url, clock.instant(), TextHash(external.textHash))
        val seed = Event(EventId("seed-${sequence++}"), clock.instant(), Actor.Owner, EventType.CARD_CREATED, mapOf("title" to title))
        recorder.record(id, Card.create(title, column).withReference(ref), seed)
        store.assignProject(id, alpha)
        return id
    }

    private fun nothingStored() {
        assertThat(store.cards()).isEmpty()
        assertThat(store.eventsAfter(0)).isEmpty()
    }

    @Test
    fun `AC-1 importing an issue creates an Inbox card with the title and the external reference`() {
        val source = item("7", "Add login")
        tracker.seed(source)

        val cards = cardsOf(importById("7"))

        assertThat(cards).hasSize(1)
        val card = requireNotNull(store.card(cards.single().id))
        assertThat(cards.single().title).isEqualTo("Add login")
        assertThat(card.title).isEqualTo("Add login")
        assertThat(card.column).isEqualTo(Column.INBOX)
        assertThat(card.references).containsExactly(
            ExternalRef("github", "7", source.url, clock.instant(), TextHash(source.textHash)),
        )
        assertThat(store.projectOf(cards.single().id)).isEqualTo(alpha)
    }

    @Test
    fun `AC-2 the creation event is authored by the provider adapter`() {
        tracker.seed(item("7"))

        val id = cardsOf(importById("7")).single().id

        val events = store.events(id)
        assertThat(events).hasSize(1)
        assertThat(events.single().type).isEqualTo(EventType.CARD_CREATED)
        assertThat(events.single().actor).isEqualTo(Actor.Adapter("github"))
        assertThat(events.single().payload["origin"]).isEqualTo("imported")
        assertThat(events.single().payload["provider"]).isEqualTo("github")
        assertThat(events.single().payload["externalId"]).isEqualTo("7")
    }

    @Test
    fun `AC-3 importing the same issue again creates no second card`() {
        tracker.seed(item("7"))

        importById("7")
        importById("7")

        assertThat(store.cards()).hasSize(1)
        assertThat(withRef("7")).hasSize(1)
    }

    @Test
    fun `AC-4 a reimport with changed text updates title and hash and records the event`() {
        tracker.seed(item("7", "Old title", "old body"))
        val id = cardsOf(importById("7")).single().id
        val changed = item("7", "New title", "new body")
        tracker.seed(changed)

        importById("7")

        val card = requireNotNull(store.card(id))
        assertThat(card.title).isEqualTo("New title")
        assertThat(card.references.single().textHash).isEqualTo(TextHash(changed.textHash))
        val event = reimports(id).single()
        assertThat(event.payload["changed"]).isEqualTo("true")
        assertThat(event.payload["title"]).isEqualTo("New title")
        assertThat(event.payload["provider"]).isEqualTo("github")
        assertThat(event.payload["externalId"]).isEqualTo("7")
    }

    @Test
    fun `AC-5 a reimport with unchanged text records the event and leaves the card as it was`() {
        tracker.seed(item("7"))
        val id = cardsOf(importById("7")).single().id
        val before = requireNotNull(store.card(id))

        importById("7")

        assertThat(store.card(id)).isEqualTo(before)
        assertThat(reimports(id).single().payload["changed"]).isEqualTo("false")
        assertThat(store.cards()).hasSize(1)
    }

    @Test
    fun `AC-6 a search imports each result as an Inbox card without duplicating imported ones`() {
        tracker.seed(item("1", "Search topic one"))
        tracker.seed(item("2", "Search topic two"))
        tracker.seed(item("3", "Search topic three"))
        importById("2")

        val created = cardsOf(importQuery("topic"))

        assertThat(created.map { it.title }).containsExactlyInAnyOrder("Search topic one", "Search topic three")
        assertThat(store.cards()).hasSize(3)
        assertThat(store.cards().map { it.card.column }).containsOnly(Column.INBOX)
        assertThat(withRef("2")).hasSize(1)
        assertThat(reimports(withRef("2").single())).isEmpty()
        cardsOf(importQuery("topic"))
        assertThat(store.cards()).hasSize(3)
    }

    @Test
    fun `AC-7 a missing issue or a tracker failure creates nothing and names the external id`() {
        val missing = importById("404")
        assertThat(missing).isInstanceOf(Response.Error::class.java)
        assertThat((missing as Response.Error).message).contains("404")
        nothingStored()

        tracker.seed(item("8"))
        tracker.failNext("fetch")
        val failed = importById("8")
        assertThat(failed).isInstanceOf(Response.Error::class.java)
        assertThat((failed as Response.Error).message).contains("8")
        nothingStored()

        tracker.failNext("search")
        assertThat(importQuery("Issue")).isInstanceOf(Response.Error::class.java)
        nothingStored()
    }

    @Test
    fun `AC-8 a request that is not from the owner is refused and nothing is created`() {
        tracker.seed(item("7"))

        assertThat(importById("7", proofText = "forged")).isInstanceOf(Response.Error::class.java)
        assertThat(importById("7", actor = "agent:coder")).isInstanceOf(Response.Error::class.java)
        assertThat(importer()("""{"cmd":"card.import","actor":"owner","project":"alpha","query":"Issue"}"""))
            .isInstanceOf(Response.Error::class.java)

        nothingStored()
    }

    @Test
    fun `AC-9 the same issue number in two projects makes one card per project`() {
        tracker.seed(item("7"))

        importById("7", project = "alpha")
        importById("7", project = "beta")

        assertThat(withRef("7")).hasSize(2)
        assertThat(withRef("7").map { store.projectOf(it) }).containsExactlyInAnyOrder(alpha, beta)
        importById("7", project = "beta")
        assertThat(withRef("7")).hasSize(2)
    }

    @Test
    fun `AC-10 the card title is normalized and never empty`() {
        val long = "a".repeat(250)
        val titles = mapOf(
            "1" to ("Fix\u0000the\nbug\t\tnow" to "Fix the bug now"),
            "2" to ("too    many   spaces" to "too many spaces"),
            "3" to ("   \n\t  " to "Issue 3"),
            "4" to (long to "a".repeat(200)),
            "5" to ("" to "Issue 5"),
        )
        titles.forEach { (id, pair) -> tracker.seed(item(id, pair.first)) }

        titles.forEach { (id, pair) ->
            val response = importById(id)
            assertThat(response).describedAs("issue $id").isInstanceOf(Response.CardsResult::class.java)
            assertThat(cardsOf(response).single().title).describedAs("issue $id").isEqualTo(pair.second)
            assertThat(store.card(cardsOf(response).single().id)?.title).isEqualTo(pair.second)
        }
    }

    @Test
    fun `AC-11 a reimport after Ready for Dev keeps the title and flags it frozen`() {
        val old = item("7", "Old title", "old")
        val id = seedCard(old, Column.CODING, title = "Old title")
        val changed = item("7", "New title", "new")
        tracker.seed(changed)

        importById("7")

        val card = requireNotNull(store.card(id))
        assertThat(card.title).isEqualTo("Old title")
        assertThat(card.column).isEqualTo(Column.CODING)
        assertThat(card.references.single().textHash).isEqualTo(TextHash(changed.textHash))
        val event = reimports(id).single()
        assertThat(event.payload["changed"]).isEqualTo("true")
        assertThat(event.payload["title_frozen"]).isEqualTo("true")
        assertThat(store.cards()).hasSize(1)
    }

    @Test
    fun `AC-3 of #55 a reimport with a different hash warns the owner with ids and hashes only`() {
        val old = item("7", "Old title", "old secret body")
        val id = seedCard(old, Column.CODING, title = "Old title")
        val changed = item("7", "New title", "new secret body")
        tracker.seed(changed)

        importById("7")

        val warnings = store.events(id).filter { it.type.name == "EXTERNAL_TEXT_CHANGED" }
        assertThat(warnings).hasSize(1)
        val warning = warnings.single()
        assertThat(warning.payload["externalId"]).isEqualTo("7")
        assertThat(warning.payload["previousHash"]).isEqualTo(old.textHash)
        assertThat(warning.payload["newHash"]).isEqualTo(changed.textHash)
        assertThat(warning.payload.values.joinToString(" ")).doesNotContain("title", "secret")

        importById("7")
        assertThat(store.events(id).filter { it.type.name == "EXTERNAL_TEXT_CHANGED" }).hasSize(1)
    }

    @Test
    fun `AC-3 of #55 a reimport with the same hash emits no warning`() {
        tracker.seed(item("7"))
        val id = cardsOf(importById("7")).single().id

        importById("7")

        assertThat(store.events(id).filter { it.type.name == "EXTERNAL_TEXT_CHANGED" }).isEmpty()
    }

    @Test
    fun `AC-12 a search skips closed issues while an import by id accepts one`() {
        tracker.seed(item("1", "Topic open"))
        tracker.seed(item("2", "Topic closed", state = TrackerItemState.CLOSED))

        val created = cardsOf(importQuery("Topic"))

        assertThat(created.map { it.title }).containsExactly("Topic open")
        assertThat(withRef("2")).isEmpty()

        val byId = cardsOf(importById("2"))
        assertThat(byId.map { it.title }).containsExactly("Topic closed")
        assertThat(withRef("2")).hasSize(1)
    }
}
