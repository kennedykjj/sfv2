package sfv2.daemon

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sfv2.core.Actor
import sfv2.core.Column

/** A parsed client message. */
sealed interface Request {
    data object Ping : Request

    data class CardCreate(val actor: Actor, val title: String) : Request
    data class CardMove(val actor: Actor, val id: String, val to: Column) : Request
    data object CardList : Request
    data class CardShow(val id: String) : Request

    /** The line is not a valid request; [message] is safe to send back (never echoes the input). */
    data class Rejected(val message: String) : Request
}

/** A card as the client sees it. */
data class CardView(val id: String, val title: String, val column: Column)

/** A reply to the client. */
sealed interface Response {
    data object Pong : Response
    data class CardResult(val card: CardView) : Response
    data class CardsResult(val cards: List<CardView>) : Response
    data class Error(val message: String) : Response
}

/** Parses one protocol line. Pure: no IO. */
fun parseRequest(line: String): Request {
    if (line.isBlank()) return Request.Rejected("message must be a JSON object")
    val element = try {
        Json.parseToJsonElement(line)
    } catch (e: SerializationException) {
        return Request.Rejected("invalid JSON")
    }
    // kotlinx accepts unquoted text as a primitive literal; strict JSON does not.
    if (element is JsonPrimitive && !element.isString && !isJsonLiteral(element.content)) {
        return Request.Rejected("invalid JSON")
    }
    val obj = element as? JsonObject ?: return Request.Rejected("message must be a JSON object")
    val cmd = obj["cmd"] as? JsonPrimitive
    if (cmd == null || !cmd.isString) return Request.Rejected("missing cmd")
    return when (cmd.content) {
        "ping" -> Request.Ping
        "card.create" -> {
            val actor = parseActor(obj) ?: return Request.Rejected("missing or invalid actor")
            val title = stringField(obj, "title") ?: return Request.Rejected("missing title")
            Request.CardCreate(actor, title)
        }
        "card.move" -> {
            val actor = parseActor(obj) ?: return Request.Rejected("missing or invalid actor")
            val id = stringField(obj, "id") ?: return Request.Rejected("missing id")
            val to = stringField(obj, "to") ?: return Request.Rejected("missing to")
            val column = Column.entries.firstOrNull { it.name == to } ?: return Request.Rejected("unknown column")
            Request.CardMove(actor, id, column)
        }
        "card.list" -> Request.CardList
        "card.show" -> {
            val id = stringField(obj, "id") ?: return Request.Rejected("missing id")
            Request.CardShow(id)
        }
        else -> Request.Rejected("unknown cmd")
    }
}

private fun stringField(obj: JsonObject, key: String): String? {
    val primitive = obj[key] as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

/** Accepts `owner`, `agent:<role>` and `factory:<component>`. */
private fun parseActor(obj: JsonObject): Actor? {
    val text = stringField(obj, "actor") ?: return null
    if (text == "owner") return Actor.Owner
    val kind = text.substringBefore(':')
    val name = text.substringAfter(':', "")
    if (name.isBlank()) return null
    return when (kind) {
        "agent" -> Actor.Agent(name)
        "factory" -> Actor.Factory(name)
        else -> null
    }
}

private fun isJsonLiteral(text: String): Boolean =
    text == "null" || text == "true" || text == "false" || text.toDoubleOrNull() != null

private fun cardJson(card: CardView): JsonObject = buildJsonObject {
    put("id", card.id)
    put("title", card.title)
    put("column", card.column.name)
}

/** Renders a response as a single JSON line (without the newline). Pure: no IO. */
fun render(response: Response): String = when (response) {
    Response.Pong -> buildJsonObject {
        put("ok", true)
        put("result", "pong")
    }.toString()
    is Response.CardResult -> buildJsonObject {
        put("ok", true)
        put("result", cardJson(response.card))
    }.toString()
    is Response.CardsResult -> buildJsonObject {
        put("ok", true)
        put("result", JsonArray(response.cards.map { cardJson(it) }))
    }.toString()
    is Response.Error -> buildJsonObject {
        put("ok", false)
        put("error", response.message)
    }.toString()
}
