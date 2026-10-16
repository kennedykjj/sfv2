package sfv2.app

import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sfv2.core.Column

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
 *
 * Closure screen (#71), the controller part (`ClosureModel.kt`): `ClosureCommands.draft/confirm` answer with the sealed
 * `DraftOutcome` / `ConfirmOutcome`; `ClosureController(commands, io)` exposes `state: StateFlow<ClosureState>`
 * (`Loading`, `Draft(status, summary, prLink, draftHash, notSent)`, `Refused(reason)`, `Confirming(draftHash)`, `Closed`,
 * `Failed(reason, draftHash)`); `open(cardId, events)` seeds from the card timeline (chronological, last `CLOSURE_*` wins)
 * and `confirm()` sends from `Draft` or `Failed` and is a no-op in any other state.
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
        column: String = "CODING",
    ) = """{"ok":true,"detail":{"card":{"id":"c1","title":"Detail card","column":"$column"},"spec":$spec,""" +
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

    @Test
    fun `AC-6 once the approval event reaches the view the new column shows, the actions go away and the timeline has the owner approval`() =
        runBlocking {
            val created = event(0, "owner", "CARD_CREATED")
            val source = FakeSource { reply(events = listOf(created), column = "SPEC_REVIEW") }
            val model = CardDetailModel("c1", source)
            model.refresh()
            val commands = FakeOwnerCommands()
            val controller = SpecReviewController(commands, Dispatchers.Unconfined)
            assertThat(specReviewActionsVisible(model.state.value)).isTrue()

            controller.approve("c1")

            // Accepted, but the daemon event has not arrived: the column is still the one the daemon last reported.
            assertThat(commands.calls).containsExactly(SentMove("c1", Column.SPEC_REVIEW, Column.READY_FOR_DEV, null))
            assertThat(controller.state.value.sent).isTrue()
            assertThat(loaded(model).column).isEqualTo("SPEC_REVIEW")
            assertThat(specReviewActionsVisible(model.state.value)).isTrue()

            val approval = event(1, "owner", "OWNER_APPROVED", """{"from":"SPEC_REVIEW","to":"READY_FOR_DEV"}""")
            source.reply = { reply(events = listOf(created, approval), column = "READY_FOR_DEV") }
            model.onCardEvent("c1")
            controller.onDetail(loaded(model))

            assertThat(loaded(model).column).isEqualTo("READY_FOR_DEV")
            assertThat(specReviewActionsVisible(model.state.value)).isFalse()
            assertThat(controller.state.value.sent).isFalse()
            val signed = loaded(model).events.last()
            assertThat(signed.type).isEqualTo("OWNER_APPROVED")
            assertThat(signed.actor).isEqualTo("owner")
        }

    @Test
    fun `AC-6 a change request event sends the card back to refinement, hides the actions and shows the signed request with its note`() =
        runBlocking {
            val created = event(0, "owner", "CARD_CREATED")
            val source = FakeSource { reply(events = listOf(created), column = "SPEC_REVIEW") }
            val model = CardDetailModel("c1", source)
            model.refresh()
            val controller = SpecReviewController(FakeOwnerCommands(), Dispatchers.Unconfined)

            controller.requestChange("c1", "cover offline")
            assertThat(controller.state.value.sent).isTrue()

            val change = event(
                1,
                "owner",
                "OWNER_CHANGE_REQUESTED",
                """{"from":"SPEC_REVIEW","to":"READY_FOR_REFINEMENT","note":"cover offline"}""",
            )
            source.reply = { reply(events = listOf(created, change), column = "READY_FOR_REFINEMENT") }
            model.onCardEvent("c1")
            controller.onDetail(loaded(model))

            assertThat(loaded(model).column).isEqualTo("READY_FOR_REFINEMENT")
            assertThat(specReviewActionsVisible(model.state.value)).isFalse()
            assertThat(controller.state.value.sent).isFalse()
            val signed = loaded(model).events.last()
            assertThat(signed.actor).isEqualTo("owner")
            assertThat(signed.payload["note"]).isEqualTo("cover offline")
        }

    @Test
    fun `AC-1 opening the closure screen asks the daemon for the draft and shows it without confirming`() = runBlocking {
        val fx = ClosureFixtures.controller()

        fx.controller.open("c1", ClosureFixtures.timeline())

        assertThat(fx.commands.drafts).containsExactly("c1")
        assertThat(fx.commands.confirms).isEmpty()
        assertThat(fx.controller.state.value).isEqualTo(ClosureFixtures.aDraftState("h1"))
    }

    @Test
    fun `AC-1 a truncated timeline without a closure event still asks for the draft`() = runBlocking {
        val fx = ClosureFixtures.controller()

        fx.controller.open("c1", ClosureFixtures.timeline())

        assertThat(fx.commands.drafts).containsExactly("c1")
        assertThat(fx.controller.state.value).isInstanceOf(ClosureState.Draft::class.java)
    }

    @Test
    fun `AC-2 a refused draft shows the daemon reason and confirming sends nothing`() = runBlocking {
        val fx = ClosureFixtures.controller()
        fx.commands.draftReply = { DraftOutcome.Refused("no_pr_recorded") }

        fx.controller.open("c1", ClosureFixtures.timeline())
        fx.controller.confirm()

        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Refused("no_pr_recorded"))
        assertThat(fx.commands.confirms).isEmpty()
    }

    @Test
    fun `AC-3 confirming sends the displayed draft hash and shows closed when the daemon accepts`() = runBlocking {
        val fx = ClosureFixtures.controller()
        fx.controller.open("c1", ClosureFixtures.timeline())
        var stateWhileInFlight: ClosureState? = null
        fx.commands.confirmReply = {
            stateWhileInFlight = fx.controller.state.value
            ConfirmOutcome.Closed
        }

        fx.controller.confirm()

        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"))
        assertThat(stateWhileInFlight).isEqualTo(ClosureState.Confirming("h1"))
        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
    }

    @Test
    fun `AC-4 a tracker write error shows the error, keeps the draft hash and retry resends the same confirm`() = runBlocking {
        val fx = ClosureFixtures.controller()
        fx.controller.open("c1", ClosureFixtures.timeline())
        fx.commands.confirmReply = { ConfirmOutcome.Failed("tracker unavailable") }

        fx.controller.confirm()

        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Failed("tracker unavailable", "h1"))

        fx.commands.confirmReply = { ConfirmOutcome.Closed }
        fx.controller.confirm()

        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"), SentConfirm("c1", "h1"))
        assertThat(fx.commands.drafts).containsExactly("c1")
        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
    }

    @Test
    fun `AC-4 a changed draft fetches a new one and waits for a new confirmation`() = runBlocking {
        val fx = ClosureFixtures.controller()
        fx.controller.open("c1", ClosureFixtures.timeline())
        fx.commands.confirmReply = { ConfirmOutcome.DraftChanged }
        fx.commands.draftReply = { ClosureFixtures.aDrafted("h2") }

        fx.controller.confirm()

        assertThat(fx.commands.drafts).containsExactly("c1", "c1")
        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"))
        assertThat(fx.controller.state.value).isEqualTo(ClosureFixtures.aDraftState("h2"))

        fx.commands.confirmReply = { ConfirmOutcome.Closed }
        fx.controller.confirm()

        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"), SentConfirm("c1", "h2"))
        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
    }

    @Test
    fun `AC-5 a confirm that did not reach the daemon keeps the draft visible and allows another try`() = runBlocking {
        val fx = ClosureFixtures.controller()
        fx.controller.open("c1", ClosureFixtures.timeline())
        fx.commands.confirmReply = { ConfirmOutcome.NotSent(NotSentCause.NO_OWNER_PROOF) }

        fx.controller.confirm()

        assertThat(fx.controller.state.value)
            .isEqualTo(ClosureFixtures.aDraftState("h1").copy(notSent = NotSentCause.NO_OWNER_PROOF))

        fx.commands.confirmReply = { ConfirmOutcome.Closed }
        fx.controller.confirm()

        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"), SentConfirm("c1", "h1"))
        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
    }

    @Test
    fun `AC-6 a timeline ending in CLOSURE_WRITTEN shows closed and asks for nothing`() = runBlocking {
        val fx = ClosureFixtures.controller()
        val events = ClosureFixtures.timeline(
            ClosureFixtures.anEvent(1, "CLOSURE_CONFIRMED", mapOf("draft_hash" to "h1")),
            ClosureFixtures.anEvent(2, "CLOSURE_FAILED", mapOf("reason" to "tracker unavailable")),
            ClosureFixtures.anEvent(3, "CLOSURE_WRITTEN", mapOf("result" to "closed")),
        )

        fx.controller.open("c1", events)
        fx.controller.confirm()

        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
        assertThat(fx.commands.drafts).isEmpty()
        assertThat(fx.commands.confirms).isEmpty()
    }

    @Test
    fun `AC-6 a timeline whose last closure event is CLOSURE_FAILED shows the error and retries the confirmed hash`() = runBlocking {
        val fx = ClosureFixtures.controller()
        val events = ClosureFixtures.timeline(
            ClosureFixtures.anEvent(1, "CLOSURE_CONFIRMED", mapOf("draft_hash" to "h1")),
            ClosureFixtures.anEvent(2, "CLOSURE_FAILED", mapOf("reason" to "tracker unavailable")),
        )

        fx.controller.open("c1", events)

        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Failed("tracker unavailable", "h1"))
        assertThat(fx.commands.drafts).isEmpty()

        fx.controller.confirm()

        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"))
        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
    }

    @Test
    fun `AC-7 a second confirm while one is in flight is not sent`() = runBlocking {
        val fx = ClosureFixtures.controller()
        fx.controller.open("c1", ClosureFixtures.timeline())
        fx.commands.confirmReply = {
            runBlocking { fx.controller.confirm() }
            ConfirmOutcome.Closed
        }

        fx.controller.confirm()

        assertThat(fx.commands.confirms).containsExactly(SentConfirm("c1", "h1"))
        assertThat(fx.controller.state.value).isEqualTo(ClosureState.Closed)
    }
}

data class SentConfirm(val cardId: String, val draftHash: String)

class FakeClosureCommands : ClosureCommands {
    val drafts = mutableListOf<String>()
    val confirms = mutableListOf<SentConfirm>()
    var draftReply: () -> DraftOutcome = { ClosureFixtures.aDrafted("h1") }
    var confirmReply: () -> ConfirmOutcome = { ConfirmOutcome.Closed }

    override fun draft(cardId: String): DraftOutcome {
        drafts += cardId
        return draftReply()
    }

    override fun confirm(cardId: String, draftHash: String): ConfirmOutcome {
        confirms += SentConfirm(cardId, draftHash)
        return confirmReply()
    }
}

class ClosureControllerFixture {
    val commands = FakeClosureCommands()
    val controller = ClosureController(commands, Dispatchers.Unconfined)
}

object ClosureFixtures {
    fun controller() = ClosureControllerFixture()

    fun aDrafted(hash: String) = DraftOutcome.Drafted("Entregue", "Resumo do card", "https://github.com/o/r/pull/7", hash)

    fun aDraftState(hash: String) = ClosureState.Draft("Entregue", "Resumo do card", "https://github.com/o/r/pull/7", hash)

    fun anEvent(n: Int, type: String, payload: Map<String, String> = emptyMap()) =
        TimelineEvent("e$n", Instant.parse("2026-10-16T10:00:%02dZ".format(n)), "factory", type, payload)

    /** Chronological timeline: a delivery to Done first, then whatever closure events the test passes. */
    fun timeline(vararg closure: TimelineEvent): List<TimelineEvent> =
        listOf(anEvent(0, "CARD_MOVED", mapOf("to" to "DONE"))) + closure
}
