package sfv2.app

// Temporary signatures so the acceptance tests compile; the engineer replaces them with `PaletteModel.kt` and empties this file.

enum class PaletteView(val label: String) {
    BOARD(""),
    FEED(""),
    SETTINGS(""),
}

enum class Direction { UP, DOWN, FIRST, LAST }

sealed interface PaletteEntry {
    data class Card(val card: BoardCard, val waiting: Boolean) : PaletteEntry
    data class View(val view: PaletteView) : PaletteEntry
}

data class PaletteChoice(val state: PaletteState, val entry: PaletteEntry?)

data class PaletteState(val open: Boolean = false, val query: String = "", val active: Int = 0) {
    fun toggle(): PaletteState = this

    fun close(): PaletteState = this

    fun withQuery(query: String): PaletteState = this

    fun move(direction: Direction, size: Int): PaletteState = this

    fun select(entries: List<PaletteEntry>): PaletteChoice = PaletteChoice(this, null)
}

const val PALETTE_ROWS = 9

fun paletteEntries(cards: List<BoardCard>, views: List<PaletteView>, query: String): List<PaletteEntry> = emptyList()
