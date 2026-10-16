package sfv2.core

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

object EventFixtures {
    val at: Instant = Instant.parse("2026-10-15T12:00:00Z")

    fun anEvent(
        id: String = "evt-1",
        actor: Actor = Actor.Owner,
        type: EventType = EventType.CARD_CREATED,
        payload: Map<String, String> = mapOf("card" to "9"),
    ): Event = Event(id = EventId(id), at = at, actor = actor, type = type, payload = payload)
}

class EventTest {
    @Test
    fun `AC-1 event carries id, instant, actor, type and payload`() {
        val event = EventFixtures.anEvent(
            id = "evt-42",
            actor = Actor.Agent("engineer"),
            type = EventType.CARD_MOVED,
            payload = mapOf("from" to "todo", "to" to "doing"),
        )

        assertThat(event.id).isEqualTo(EventId("evt-42"))
        assertThat(event.at).isEqualTo(EventFixtures.at)
        assertThat(event.actor).isEqualTo(Actor.Agent("engineer"))
        assertThat(event.type).isEqualTo(EventType.CARD_MOVED)
        assertThat(event.payload).containsExactlyInAnyOrderEntriesOf(mapOf("from" to "todo", "to" to "doing"))
    }

    @Test
    fun `AC-2 every actor kind survives a JSON round trip`() {
        val actors = listOf(
            Actor.Owner,
            Actor.Agent("engineer"),
            Actor.Adapter("github"),
            Actor.Factory("scheduler"),
        )

        actors.forEach { actor ->
            val event = EventFixtures.anEvent(actor = actor, type = EventType.OWNER_APPROVED)

            val decoded = EventJson.decode(EventJson.encode(event))

            assertThat(decoded).isEqualTo(event)
            assertThat(decoded.actor).isEqualTo(actor)
        }
    }

    @Test
    fun `AC-3 reading an unknown event type fails naming the type`() {
        val json = EventJson.encode(EventFixtures.anEvent(type = EventType.CARD_CREATED))
            .replace("CARD_CREATED", "TIME_TRAVEL")

        val error = assertThrows<UnknownEventTypeException> { EventJson.decode(json) }

        assertThat(error).hasMessageContaining("TIME_TRAVEL")
    }
}
