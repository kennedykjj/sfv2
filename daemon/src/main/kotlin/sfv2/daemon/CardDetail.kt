package sfv2.daemon

import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sfv2.core.Card
import sfv2.core.Event
import sfv2.core.EventType
import sfv2.store.SpecRead

/** Builds the `card.detail` answer: card, spec, events and runs with their cost. Pure: no IO. */
object CardDetail {
    const val MAX_EVENTS = 500
    const val MAX_SPEC_BYTES = 64 * 1024
    const val MAX_PAYLOAD_CHARS = 512
    const val SPEC_CUT_NOTICE = "\n\n[spec cortada: o texto passou de 64 KiB]"

    fun build(id: String, card: Card, spec: SpecRead, events: List<Event>): JsonObject {
        val kept = events.takeLast(MAX_EVENTS)
        val runs = events.filter { it.type == EventType.RUN_COST_RECORDED }.map { run(it.payload) }
        return buildJsonObject {
            put(
                "card",
                buildJsonObject {
                    put("id", id)
                    put("title", card.title)
                    put("column", card.column.name)
                },
            )
            put("spec", specJson(spec))
            put("events", JsonArray(kept.map { eventJson(it) }))
            put("truncated", events.size > kept.size)
            put("runs", JsonArray(runs.map { it.json }))
            put("total", total(runs))
        }
    }

    private data class RunView(val json: JsonObject, val cost: BigDecimal?)

    private fun run(payload: Map<String, String>): RunView {
        val cost = if (payload["status"] == "KNOWN") payload["cost_usd"]?.toBigDecimalOrNull() else null
        val json = buildJsonObject {
            put("run_id", payload["run_id"].orEmpty())
            put("stage", payload["stage"].orEmpty())
            put("runtime", payload["runtime"].orEmpty())
            if (cost != null) {
                put("status", "KNOWN")
                put("cost_usd", cost.toPlainString())
            } else {
                put("status", "UNKNOWN")
                put("reason", cut(payload["reason"] ?: "cost not measured", MAX_PAYLOAD_CHARS))
            }
            payload["tokens"]?.let { put("tokens", cut(it, MAX_PAYLOAD_CHARS)) }
            put("estimate", payload["estimate"] ?: "true")
        }
        return RunView(json, cost)
    }

    private fun total(runs: List<RunView>): JsonObject {
        val known = runs.mapNotNull { it.cost }
        val sum = known.fold(BigDecimal.ZERO, BigDecimal::add)
        val status = when {
            known.size == runs.size -> "KNOWN"
            known.isEmpty() -> "UNKNOWN"
            else -> "PARTIAL"
        }
        return buildJsonObject {
            put("status", status)
            if (status != "UNKNOWN") put("cost_usd", sum.toPlainString())
        }
    }

    private fun specJson(spec: SpecRead): JsonObject = buildJsonObject {
        when (spec) {
            is SpecRead.Valid -> {
                put("status", "valid")
                put("text", cutSpec(spec.text))
            }
            is SpecRead.Diverged -> put("status", "diverged")
            is SpecRead.NotRegistered -> put("status", "missing")
        }
    }

    private fun cutSpec(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_SPEC_BYTES) return text
        var end = MAX_SPEC_BYTES
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return String(bytes, 0, end, Charsets.UTF_8) + SPEC_CUT_NOTICE
    }

    private fun eventJson(event: Event): JsonObject = buildJsonObject {
        put("id", event.id.value)
        put("at", event.at.toString())
        put("actor", actorText(event.actor))
        put("type", event.type.name)
        put("payload", JsonObject(event.payload.mapValues { JsonPrimitive(cut(it.value, MAX_PAYLOAD_CHARS)) }))
    }

    private fun cut(value: String, max: Int): String = if (value.length <= max) value else value.take(max - 1) + "…"
}
