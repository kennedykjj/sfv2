package sfv2.app

import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject

/** Returns the daemon's `card.detail` reply line; throws [IOException] when the daemon cannot be reached. */
fun interface CardDetailSource {
    fun fetch(id: String): String
}

enum class CostStatus { KNOWN, PARTIAL, UNKNOWN, NONE }

data class SpecView(val status: String, val text: String)

data class TimelineEvent(val id: String, val at: Instant, val actor: String, val type: String, val payload: Map<String, String>)

data class RunRow(
    val runId: String,
    val stage: String,
    val runtime: String,
    val status: CostStatus,
    val costUsd: BigDecimal?,
    val reason: String?,
)

data class CostTotal(val status: CostStatus, val costUsd: BigDecimal?)

data class CardDetail(
    val id: String,
    val title: String,
    val column: String,
    val spec: SpecView,
    val events: List<TimelineEvent>,
    val eventsTruncated: Boolean,
    val runs: List<RunRow>,
    val total: CostTotal,
)

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data class Loaded(val detail: CardDetail) : CardDetailState
    data object NotFound : CardDetailState
    data object Disconnected : CardDetailState
}

/** `US$ 0,42 est.`; `US$ ?` when the cost is unknown (never a silent zero). */
fun formatCost(costUsd: BigDecimal?): String {
    if (costUsd == null) return "US$ ?"
    val rounded = costUsd.setScale(2, RoundingMode.HALF_UP)
    return "US$ " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", rounded) + " est."
}

class CardDetailModel(private val cardId: String, private val source: CardDetailSource) {
    private val mutableState = MutableStateFlow<CardDetailState>(CardDetailState.Loading)
    val state: StateFlow<CardDetailState> = mutableState

    /** Fetches the detail synchronously; callers run it off the UI thread. */
    fun refresh() {
        mutableState.value = try {
            parseReply(source.fetch(cardId))
        } catch (e: IOException) {
            CardDetailState.Disconnected
        }
    }

    /** Called by the `events.subscribe` feed for each event; refetches only for this model's card. */
    fun onCardEvent(cardId: String) {
        if (cardId == this.cardId) refresh()
    }

    private fun parseReply(line: String): CardDetailState {
        val root = try {
            Json.parseToJsonElement(line).jsonObject
        } catch (e: IllegalArgumentException) {
            return CardDetailState.Disconnected
        }
        if (root.bool("ok") != true) return CardDetailState.NotFound
        val detail = root["detail"] as? JsonObject ?: return CardDetailState.Disconnected
        return try {
            CardDetailState.Loaded(parseDetail(detail))
        } catch (e: IllegalArgumentException) {
            CardDetailState.Disconnected
        }
    }

    private fun parseDetail(detail: JsonObject): CardDetail {
        val card = detail.obj("card")
        val spec = detail.obj("spec")
        val total = detail.obj("total")
        return CardDetail(
            id = card.str("id"),
            title = card.str("title"),
            column = card.str("column"),
            spec = SpecView(spec.str("status"), spec.str("text")),
            events = detail.arr("events").map { parseEvent(it.jsonObject) },
            eventsTruncated = detail.bool("truncated") ?: false,
            runs = detail.arr("runs").map { parseRun(it.jsonObject) },
            total = CostTotal(costStatus(total.str("status")), total.cost()),
        )
    }

    private fun parseEvent(event: JsonObject) = TimelineEvent(
        id = event.str("id"),
        at = Instant.parse(event.str("at")),
        actor = event.str("actor"),
        type = event.str("type"),
        payload = (event["payload"] as? JsonObject).orEmpty().mapValues { (_, value) -> value.asText() },
    )

    private fun parseRun(run: JsonObject) = RunRow(
        runId = run.str("run_id"),
        stage = run.str("stage"),
        runtime = run.str("runtime"),
        status = costStatus(run.str("status")),
        costUsd = run.cost(),
        reason = (run["reason"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
    )

    private fun costStatus(name: String): CostStatus =
        CostStatus.entries.firstOrNull { it.name == name } ?: CostStatus.UNKNOWN

    private fun JsonObject.cost(): BigDecimal? =
        (this["cost_usd"] as? JsonPrimitive)?.content?.let { raw ->
            try {
                BigDecimal(raw)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("invalid cost_usd in card.detail reply", e)
            }
        }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: throw IllegalArgumentException("missing $key in card.detail reply")

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("missing $key in card.detail reply")

    private fun JsonObject.arr(key: String): JsonArray =
        this[key] as? JsonArray ?: throw IllegalArgumentException("missing $key in card.detail reply")

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.let { if (it.isString) null else it.boolean }

    private fun JsonElement.asText(): String = (this as? JsonPrimitive)?.content ?: toString()
}
