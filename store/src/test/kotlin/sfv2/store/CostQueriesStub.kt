package sfv2.store

import java.time.Instant
import sfv2.core.Actor
import sfv2.core.EventType

// shipfloor:qa-stub — lets the acceptance tests compile before CardEventStore gets the real members
// (members win over these extensions). The implementer deletes this file.
@Suppress("unused")
fun CardEventStore.costEventsBetween(from: Instant, toExclusive: Instant): List<StoredEvent> = emptyList()

@Suppress("unused")
fun CardEventStore.lastSeq(cardId: String, type: EventType, actor: Actor? = null): Long? = null
