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
import sfv2.ports.CloseResult
import sfv2.ports.ExternalText
import sfv2.ports.FakeTracker
import sfv2.ports.Tracker
import sfv2.ports.TrackerItem
import sfv2.ports.TrackerItemState
import sfv2.store.CardEventStore
import sfv2.store.Database

class CardClosureTest {
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
    private val fake = FakeTracker()
    private var confirmedWhenClosing: Boolean? = null
    private var sequence = 0

    /** Delegates to [fake] and notes whether the confirmation was already stored when the write started. */
    private val tracker: Tracker = object : Tracker by fake {
        override fun close(id: String, comment: String): CloseResult {
            confirmedWhenClosing = store.cards().any { c -> store.events(c.id).any { it.type.name == "CLOSURE_CONFIRMED" } }
            return fake.close(id, comment)
        }
    }

    /** Looks a production class up by name so a missing one fails an assertion, not the compilation. */
    private fun production(name: String): Class<*> {
        val found = runCatching { Class.forName(name) }.getOrNull()
        assertThat(found).describedAs("class $name exists").isNotNull()
        return requireNotNull(found)
    }

    private fun closure(): (String) -> Response {
        val constructor = production("sfv2.daemon.CardClosure").constructors.single { it.parameterCount == 7 }
        val trackerArg: Any = if (Tracker::class.java.isAssignableFrom(constructor.parameterTypes[3])) tracker else { _: Project -> tracker }
        val instance = constructor.newInstance(store, recorder, registry, trackerArg, proof, clock, DaemonLog { _, _ -> })
        val handle = instance.javaClass.methods.single { it.name == "handle" }
        return { line -> handle.invoke(instance, parseRequest(line)) as Response }
    }

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("closure.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
        registry = ProjectRegistry(object : ProjectConfigSource {
            override fun read(repo: Path): String =
                "project:\n  name: ${repo.fileName}\nlayers:\n  - name: daemon\n    path: daemon/\n"
        })
        val path = Files.createDirectories(dir.resolve("alpha"))
        Files.createDirectories(path.resolve(".git"))
        alpha = registry.register(path).repo
        proof = OwnerProof.create(dir.resolve("run").resolve("owner.token"), SecureRandom())
        token = Files.readString(dir.resolve("run").resolve("owner.token"))
    }

    @AfterEach
    fun close() {
        stream.close()
        database.close()
    }

    private fun item(id: String, state: TrackerItemState = TrackerItemState.OPEN): TrackerItem =
        TrackerItem.of(id, ExternalText("Issue title"), ExternalText("body"), emptyList(), "https://github.com/acme/alpha/issues/$id", state)

    private fun record(cardId: String, card: Card, type: EventType, payload: Map<String, String>, actor: Actor = Actor.Factory("deliver")) {
        recorder.record(cardId, card, Event(EventId("e-${sequence++}"), clock.instant(), actor, type, payload))
    }

    /** A card in [column] referencing issue [issue]; DONE cards carry the delivery events [merged] (PR_MERGED) and [movePr]. */
    private fun seed(
        column: Column = Column.DONE,
        title: String = "Local title",
        issue: String? = "7",
        merged: Pair<String, String>? = "42" to "abc1234",
        movePr: String? = null,
    ): String {
        val id = "card-${sequence++}"
        val base = Card.create(title, column)
        val card = issue?.let { i -> base.withReference(ExternalRef("github", i, "https://github.com/acme/alpha/issues/$i", clock.instant(), TextHash("h"))) } ?: base
        record(id, card, EventType.CARD_CREATED, mapOf("title" to title), Actor.Owner)
        store.assignProject(id, alpha)
        merged?.let { record(id, card, EventType.PR_MERGED, mapOf("pr" to it.first, "merge_commit" to it.second, "head" to "h1")) }
        if (column == Column.DONE) {
            val extra = movePr?.let { mapOf("pr" to it) } ?: emptyMap()
            record(id, card, EventType.CARD_MOVED, mapOf("from" to "STAGED", "to" to "DONE") + extra)
        }
        return id
    }

    private fun draft(id: String): Response = closure()("""{"cmd":"card.close.draft","id":"$id"}""")

    private fun confirm(id: String, hash: String, proofText: String = token, actor: String = "owner"): Response =
        closure()("""{"cmd":"card.close.confirm","actor":"$actor","id":"$id","draftHash":"$hash","proof":"$proofText"}""")

    private fun prop(response: Response, name: String): String {
        assertThat(response.javaClass.simpleName).isEqualTo("CloseDraftResult")
        val getter = response.javaClass.methods.single { it.name == "get" + name.replaceFirstChar { c -> c.uppercase() } }
        return getter.invoke(response) as String
    }

    private fun hashOf(id: String): String = prop(draft(id), "draftHash")

    private fun closureEvents(id: String): List<Event> = store.events(id).filter { it.type.name.startsWith("CLOSURE_") }

    private fun typesOf(id: String): List<String> = closureEvents(id).map { it.type.name }

    private fun nothingWritten(id: String) {
        assertThat(closureEvents(id)).isEmpty()
        assertThat(fake.calls["close"]).isNull()
        assertThat(fake.closures).isEmpty()
    }

    @Test
    fun `AC-1 the draft of a Done card with a github reference has status, summary and the PR link and writes nothing`() {
        fake.seed(item("7"))
        val id = seed()

        val response = draft(id)

        assertThat(prop(response, "status")).containsIgnoringCase("done")
        assertThat(prop(response, "summary")).contains(id).contains("abc1234")
        assertThat(prop(response, "prLink")).isEqualTo("https://github.com/acme/alpha/pull/42")
        assertThat(prop(response, "draftHash")).isNotBlank()
        nothingWritten(id)
    }

    @Test
    fun `AC-2 a card outside Done or without an external reference is refused for draft and confirmation`() {
        fake.seed(item("7"))
        val coding = seed(column = Column.CODING)
        val noRef = seed(issue = null)

        listOf(coding, noRef).forEach { id ->
            val refusedDraft = draft(id)
            assertThat(refusedDraft).describedAs("draft of $id").isInstanceOf(Response.Error::class.java)
            assertThat((refusedDraft as Response.Error).message).isNotBlank()
            assertThat(confirm(id, "anything")).describedAs("confirm of $id").isInstanceOf(Response.Error::class.java)
            nothingWritten(id)
        }
    }

    @Test
    fun `AC-3 the owner confirmation is stored before the tracker write and the write is the tracker close`() {
        fake.seed(item("7"))
        val id = seed()
        val hash = hashOf(id)

        val response = confirm(id, hash)

        assertThat(response).isNotInstanceOf(Response.Error::class.java)
        assertThat(confirmedWhenClosing).isTrue()
        assertThat(typesOf(id)).containsExactly("CLOSURE_CONFIRMED", "CLOSURE_WRITTEN")
        val events = closureEvents(id)
        assertThat(events[0].actor).isEqualTo(Actor.Owner)
        assertThat(events[0].payload["draft_hash"]).isEqualTo(hash)
        assertThat(events[1].actor).isInstanceOf(Actor.Factory::class.java)
        assertThat(events[1].payload["result"]).isEqualTo("closed")
        assertThat(fake.closures).containsOnlyKeys("7")
        assertThat(fake.closures.getValue("7")).contains("https://github.com/acme/alpha/pull/42")
    }

    @Test
    fun `AC-4 a confirmation without the owner proof is refused and nothing is recorded or written`() {
        fake.seed(item("7"))
        val id = seed()
        val hash = hashOf(id)

        assertThat(confirm(id, hash, proofText = "forged")).isInstanceOf(Response.Error::class.java)
        assertThat(confirm(id, hash, actor = "agent:coder")).isInstanceOf(Response.Error::class.java)
        assertThat(closure()("""{"cmd":"card.close.confirm","actor":"owner","id":"$id","draftHash":"$hash"}"""))
            .isInstanceOf(Response.Error::class.java)

        nothingWritten(id)
    }

    @Test
    fun `AC-5 a tracker failure leaves the card in Done with the confirmation and the failure recorded`() {
        fake.seed(item("7"))
        val id = seed(title = "Secret issue title")
        val hash = hashOf(id)
        fake.failNext("close")

        val response = confirm(id, hash)

        assertThat(response).isInstanceOf(Response.Error::class.java)
        assertThat(typesOf(id)).containsExactly("CLOSURE_CONFIRMED", "CLOSURE_FAILED")
        val failed = closureEvents(id).last()
        assertThat(failed.payload["reason"]).isNotBlank().doesNotContain("Secret issue title")
        assertThat(requireNotNull(store.card(id)).column).isEqualTo(Column.DONE)
        assertThat(fake.closures).isEmpty()
    }

    @Test
    fun `AC-6 a retry repeats the write without a new confirmation and an already closed item counts as written`() {
        fake.seed(item("7"))
        val id = seed()
        val hash = hashOf(id)
        fake.failNext("close")
        confirm(id, hash)

        val retry = confirm(id, hash)

        assertThat(retry).isNotInstanceOf(Response.Error::class.java)
        assertThat(typesOf(id)).containsExactly("CLOSURE_CONFIRMED", "CLOSURE_FAILED", "CLOSURE_WRITTEN")
        assertThat(fake.calls["close"]).isEqualTo(2)
        assertThat(fake.closures).containsOnlyKeys("7")

        assertThat(confirm(id, hash)).isNotInstanceOf(Response.Error::class.java)
        assertThat(fake.calls["close"]).isEqualTo(2)
        assertThat(typesOf(id)).hasSize(3)

        fake.seed(item("8", TrackerItemState.CLOSED))
        val other = seed(issue = "8")
        assertThat(confirm(other, hashOf(other))).isNotInstanceOf(Response.Error::class.java)
        assertThat(closureEvents(other).last().payload["result"]).isEqualTo("already_closed")
        assertThat(fake.closures).doesNotContainKey("8")
    }

    @Test
    fun `AC-7 a PR-mode card takes the link from the move to Done and a card with no PR is refused`() {
        fake.seed(item("7"))
        val delivered = seed(merged = null, movePr = "9")

        val response = draft(delivered)

        assertThat(prop(response, "prLink")).isEqualTo("https://github.com/acme/alpha/pull/9")
        assertThat(prop(response, "summary")).doesNotContain("abc1234")

        val none = seed(merged = null)
        val refused = draft(none)
        assertThat(refused).isInstanceOf(Response.Error::class.java)
        assertThat((refused as Response.Error).message).contains("no_pr_recorded")
        assertThat(confirm(none, "x")).isInstanceOf(Response.Error::class.java)
        nothingWritten(none)
    }

    @Test
    fun `AC-8 the comment written to the tracker never contains the card title`() {
        fake.seed(item("7"))
        val title = "@team fix [login](https://evil.example/x) **now**"
        val id = seed(title = title)

        val hash = hashOf(id)
        assertThat(confirm(id, hash)).isNotInstanceOf(Response.Error::class.java)

        val comment = fake.closures.getValue("7")
        assertThat(comment).contains(id).contains("abc1234").contains("https://github.com/acme/alpha/pull/42")
        assertThat(comment).doesNotContain("@team").doesNotContain("evil.example").doesNotContain("login").doesNotContain("**now**")
    }
}
