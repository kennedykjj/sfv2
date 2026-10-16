package sfv2.store

import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventJson

/** Writes a card and its event in one transaction. The event log has no update or delete operation. */
class CardEventStore(private val database: Database) {
    private val queries = database.queries.schemaQueries

    /** Upserts the card and appends the event atomically: a failure in either write rolls back both. */
    fun recordChange(cardId: String, card: Card, event: Event) {
        database.queries.transaction {
            queries.upsertCard(id = cardId, title = card.title, column_name = card.column.name)
            queries.updateCard(id = cardId, title = card.title, column_name = card.column.name)
            queries.insertEvent(
                id = event.id.value,
                cardId = cardId,
                at = event.at.toString(),
                actor = event.actor.kind(),
                type = event.type.name,
                payloadJson = EventJson.encode(event),
            )
        }
    }

    fun events(cardId: String): List<Event> =
        queries.eventPayloadsByCard(cardId).executeAsList().map { EventJson.decode(it) }

    fun card(cardId: String): Card? =
        queries.cardById(cardId).executeAsOneOrNull()
            ?.let { Card.create(it.title, Column.valueOf(it.column_name)) }

    private fun Actor.kind(): String = when (this) {
        Actor.Owner -> "owner"
        is Actor.Agent -> "agent"
        is Actor.Adapter -> "adapter"
        is Actor.Factory -> "factory"
    }
}
