package sfv2.app

import sfv2.core.Column

enum class BoardDensity(val minWidth: Int, val defaultWidth: Int) {
    CARDS(0, 0),
    CHIPS(0, 0),
}

data class ColumnLayoutState(
    val fit: Boolean = true,
    val density: BoardDensity = BoardDensity.CARDS,
    val restored: Set<Column> = emptySet(),
) {
    fun widthOf(column: Column): Int = 0

    fun resize(column: Column, width: Int): ColumnLayoutState = this

    fun reset(column: Column): ColumnLayoutState = this

    fun equalize(): ColumnLayoutState = this

    fun withFit(fit: Boolean): ColumnLayoutState = this

    fun withDensity(density: BoardDensity): ColumnLayoutState = this

    fun restore(column: Column): ColumnLayoutState = this

    companion object {
        const val MAX_WIDTH = 0
        const val KEY_STEP = 0
        const val COLLAPSED_WIDTH = 0
    }
}

fun columnWidths(state: ColumnLayoutState, counts: Map<Column, Int>, viewportDp: Int, gap: Int): Map<Column, Int> =
    Column.entries.associateWith { 0 }
