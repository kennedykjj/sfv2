package sfv2.daemon

import java.math.BigDecimal
import sfv2.core.Column
import sfv2.core.Event
import sfv2.store.StoredCard

// shipfloor:qa-stub - lets the red gate compile; the implementer deletes this file and adds CardReport.kt in main.

enum class CostStatus { KNOWN, PARTIAL, UNKNOWN, NONE }

data class CardReportRow(
    val cardId: String,
    val title: String,
    val stageSeconds: Map<Column, Long>,
    val costUsd: BigDecimal?,
    val costStatus: CostStatus,
)

object CardReport {
    fun build(cards: List<StoredCard>, eventsOf: (String) -> List<Event>): List<CardReportRow> = emptyList()

    fun toCsv(rows: List<CardReportRow>): String = ""
}
