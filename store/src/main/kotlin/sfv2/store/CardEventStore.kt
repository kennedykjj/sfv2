package sfv2.store

import java.time.Instant
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventJson
import sfv2.core.ExternalRef
import sfv2.core.TextHash

/** A card together with its stored id. */
data class StoredCard(val id: String, val card: Card)

/** Writes a card and its event in one transaction. The event log has no update or delete operation. */
class CardEventStore(private val database: Database) {
    private val queries = database.queries.schemaQueries

    /** Upserts the card and appends the event atomically: a failure in either write rolls back both. */
    fun recordChange(cardId: String, card: Card, event: Event) {
        database.queries.transaction {
            // Insert-then-update keeps the card's rowid, so cards() order stays stable across changes.
            queries.insertCardIfAbsent(id = cardId, title = card.title, column_name = card.column.name)
            queries.updateCard(title = card.title, column_name = card.column.name, id = cardId)
            // The card is the whole state: the stored references mirror its list, in order.
            queries.deleteReferences(cardId)
            card.references.forEachIndexed { index, ref ->
                queries.insertReference(
                    cardId = cardId,
                    position = index.toLong(),
                    provider = ref.provider,
                    externalId = ref.externalId,
                    url = ref.url,
                    importedAt = ref.importedAt.toString(),
                    textHash = ref.textHash.value,
                )
            }
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
            ?.let { assemble(cardId, it.title, it.column_name) }

    /** All cards in insertion order. */
    fun cards(): List<StoredCard> =
        queries.allCards().executeAsList()
            .map { StoredCard(it.id, assemble(it.id, it.title, it.column_name)) }

    private fun assemble(cardId: String, title: String, columnName: String): Card =
        queries.referencesByCard(cardId).executeAsList().fold(Card.create(title, Column.valueOf(columnName))) { card, row ->
            card.withReference(
                ExternalRef(
                    provider = row.provider,
                    externalId = row.external_id,
                    url = row.url,
                    importedAt = Instant.parse(row.imported_at),
                    textHash = TextHash(row.text_hash),
                ),
            )
        }

    private fun Actor.kind(): String = when (this) {
        Actor.Owner -> "owner"
        is Actor.Agent -> "agent"
        is Actor.Adapter -> "adapter"
        is Actor.Factory -> "factory"
    }
}
