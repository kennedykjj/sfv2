package sfv2.daemon

import sfv2.core.Card
import sfv2.core.Event
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

/**
 * The single writer of events in the daemon: stores a change and publishes it under one monitor, so
 * subscribers see every event in strict `seq` order. Takes no other lock, so it cannot deadlock.
 */
open class EventRecorder(
    private val store: CardEventStore,
    private val events: EventStream,
) {
    @Synchronized
    open fun record(cardId: String, card: Card, event: Event): StoredEvent {
        val stored = StoredEvent(store.recordChange(cardId, card, event), cardId, event)
        events.publish(stored)
        return stored
    }
}
