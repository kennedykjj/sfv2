package sfv2.store

import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventJson

/** A card together with its stored id. */
data class StoredCard(val id: String, val card: Card)

/** An event with its database-assigned sequence number and card id. */
data class StoredEvent(val seq: Long, val cardId: String, val event: Event)

/** Writes a card and its event in one transaction. The event log has no update or delete operation. */
class CardEventStore(private val database: Database) {
    private val queries = database.queries.schemaQueries

    /** Upserts the card and appends the event atomically: a failure in either write rolls back both. */
    fun recordChange(cardId: String, card: Card, event: Event): Long =
        database.queries.transactionWithResult {
            // Insert-then-update keeps the card's rowid, so cards() order stays stable across changes.
            queries.insertCardIfAbsent(id = cardId, title = card.title, column_name = card.column.name)
            queries.updateCard(title = card.title, column_name = card.column.name, id = cardId)
            queries.insertEvent(
                id = event.id.value,
                cardId = cardId,
                at = event.at.toString(),
                actor = event.actor.kind(),
                type = event.type.name,
                payloadJson = EventJson.encode(event),
            )
            queries.eventSeqById(event.id.value).executeAsOne()
        }

    /** Events with seq greater than [seq], in seq order. */
    fun eventsAfter(seq: Long): List<StoredEvent> =
        queries.eventsAfter(seq).executeAsList()
            .map { StoredEvent(it.seq, it.card_id, EventJson.decode(it.payload_json)) }

    fun events(cardId: String): List<Event> =
        queries.eventPayloadsByCard(cardId).executeAsList().map { EventJson.decode(it) }

    fun card(cardId: String): Card? =
        queries.cardById(cardId).executeAsOneOrNull()
            ?.let { Card.create(it.title, Column.valueOf(it.column_name)) }

    /** All cards in insertion order. */
    fun cards(): List<StoredCard> =
        queries.allCards().executeAsList()
            .map { StoredCard(it.id, Card.create(it.title, Column.valueOf(it.column_name))) }

    private fun Actor.kind(): String = when (this) {
        Actor.Owner -> "owner"
        is Actor.Agent -> "agent"
        is Actor.Adapter -> "adapter"
        is Actor.Factory -> "factory"
    }
}
