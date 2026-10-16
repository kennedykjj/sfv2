package sfv2.daemon

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A parsed client message. */
sealed interface Request {
    data object Ping : Request

    /** The line is not a valid request; [message] is safe to send back (never echoes the input). */
    data class Rejected(val message: String) : Request
}

/** A reply to the client. */
sealed interface Response {
    data object Pong : Response
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
        else -> Request.Rejected("unknown cmd")
    }
}

private fun isJsonLiteral(text: String): Boolean =
    text == "null" || text == "true" || text == "false" || text.toDoubleOrNull() != null

/** Renders a response as a single JSON line (without the newline). Pure: no IO. */
fun render(response: Response): String = when (response) {
    Response.Pong -> buildJsonObject {
        put("ok", true)
        put("result", "pong")
    }.toString()
    is Response.Error -> buildJsonObject {
        put("ok", false)
        put("error", response.message)
    }.toString()
}
