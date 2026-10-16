package sfv2.daemon

import sfv2.store.CardEventStore

// shipfloor:qa-stub Compile-only stand-in for the production class the plan puts in
// daemon/src/main/kotlin/sfv2/daemon/CostSummaryQuery.kt. The implementer deletes this file and creates the real
// `CostSummaryQuery(store, limits)` whose `handle(request)` answers a parsed `cost.summary` request
// (a `Request.CostSummary`) with the contract of the spec; ProtocolTest only renders the Response with `render`.
class CostSummaryQuery(private val store: CardEventStore, private val limits: CostLimits) {
    fun handle(request: Request): Response = Response.Error("cost summary is not available")
}
