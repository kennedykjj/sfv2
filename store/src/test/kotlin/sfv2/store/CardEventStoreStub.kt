package sfv2.store

import sfv2.core.Card
import sfv2.core.Event

// shipfloor:qa-stub — exists only so the red gate compiles; the implementer deletes it.
class CardEventStore(private val database: Database) {
    fun recordChange(cardId: String, card: Card, event: Event): Unit = Unit

    fun events(cardId: String): List<Event> = emptyList()

    fun card(cardId: String): Card? = null
}
