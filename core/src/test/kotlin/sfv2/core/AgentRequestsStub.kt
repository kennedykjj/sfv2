package sfv2.core

import java.time.Instant

// shipfloor:qa-stub - lets the acceptance tests compile before the implementation exists; the implementer deletes this file.
class AgentRequestRefusedException(message: String) : RuntimeException(message)

object AgentRequests {
    fun reportCompletion(cardId: String, card: Card, role: String, summary: String, id: EventId, at: Instant): Event =
        Event(id, at, Actor.Owner, EventType.CARD_CREATED, emptyMap())

    fun requestWrite(cardId: String, card: Card, role: String, path: String, bytes: Int, id: EventId, at: Instant): Event =
        Event(id, at, Actor.Owner, EventType.CARD_CREATED, emptyMap())
}
