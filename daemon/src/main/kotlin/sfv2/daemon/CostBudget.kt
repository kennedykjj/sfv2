package sfv2.daemon

import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore

/** Decides whether the dispatcher may start a run for a card. */
fun interface LaunchGuard {
    fun allows(cardId: String): Boolean

    /** True when no run may start today, whatever the card. */
    fun dayLimitReached(): Boolean = false

    companion object {
        val Open: LaunchGuard = LaunchGuard { true }
    }
}

/** Cost ceilings in USD; null means no limit. */
data class CostLimits(val perCardUsd: BigDecimal?, val perDayUsd: BigDecimal?) {
    companion object {
        val NONE = CostLimits(null, null)
    }
}

/** Blocks cards and stops the day when the recorded run costs reach the limits. Fails closed on unpriced models. */
class CostBudget(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val limits: CostLimits,
    private val clock: Clock,
    private val zone: ZoneId,
    private val log: DaemonLog = StderrDaemonLog,
) : LaunchGuard {

    private data class Spent(val usd: BigDecimal, val unpriced: Boolean)

    fun onRunFinished(cardId: String) {
        val limit = limits.perCardUsd ?: return
        if (isBlocked(cardId)) return
        val card = store.card(cardId) ?: return
        val costs = store.events(cardId).filter { it.type == EventType.RUN_COST_RECORDED }
        val spent = sum(cardId, costs, logZero = true)
        val reason = when {
            spent.unpriced -> "unpriced_model"
            spent.usd >= limit -> "card_cost_limit"
            else -> return
        }
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Factory("cost"),
            type = EventType.CARD_BLOCKED,
            payload = mapOf(
                "reason" to reason,
                "spent_usd" to spent.usd.toPlainString(),
                "limit_usd" to limit.toPlainString(),
                "estimate" to "true",
            ),
        )
        recorder.record(cardId, card, event)
        log.log("card_blocked_by_cost", mapOf("card" to cardId, "reason" to reason))
    }

    fun isBlocked(cardId: String): Boolean {
        val lastBlock = store.lastSeq(cardId, EventType.CARD_BLOCKED) ?: return false
        val lastOwnerMove = store.lastSeq(cardId, EventType.CARD_MOVED, Actor.Owner) ?: return true
        return lastOwnerMove < lastBlock
    }

    override fun dayLimitReached(): Boolean {
        val limit = limits.perDayUsd ?: return false
        val today = LocalDate.now(clock.withZone(zone))
        val from = today.atStartOfDay(zone).toInstant()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        val byCard = store.costEventsBetween(from, to)
            .groupBy({ it.cardId }, { it.event })
        val spents = byCard.map { (cardId, events) -> sum(cardId, events, logZero = false) }
        if (spents.any { it.unpriced }) return true
        return spents.fold(BigDecimal.ZERO) { acc, s -> acc + s.usd } >= limit
    }

    override fun allows(cardId: String): Boolean = !isBlocked(cardId)

    private fun sum(cardId: String, costs: List<Event>, logZero: Boolean): Spent {
        var total = BigDecimal.ZERO
        var unpriced = false
        for (event in costs) {
            val usd = event.payload["cost_usd"]?.let { runCatching { BigDecimal(it) }.getOrNull() }
            if (usd != null) {
                total += usd
            } else if (event.payload["status"] == "UNKNOWN") {
                if (event.payload["reason"]?.startsWith(UNPRICED_PREFIX) == true) {
                    unpriced = true
                } else if (logZero) {
                    log.log(
                        "run_cost_unknown_counted_zero",
                        mapOf("card" to cardId, "run" to (event.payload["run_id"] ?: "unknown")),
                    )
                }
            }
        }
        return Spent(total, unpriced)
    }

    private companion object {
        const val UNPRICED_PREFIX = "no price for models"
    }
}
