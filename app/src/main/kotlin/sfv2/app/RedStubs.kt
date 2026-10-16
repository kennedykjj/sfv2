package sfv2.app

import sfv2.core.Column

// Temporary red-gate stubs: signatures only, neutral bodies. The engineer replaces them in BoardMap.kt.

data class MapBox(val column: Column, val count: Int, val start: Float, val share: Float)

data class BoardMap(val boxes: List<MapBox>, val viewStart: Float, val viewShare: Float)

data class EdgeFades(val start: Boolean, val end: Boolean)

fun boardMap(board: Board, widths: Map<Column, Int>, gap: Int, viewportWidth: Int, scrollOffset: Int): BoardMap =
    BoardMap(emptyList(), 0f, 0f)

fun scrollTarget(column: Column, widths: Map<Column, Int>, gap: Int, viewportWidth: Int): Int = 0

fun edgeFades(scrollOffset: Int, maxScroll: Int): EdgeFades = EdgeFades(start = false, end = false)
