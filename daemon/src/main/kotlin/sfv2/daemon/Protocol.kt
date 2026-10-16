package sfv2.daemon

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

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

private val MAPPER = ObjectMapper()

/** Parses one protocol line. Pure: no IO. */
fun parseRequest(line: String): Request {
    val node: JsonNode = try {
        MAPPER.readTree(line)
    } catch (e: JsonProcessingException) {
        null
    } ?: return Request.Rejected("invalid JSON")
    if (!node.isObject) return Request.Rejected("message must be a JSON object")
    val cmd = node.get("cmd")
    if (cmd == null || !cmd.isTextual) return Request.Rejected("missing cmd")
    return when (cmd.asText()) {
        "ping" -> Request.Ping
        else -> Request.Rejected("unknown cmd")
    }
}

/** Renders a response as a single JSON line (without the newline). Pure: no IO. */
fun render(response: Response): String = when (response) {
    Response.Pong -> MAPPER.writeValueAsString(linkedMapOf("ok" to true, "result" to "pong"))
    is Response.Error -> MAPPER.writeValueAsString(linkedMapOf("ok" to false, "error" to response.message))
}
