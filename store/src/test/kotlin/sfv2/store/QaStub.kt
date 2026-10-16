package sfv2.store

import sfv2.core.Card

// shipfloor:qa-stub - lets the acceptance tests compile before the feature exists; the implementer deletes this file.
data class StoredCard(val id: String, val card: Card)

fun CardEventStore.cards(): List<StoredCard> = emptyList()
