package sfv2.daemon

import sfv2.core.Event
import sfv2.store.CardEventStore

/**
 * Read-only `cost.summary`: sums the recorded run costs of a period with the same calculation as [CostBudget]
 * and says which totals reached the limits. [limitsOf] is read on every request, as the limits can change.
 */
class CostSummaryQuery(private val store: CardEventStore, private val limitsOf: () -> CostLimits) {

    constructor(store: CardEventStore, limits: CostLimits) : this(store, { limits })

    fun handle(request: Request): Response {
        if (request !is Request.CostSummary) return Response.Error("not a cost summary request")
        val limits = limitsOf()
        val byCard = store.costEventsBetween(request.from, request.to)
            .groupBy({ it.cardId }, { it.event })
            .toSortedMap()
        val spents = byCard.mapValues { (_, events) -> CostSums.sum(events) }
        val total = CostSums.total(spents.values)
        return Response.CostSummaryResult(
            totalUsd = total.usd,
            status = statusOf(byCard.values.flatten()),
            cards = byCard.map { (card, events) -> CardCost(card, spents.getValue(card).usd, statusOf(events)) },
            limits = limits,
            dayReached = total.reached(limits.perDayUsd),
            cardsReached = spents.filterValues { it.reached(limits.perCardUsd) }.keys.toList(),
        )
    }

    private fun statusOf(events: List<Event>): CostStatus {
        val statuses = events.map { event -> CostStatus.entries.firstOrNull { it.name == event.payload["status"] } ?: CostStatus.UNKNOWN }.toSet()
        return when {
            statuses.isEmpty() -> CostStatus.NONE
            statuses.size == 1 -> statuses.single()
            else -> CostStatus.PARTIAL
        }
    }
}
