package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sfv2.core.Column as BoardColumn

private val sans = FontFamily.SansSerif
private val mono = FontFamily.Monospace
private const val ID_LENGTH = 8

/**
 * The conveyor belt: ten columns in product order, read only. [view] is null until the first snapshot; [connected]
 * false dims the last known state and says so. Titles are external text: drawn as plain text, never interpreted.
 */
@Composable
fun BoardScreen(view: BoardView?, connected: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().background(Tokens.ground).padding(16.dp).semantics { contentDescription = Texts.BOARD },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) StaleBanner()
        val board = view?.board
        if (board != null && board.cards.isEmpty()) {
            BasicText(Texts.EMPTY_BOARD, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft))
        }
        Row(
            modifier = Modifier.fillMaxSize().alpha(if (connected) 1f else 0.55f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoardColumn.entries.forEach { column -> Lane(column, board?.cardsIn(column)) }
        }
    }
}

@Composable
private fun StaleBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Tokens.warn, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(Tokens.warn, CircleShape))
        BasicText(Texts.STALE_BOARD, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text))
    }
}

/** [cards] is null while the first snapshot has not arrived. */
@Composable
private fun Lane(column: BoardColumn, cards: List<BoardCard>?) {
    val waiting = isWaitingForOwner(column) && !cards.isNullOrEmpty()
    val accent = if (waiting) Tokens.sand else Tokens.text
    val shape = RoundedCornerShape(12.dp)
    val count = cards?.size
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Tokens.panel, shape)
            .border(1.dp, if (waiting) Tokens.sand else Tokens.edge, shape)
            .padding(12.dp)
            .semantics { contentDescription = laneDescription(column, count ?: 0, waiting) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            BasicText(
                column.displayName,
                style = TextStyle(fontFamily = sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = accent),
            )
            BasicText(
                count?.toString() ?: "–",
                style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted, fontFeatureSettings = "tnum"),
            )
        }
        if (waiting) BasicText(Texts.WAITS_FOR_YOU, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.sand))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                cards == null -> Meta(Texts.LOADING_BOARD, Tokens.faint)
                cards.isEmpty() -> Meta(Texts.EMPTY_COLUMN, Tokens.faint)
                else -> cards.forEach { Card(it, waiting) }
            }
        }
    }
}

@Composable
private fun Meta(text: String, color: Color) {
    BasicText(text, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = color))
}

@Composable
private fun Card(card: BoardCard, waiting: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val shortId = card.id.take(ID_LENGTH)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.ground, shape)
            .border(1.dp, Tokens.edge, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = "${card.title}, $shortId" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(shortId, style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = if (waiting) Tokens.sand else Tokens.muted))
        BasicText(
            card.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft),
        )
    }
}

private fun laneDescription(column: BoardColumn, count: Int, waiting: Boolean): String {
    val base = "${column.displayName}, ${Texts.cardCount(count)}"
    return if (waiting) "$base, ${Texts.WAITS_FOR_YOU.replaceFirstChar { it.lowercase() }}" else base
}
