package sfv2.app

import java.io.IOException
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Shape the implementation must follow (deterministic: no sockets, no sleeps, no clock):
 *
 * - `fun interface CardDetailSource { fun fetch(id: String): String }` returns the daemon's `card.detail` reply line
 *   (the JSON of the spec's contract) and throws [IOException] when the daemon cannot be reached.
 * - `CardDetailModel(cardId: String, source: CardDetailSource)` with `val state: StateFlow<CardDetailState>`
 *   starting at `Loading`; `fun refresh()` fetches synchronously; `fun onCardEvent(cardId: String)` is what the
 *   `events.subscribe` feed calls for each event and refetches only when it is this model's card.
 * - `sealed interface CardDetailState`: `Loading`, `Loaded(detail: CardDetail)`, `NotFound`, `Disconnected`.
 * - `CardDetail(id, title, column, spec: SpecView(status, text), events: List<TimelineEvent(id, at, actor, type, payload)>,
 *   eventsTruncated: Boolean, runs: List<RunRow(runId, stage, runtime, status: CostStatus, costUsd: BigDecimal?, reason: String?)>,
 *   total: CostTotal(status: CostStatus, costUsd: BigDecimal?))`; `eventsTruncated` reads `detail.truncated` of the reply.
 * - `enum class CostStatus { KNOWN, PARTIAL, UNKNOWN }` and the pure `formatCost(costUsd: BigDecimal?): String`
 *   (`US$ 0,42 est.`; `US$ ?` when the cost is unknown).
 */
class CardDetailModelTest {

    private class FakeSource(var reply: () -> String) : CardDetailSource {
        var fetches = 0

        override fun fetch(id: String): String {
            fetches++
            return reply()
        }
    }

    private fun loaded(model: CardDetailModel): CardDetail =
        (model.state.value as CardDetailState.Loaded).detail

    private fun event(n: Int, actor: String = "agent:dev", type: String = "CARD_MOVED", payload: String = "{}") =
        """{"id":"e$n","at":"2026-10-16T10:00:%02dZ","actor":"$actor","type":"$type","payload":$payload}""".format(n)

    private fun runJson(n: Int, status: String, cost: String?, reason: String? = null): String {
        val costField = if (cost == null) "" else ""","cost_usd":"$cost""""
        val reasonField = if (reason == null) "" else ""","reason":"$reason""""
        return """{"run_id":"run-$n","at":"2026-10-16T10:01:%02dZ","stage":"develop","runtime":"claude","status":"$status","estimate":"true"$costField$reasonField}""".format(n)
    }

    private fun reply(
        events: List<String> = listOf(event(0, "owner", "CARD_CREATED")),
        runs: List<String> = emptyList(),
        total: String = """{"status":"KNOWN","cost_usd":"0"}""",
        spec: String = """{"status":"valid","text":"Do the thing"}""",
        truncated: Boolean = false,
    ) = """{"ok":true,"detail":{"card":{"id":"c1","title":"Detail card","column":"CODING"},"spec":$spec,""" +
        """"events":[${events.joinToString(",")}],"truncated":$truncated,"runs":[${runs.joinToString(",")}],"total":$total}}"""

    @Test
    fun `AC-7 a daemon that cannot be reached shows disconnected with no data and recovers on reconnect`() {
        val source = FakeSource { reply() }
        val model = CardDetailModel("c1", source)
        assertThat(model.state.value).isEqualTo(CardDetailState.Loading)
        model.refresh()
        assertThat(model.state.value).isInstanceOf(CardDetailState.Loaded::class.java)

        source.reply = { throw IOException("connection refused") }
        model.refresh()

        assertThat(model.state.value).isEqualTo(CardDetailState.Disconnected)

        source.reply = { reply() }
        model.refresh()
        assertThat(loaded(model).title).isEqualTo("Detail card")
    }

    @Test
    fun `AC-7 an unknown card shows not found`() {
        val model = CardDetailModel("nope", FakeSource { """{"ok":false,"error":"card not found: nope"}""" })

        model.refresh()

        assertThat(model.state.value).isEqualTo(CardDetailState.NotFound)
    }

    @Test
    fun `AC-8 a new event or run of this card shows without reopening and other cards are ignored`() {
        val source = FakeSource { reply() }
        val model = CardDetailModel("c1", source)
        model.refresh()
        assertThat(loaded(model).events).hasSize(1)
        assertThat(loaded(model).runs).isEmpty()
        val fetchesBefore = source.fetches

        source.reply = {
            reply(
                events = listOf(event(0, "owner", "CARD_CREATED"), event(1, "factory:run-cost", "RUN_COST_RECORDED")),
                runs = listOf(runJson(1, "KNOWN", "0.42")),
                total = """{"status":"KNOWN","cost_usd":"0.42"}""",
            )
        }
        model.onCardEvent("other-card")
        assertThat(source.fetches).isEqualTo(fetchesBefore)
        assertThat(loaded(model).events).hasSize(1)

        model.onCardEvent("c1")

        val detail = loaded(model)
        assertThat(detail.events.map { it.id }).containsExactly("e0", "e1")
        assertThat(detail.runs.map { it.runId }).containsExactly("run-1")
        assertThat(detail.total.costUsd).isEqualByComparingTo("0.42")
    }

    @Test
    fun `AC-9 the view carries the truncation warnings from the daemon instead of growing`() {
        val capped = (1..500).map { event(it % 60) }
        val model = CardDetailModel(
            "c1",
            FakeSource {
                reply(
                    events = capped,
                    truncated = true,
                    spec = """{"status":"valid","text":"first part of the spec\n\n[spec cortada]"}""",
                )
            },
        )

        model.refresh()

        val detail = loaded(model)
        assertThat(detail.eventsTruncated).isTrue()
        assertThat(detail.events).hasSize(500)
        assertThat(detail.spec.text).contains("spec cortada")
    }

    @Test
    fun `AC-10 an unknown run cost shows a question mark with the reason and the total is never a silent zero`() {
        val source = FakeSource {
            reply(
                runs = listOf(runJson(1, "KNOWN", "0.42"), runJson(2, "UNKNOWN", null, "no price for model m9")),
                total = """{"status":"PARTIAL","cost_usd":"0.42"}""",
            )
        }
        val model = CardDetailModel("c1", source)

        model.refresh()

        val detail = loaded(model)
        val unknown = detail.runs.single { it.runId == "run-2" }
        assertThat(unknown.status).isEqualTo(CostStatus.UNKNOWN)
        assertThat(unknown.costUsd).isNull()
        assertThat(unknown.reason).isEqualTo("no price for model m9")
        assertThat(formatCost(unknown.costUsd)).isEqualTo("US$ ?")
        assertThat(formatCost(detail.runs.single { it.runId == "run-1" }.costUsd)).isEqualTo("US$ 0,42 est.")
        assertThat(detail.total.status).isEqualTo(CostStatus.PARTIAL)

        source.reply = { reply(runs = listOf(runJson(2, "UNKNOWN", null, "no price")), total = """{"status":"UNKNOWN"}""") }
        model.refresh()

        val allUnknown = loaded(model).total
        assertThat(allUnknown.status).isEqualTo(CostStatus.UNKNOWN)
        assertThat(allUnknown.costUsd).isNull()
        assertThat(formatCost(allUnknown.costUsd)).isEqualTo("US$ ?")
        assertThat(formatCost(BigDecimal("1.356"))).isEqualTo("US$ 1,36 est.")
    }
}
