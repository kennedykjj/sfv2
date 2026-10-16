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
import sfv2.store.StoredEvent

/** A parsed client message. */
sealed interface Request {
    data object Ping : Request

    data class CardCreate(val actor: Actor, val title: String, val proof: String? = null) : Request {
        override fun toString(): String = "CardCreate(actor=$actor, title=$title, proof=${if (proof == null) "null" else "***"})"
    }

    data class CardMove(val actor: Actor, val id: String, val to: Column, val proof: String? = null) : Request {
        override fun toString(): String = "CardMove(actor=$actor, id=$id, to=$to, proof=${if (proof == null) "null" else "***"})"
    }

    data class CardApprove(val actor: Actor, val id: String, val proof: String? = null) : Request {
        override fun toString(): String = "CardApprove(actor=$actor, id=$id, proof=${if (proof == null) "null" else "***"})"
    }

    data class CardRequestChange(val actor: Actor, val id: String, val note: String? = null, val proof: String? = null) : Request {
        override fun toString(): String =
            "CardRequestChange(actor=$actor, id=$id, note=${if (note == null) "null" else "***"}, proof=${if (proof == null) "null" else "***"})"
    }

    data class ProjectAdd(val actor: Actor, val path: String, val proof: String? = null) : Request {
        override fun toString(): String = "ProjectAdd(actor=$actor, path=$path, proof=${if (proof == null) "null" else "***"})"
    }

    data object ProjectList : Request

    data object CardList : Request
    data class CardShow(val id: String) : Request

    /** Starts the event stream; [after] is the last seq the client has seen, or null for live events only. */
    data class Subscribe(val after: Long?) : Request

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
    data class ProjectResult(val project: Project) : Response
    data class ProjectsResult(val projects: List<Project>) : Response
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
            Request.CardCreate(actor, title, stringField(obj, "proof"))
        }
        "card.move" -> {
            val actor = parseActor(obj) ?: return Request.Rejected("missing or invalid actor")
            val id = stringField(obj, "id") ?: return Request.Rejected("missing id")
            val to = stringField(obj, "to") ?: return Request.Rejected("missing to")
            val column = Column.entries.firstOrNull { it.name == to } ?: return Request.Rejected("unknown column")
            Request.CardMove(actor, id, column, stringField(obj, "proof"))
        }
        "card.approve" -> {
            val actor = parseActor(obj) ?: return Request.Rejected("missing or invalid actor")
            val id = stringField(obj, "id") ?: return Request.Rejected("missing id")
            Request.CardApprove(actor, id, stringField(obj, "proof"))
        }
        "card.request-change" -> {
            val actor = parseActor(obj) ?: return Request.Rejected("missing or invalid actor")
            val id = stringField(obj, "id") ?: return Request.Rejected("missing id")
            Request.CardRequestChange(actor, id, stringField(obj, "note"), stringField(obj, "proof"))
        }
        "project.add" -> {
            val actor = parseActor(obj) ?: return Request.Rejected("missing or invalid actor")
            val path = stringField(obj, "path") ?: return Request.Rejected("missing path")
            Request.ProjectAdd(actor, path, stringField(obj, "proof"))
        }
        "project.list" -> Request.ProjectList
        "card.list" -> Request.CardList
        "card.show" -> {
            val id = stringField(obj, "id") ?: return Request.Rejected("missing id")
            Request.CardShow(id)
        }
        "events.subscribe" -> parseSubscribe(obj)
        else -> Request.Rejected("unknown cmd")
    }
}

private fun parseSubscribe(obj: JsonObject): Request {
    val element = obj["after"] ?: return Request.Subscribe(null)
    val primitive = element as? JsonPrimitive
    val after = if (primitive == null || primitive.isString) null else primitive.content.toLongOrNull()
    if (after == null || after < 0) return Request.Rejected("invalid after")
    return Request.Subscribe(after)
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

private fun projectJson(project: Project): JsonObject = buildJsonObject {
    put("name", project.name)
    put("repo", project.repo)
    put(
        "layers",
        JsonArray(
            project.layers.map { layer ->
                buildJsonObject {
                    put("name", layer.name)
                    put("path", layer.path)
                }
            },
        ),
    )
    put(
        "policies",
        buildJsonObject {
            put("delivery_mode", project.policies.deliveryMode.key)
            put(
                "signers",
                buildJsonObject { project.policies.signers.forEach { (effect, signer) -> put(effect.key, signer.key) } },
            )
            put(
                "conventions",
                buildJsonObject {
                    put("commit_format", project.policies.conventions.commitFormat)
                    put("branch_format", project.policies.conventions.branchFormat)
                },
            )
        },
    )
}

private fun actorText(actor: Actor): String = when (actor) {
    Actor.Owner -> "owner"
    is Actor.Agent -> "agent:${actor.role}"
    is Actor.Adapter -> "adapter:${actor.provider}"
    is Actor.Factory -> "factory:${actor.component}"
}

/** Renders a stream event as a single JSON line (without the newline). Pure: no IO. */
fun renderEvent(stored: StoredEvent): String = buildJsonObject {
    put(
        "event",
        buildJsonObject {
            put("seq", stored.seq)
            put("card", stored.cardId)
            put("id", stored.event.id.value)
            put("at", stored.event.at.toString())
            put("actor", actorText(stored.event.actor))
            put("type", stored.event.type.name)
            put("payload", JsonObject(stored.event.payload.mapValues { JsonPrimitive(it.value) }))
        },
    )
}.toString()

/** Renders the line that ends a stream abnormally. Pure: no IO. */
fun renderStreamError(message: String): String = buildJsonObject { put("error", message) }.toString()

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
    is Response.ProjectResult -> buildJsonObject {
        put("ok", true)
        put("result", projectJson(response.project))
    }.toString()
    is Response.ProjectsResult -> buildJsonObject {
        put("ok", true)
        put("result", JsonArray(response.projects.map { projectJson(it) }))
    }.toString()
    is Response.Error -> buildJsonObject {
        put("ok", false)
        put("error", response.message)
    }.toString()
}
