package sfv2.daemon

import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

// shipfloor:qa-stub - only so the red gate compiles; the implementer deletes this file.

class ModelStepUp(private val ladder: List<String>, store: CardEventStore) {
    fun modelFor(cardId: String): String = ladder.first()

    fun onEvent(event: StoredEvent) = Unit
}
