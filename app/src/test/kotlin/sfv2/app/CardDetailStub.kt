package sfv2.app

import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// shipfloor:qa-stub - exists only so the red gate compiles; the implementer deletes it and writes the real classes.

fun interface CardDetailSource {
    fun fetch(id: String): String
}

enum class CostStatus { KNOWN, PARTIAL, UNKNOWN }

data class SpecView(val status: String, val text: String)

data class TimelineEvent(val id: String, val at: Instant, val actor: String, val type: String, val payload: Map<String, String>)

data class RunRow(
    val runId: String,
    val stage: String,
    val runtime: String,
    val status: CostStatus,
    val costUsd: BigDecimal?,
    val reason: String?,
)

data class CostTotal(val status: CostStatus, val costUsd: BigDecimal?)

data class CardDetail(
    val id: String,
    val title: String,
    val column: String,
    val spec: SpecView,
    val events: List<TimelineEvent>,
    val eventsTruncated: Boolean,
    val runs: List<RunRow>,
    val total: CostTotal,
)

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data class Loaded(val detail: CardDetail) : CardDetailState
    data object NotFound : CardDetailState
    data object Disconnected : CardDetailState
}

fun formatCost(costUsd: BigDecimal?): String = "stub"

class CardDetailModel(private val cardId: String, private val source: CardDetailSource) {
    private val mutableState = MutableStateFlow<CardDetailState>(CardDetailState.Loading)
    val state: StateFlow<CardDetailState> = mutableState

    fun refresh() = Unit

    fun onCardEvent(cardId: String) = Unit
}
