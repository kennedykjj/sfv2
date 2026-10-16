package sfv2.daemon

import sfv2.store.CardEventStore

// Temporary red-step signature; the engineer replaces it with the real ColumnTimesQuery.kt.

class ColumnTimesQuery(private val store: CardEventStore) {
    fun handle(request: Request): Response = Response.Error("not implemented")
}
