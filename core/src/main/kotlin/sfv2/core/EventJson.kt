package sfv2.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

/** Serialização do evento em JSON, montada à mão sobre a árvore do Jackson (sem IO: só string). */
object EventJson {
    private val mapper = ObjectMapper()

    fun encode(event: Event): String {
        val node = mapper.createObjectNode()
        node.put("id", event.id.value)
        node.put("at", event.at.toString())
        node.set<JsonNode>("actor", encodeActor(event.actor))
        node.put("type", event.type.name)
        val payload = node.putObject("payload")
        event.payload.forEach { (key, value) -> payload.put(key, value) }
        return mapper.writeValueAsString(node)
    }

    fun decode(json: String): Event {
        val node = mapper.readTree(json)
        return Event(
            id = EventId(node.path("id").asText()),
            at = Instant.parse(node.path("at").asText()),
            actor = decodeActor(node.path("actor")),
            type = decodeType(node.path("type").asText()),
            payload = node.path("payload").fields().asSequence().associate { it.key to it.value.asText() },
        )
    }

    private fun encodeActor(actor: Actor): ObjectNode {
        val node = mapper.createObjectNode()
        when (actor) {
            Actor.Owner -> node.put("kind", "owner")
            is Actor.Agent -> node.put("kind", "agent").put("role", actor.role)
            is Actor.Adapter -> node.put("kind", "adapter").put("provider", actor.provider)
            is Actor.Factory -> node.put("kind", "factory").put("component", actor.component)
        }
        return node
    }

    private fun decodeActor(node: JsonNode): Actor {
        val kind = node.path("kind").asText()
        return when (kind) {
            "owner" -> Actor.Owner
            "agent" -> Actor.Agent(node.path("role").asText())
            "adapter" -> Actor.Adapter(node.path("provider").asText())
            "factory" -> Actor.Factory(node.path("component").asText())
            else -> error("unknown actor kind: $kind")
        }
    }

    private fun decodeType(type: String): EventType =
        EventType.entries.firstOrNull { it.name == type } ?: throw UnknownEventTypeException(type)
}
