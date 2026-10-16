package sfv2.app

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// shipfloor:qa-stub Compile-only stand-ins for the production types of #68 under app/src/main/kotlin/sfv2/app/.
// The implementer deletes this file and implements them in main with these names and signatures (see the shape
// described in FeedTest.kt). Every behavior here is deliberately empty so the acceptance tests fail by assertion.

data class FeedEvent(
    val seq: Long,
    val card: String,
    val id: String,
    val at: Instant,
    val actor: String,
    val type: String,
    val payload: Map<String, String> = emptyMap(),
)

data class RecentEvents(val events: List<FeedEvent>, val truncated: Boolean)

interface FeedListener {
    fun onEvent(event: FeedEvent)
    fun onClosed()
}

interface FeedTransport {
    fun recent(limit: Int): RecentEvents
    fun subscribe(after: Long?, listener: FeedListener): AutoCloseable
}

enum class CostStatus { KNOWN, PARTIAL, UNKNOWN, NONE }

data class CardCost(val card: String, val costUsd: BigDecimal?, val status: CostStatus)

data class CostLimitsInfo(val perCardUsd: BigDecimal?, val perDayUsd: BigDecimal?)

data class CostSummary(
    val totalUsd: BigDecimal?,
    val status: CostStatus,
    val cards: List<CardCost>,
    val limits: CostLimitsInfo,
    val dayReached: Boolean,
    val cardsReached: Set<String>,
)

fun interface CostSummarySource {
    fun summary(from: Instant, to: Instant): CostSummary
}

enum class LimitState { NoLimit, Within, Reached }

enum class CostTag { Partial, Unknown }

data class CostLine(val label: String, val value: String, val tag: CostTag?, val limit: String?, val limitState: LimitState)

data class CostSummaryView(val total: CostLine, val cards: List<CostLine>, val explainsPartial: Boolean) {
    companion object {
        fun of(summary: CostSummary): CostSummaryView =
            CostSummaryView(CostLine("", "", null, null, LimitState.NoLimit), emptyList(), false)

        fun loading(): CostSummaryView = of(CostSummary(null, CostStatus.NONE, emptyList(), CostLimitsInfo(null, null), false, emptySet()))
    }
}

enum class FeedConnection { Live, Reconnecting }

data class FeedRow(val seq: Long, val time: String, val type: String, val actor: String, val card: String)

data class FeedState(
    val day: LocalDate,
    val rows: List<FeedRow>,
    val loading: Boolean,
    val connection: FeedConnection,
    val earlierHistoryUnavailable: Boolean,
    val cost: CostSummaryView,
)

class EventFeedClient(
    private val transport: FeedTransport,
    private val costSource: CostSummarySource,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    private val mutable = MutableStateFlow(
        FeedState(LocalDate.now(clock.withZone(zone)), emptyList(), true, FeedConnection.Live, false, CostSummaryView.loading()),
    )
    val state: StateFlow<FeedState> = mutable

    fun open() {}
    fun reconnect() {}
    fun selectDay(day: LocalDate) {}
}
