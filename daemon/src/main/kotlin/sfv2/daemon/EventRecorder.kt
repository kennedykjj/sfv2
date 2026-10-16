package sfv2.daemon

import sfv2.core.Card
import sfv2.core.Event
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

/**
 * The single writer of events in the daemon: stores a change and publishes it under one monitor, so
 * subscribers see every event in strict `seq` order. Takes no other lock, so it cannot deadlock.
 */
class EventRecorder(
    private val store: CardEventStore,
    private val events: EventStream,
) {
    @Synchronized
    fun record(cardId: String, card: Card, event: Event): StoredEvent {
        val stored = StoredEvent(store.recordChange(cardId, card, event), cardId, event)
        events.publish(stored)
        return stored
    }
}

/**
 * The run pool's view of the store: the card lookup and the write of one change. The pool writes events only
 * through [EventRecorder]; this seam lets tests make a write fail. It lives here because this file is the only
 * place allowed to call `recordChange`.
 */
interface LockEventRecorder {
    fun card(id: String): Card?

    fun recordChange(id: String, card: Card, event: Event): Long
}

/** [LockEventRecorder] over the real store. */
internal class StoreLockEventRecorder(private val store: CardEventStore) : LockEventRecorder {
    override fun card(id: String): Card? = store.card(id)

    override fun recordChange(id: String, card: Card, event: Event): Long = store.recordChange(id, card, event)
}

/** Writes one change through the seam and reports it, without publishing (test and tooling use only). */
internal fun LockEventRecorder.store(id: String, card: Card, event: Event): StoredEvent =
    StoredEvent(recordChange(id, card, event), id, event)
