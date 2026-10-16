package sfv2.daemon

import java.math.BigDecimal
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class ProtocolTest {

    @Test
    fun `valid ping is parsed`() {
        assertThat(parseRequest("""{"cmd":"ping"}""")).isEqualTo(Request.Ping)
    }

    @Test
    fun `card commands parse actors and columns and reject bad ones`() {
        assertThat(parseRequest("""{"cmd":"card.create","actor":"factory:gate","title":"T"}"""))
            .isEqualTo(Request.CardCreate(Actor.Factory("gate"), "T"))
        assertThat(parseRequest("""{"cmd":"card.move","actor":"agent:dev","id":"x","to":"CODING"}"""))
            .isEqualTo(Request.CardMove(Actor.Agent("dev"), "x", Column.CODING))
        assertThat(parseRequest("""{"cmd":"card.move","actor":"owner","id":"x","to":"NOWHERE"}"""))
            .isEqualTo(Request.Rejected("unknown column"))
        assertThat(parseRequest("""{"cmd":"card.create","actor":"root","title":"T"}"""))
            .isEqualTo(Request.Rejected("missing or invalid actor"))
        assertThat(parseRequest("""{"cmd":"card.create","title":"T"}"""))
            .isEqualTo(Request.Rejected("missing or invalid actor"))
    }

    @Test
    fun `subscribe parses an optional non-negative numeric after`() {
        assertThat(parseRequest("""{"cmd":"events.subscribe"}""")).isEqualTo(Request.Subscribe(null))
        assertThat(parseRequest("""{"cmd":"events.subscribe","after":7}""")).isEqualTo(Request.Subscribe(7))
        assertThat(parseRequest("""{"cmd":"events.subscribe","after":"7"}""")).isEqualTo(Request.Rejected("invalid after"))
        assertThat(parseRequest("""{"cmd":"events.subscribe","after":-1}""")).isEqualTo(Request.Rejected("invalid after"))
        assertThat(parseRequest("""{"cmd":"events.subscribe","after":1.5}""")).isEqualTo(Request.Rejected("invalid after"))
    }

    @Test
    fun `stream error is a line with only the error`() {
        assertThat(renderStreamError("subscriber too slow")).isEqualTo("""{"error":"subscriber too slow"}""")
    }

    @Test
    fun `AC-1 proof is parsed on create and move and never printed by toString`() {
        val create = parseRequest("""{"cmd":"card.create","actor":"owner","title":"T","proof":"s3cr3tvalue"}""")
        val move = parseRequest("""{"cmd":"card.move","actor":"owner","id":"x","to":"CODING","proof":"s3cr3tvalue"}""")

        assertThat((create as Request.CardCreate).proof).isEqualTo("s3cr3tvalue")
        assertThat((move as Request.CardMove).proof).isEqualTo("s3cr3tvalue")
        assertThat(create.toString()).doesNotContain("s3cr3tvalue")
        assertThat(move.toString()).doesNotContain("s3cr3tvalue")
        assertThat((parseRequest("""{"cmd":"card.create","actor":"owner","title":"T"}""") as Request.CardCreate).proof)
            .isNull()
    }

    @Test
    fun `invalid json is rejected without echoing the input`() {
        val request = parseRequest("secret-not-json")

        assertThat(request).isEqualTo(Request.Rejected("invalid JSON"))
    }

    @Test
    fun `non object json is rejected`() {
        assertThat(parseRequest("[1,2]")).isEqualTo(Request.Rejected("message must be a JSON object"))
    }

    @Test
    fun `missing cmd is rejected`() {
        assertThat(parseRequest("""{"foo":1}""")).isEqualTo(Request.Rejected("missing cmd"))
    }

    @Test
    fun `non text cmd is rejected`() {
        assertThat(parseRequest("""{"cmd":5}""")).isEqualTo(Request.Rejected("missing cmd"))
    }

    @Test
    fun `unknown cmd is rejected`() {
        assertThat(parseRequest("""{"cmd":"explode"}""")).isEqualTo(Request.Rejected("unknown cmd"))
    }

    @Test
    fun `pong renders as a single json line`() {
        assertThat(render(Response.Pong)).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `AC-4 report csv request returns one csv line per Done card and nothing for the others`(@TempDir dir: Path) {
        Database.open(dir.resolve("report.db")).use { db ->
            val store = CardEventStore(db)
            val t0 = Instant.parse("2026-10-16T10:00:00Z")
            fun record(id: String, title: String, column: Column, vararg events: Event) =
                events.forEach { store.recordChange(id, Card.create(title, column), it) }
            record(
                "done-1", "Shipped", Column.DONE,
                Event(EventId("e1"), t0, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Shipped")),
                Event(EventId("e2"), t0.plusSeconds(90), Actor.Owner, EventType.CARD_MOVED, mapOf("from" to "INBOX", "to" to "DONE")),
            )
            record(
                "open-1", "Still coding", Column.CODING,
                Event(EventId("e3"), t0, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Still coding")),
            )
            val commands = CardCommands(store, Clock.fixed(t0, ZoneOffset.UTC), OwnerProof.create(dir.resolve("owner.token"), SecureRandom()))

            val reply = Json.parseToJsonElement(render(commands.handle(parseRequest("""{"cmd":"report.csv"}""")))).jsonObject
            commands.events.close()

            assertThat(reply.getValue("ok").jsonPrimitive.boolean).isTrue()
            val lines = reply.getValue("csv").jsonPrimitive.content.trim().lines()
            assertThat(lines).hasSize(2)
            assertThat(lines[0]).startsWith("card_id,title,").endsWith("cost_usd_estimate,cost_status")
            assertThat(lines[1]).startsWith("done-1,\"Shipped\",90,")
            assertThat(lines[1].split(",")).hasSameSizeAs(lines[0].split(","))
        }
    }

    private fun recordCost(store: CardEventStore, card: String, id: String, at: Instant, usd: String) =
        store.recordChange(
            card,
            Card.create(card, Column.CODING),
            Event(
                EventId(id), at, Actor.Factory("cost"), EventType.RUN_COST_RECORDED,
                mapOf("run_id" to id, "status" to "KNOWN", "cost_usd" to usd, "estimate" to "true"),
            ),
        )

    private fun costSummary(dir: Path, limits: CostLimits): JsonObject {
        Database.open(dir.resolve("cost.db")).use { db ->
            val store = CardEventStore(db)
            recordCost(store, "a", "e1", Instant.parse("2026-10-16T12:00:00Z"), "6.00")
            recordCost(store, "b", "e2", Instant.parse("2026-10-16T13:00:00Z"), "2.00")
            // Outside the requested day: must not count.
            recordCost(store, "b", "e3", Instant.parse("2026-10-17T03:00:00Z"), "50.00")
            val request = parseRequest("""{"cmd":"cost.summary","from":"2026-10-16T03:00:00Z","to":"2026-10-17T03:00:00Z"}""")
            val reply = Json.parseToJsonElement(render(CostSummaryQuery(store, limits).handle(request))).jsonObject
            assertThat(reply.getValue("ok").jsonPrimitive.boolean).isTrue()
            return reply.getValue("result").jsonObject
        }
    }

    @Test
    fun `AC-6 cost summary delivers the limits and which totals reached them`(@TempDir dir: Path) {
        val result = costSummary(dir, CostLimits(perCardUsd = BigDecimal("5.00"), perDayUsd = BigDecimal("8.00")))

        assertThat(BigDecimal(result.getValue("totalUsd").jsonPrimitive.content)).isEqualByComparingTo("8.00")
        assertThat(result.getValue("status").jsonPrimitive.content).isEqualTo("KNOWN")
        val limits = result.getValue("limits").jsonObject
        assertThat(BigDecimal(limits.getValue("perCardUsd").jsonPrimitive.content)).isEqualByComparingTo("5.00")
        assertThat(BigDecimal(limits.getValue("perDayUsd").jsonPrimitive.content)).isEqualByComparingTo("8.00")
        // The day total equals its limit: reached, as CostBudget decides.
        assertThat(result.getValue("dayReached").jsonPrimitive.boolean).isTrue()
        assertThat(result.getValue("cardsReached").jsonArray.map { it.jsonPrimitive.content }).containsExactly("a")
        val perCard = result.getValue("cards").jsonArray.associate {
            it.jsonObject.getValue("card").jsonPrimitive.content to BigDecimal(it.jsonObject.getValue("costUsd").jsonPrimitive.content)
        }
        assertThat(perCard.getValue("a")).isEqualByComparingTo("6.00")
        assertThat(perCard.getValue("b")).isEqualByComparingTo("2.00")
    }

    @Test
    fun `AC-6 cost summary without limits reports null limits and nothing reached`(@TempDir dir: Path) {
        val result = costSummary(dir, CostLimits.NONE)

        val limits = result.getValue("limits").jsonObject
        assertThat(limits.getValue("perCardUsd")).isEqualTo(JsonNull)
        assertThat(limits.getValue("perDayUsd")).isEqualTo(JsonNull)
        assertThat(result.getValue("dayReached").jsonPrimitive.boolean).isFalse()
        assertThat(result.getValue("cardsReached").jsonArray).isEmpty()
        assertThat(BigDecimal(result.getValue("totalUsd").jsonPrimitive.content)).isEqualByComparingTo("8.00")
    }

    @Test
    fun `AC-6 cost summary request with an invalid instant is rejected`() {
        assertThat(parseRequest("""{"cmd":"cost.summary","from":"yesterday","to":"2026-10-17T03:00:00Z"}"""))
            .isInstanceOf(Request.Rejected::class.java)
        assertThat(parseRequest("""{"cmd":"cost.summary","from":"2026-10-16T03:00:00Z"}"""))
            .isInstanceOf(Request.Rejected::class.java)
    }

    @Test
    fun `error message with quotes is escaped`() {
        val rendered = render(Response.Error("bad \"thing\""))

        assertThat(rendered).isEqualTo("""{"ok":false,"error":"bad \"thing\""}""")
    }
}
