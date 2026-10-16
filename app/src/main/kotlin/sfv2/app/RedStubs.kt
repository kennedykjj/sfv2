package sfv2.app

import sfv2.core.Column

// Temporary red-gate stubs: signatures only, neutral bodies. The engineer replaces them and empties this file.

enum class PanelTab { SUMMARY, SPEC, EVENTS, RUNS }

data class SidePanelState(
    val cardId: String? = null,
    val tab: PanelTab = PanelTab.SUMMARY,
    val width: Int = 480,
    val focusReturnCardId: String? = null,
) {
    fun open(cardId: String): SidePanelState = this

    fun close(): SidePanelState = this

    fun selectTab(tab: PanelTab): SidePanelState = this

    fun resize(width: Int): SidePanelState = this

    fun escape(paletteOpen: Boolean, textFieldFocused: Boolean): SidePanelState = this

    companion object {
        const val MIN_WIDTH = 0
        const val MAX_WIDTH = 0
    }
}

enum class PanelPlacement { SIDE_BY_SIDE, OVERLAY }

fun panelPlacement(windowWidth: Int): PanelPlacement = PanelPlacement.SIDE_BY_SIDE

enum class PathStep { TRAVELED, CURRENT, NEXT }

data class PathMark(val column: Column, val name: String, val step: PathStep)

fun cardPath(column: Column): List<PathMark> = emptyList()

fun cardPathDescription(column: Column): String = ""

sealed interface PanelFooter {
    data object None : PanelFooter

    data class Decision(val approveTo: Column) : PanelFooter
}

fun panelFooter(column: Column): PanelFooter = PanelFooter.None
