package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val sans = FontFamily.SansSerif
private val mono = FontFamily.Monospace
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private const val NARROW_DP = 560

@Composable
fun FeedScreen(state: FeedState, today: LocalDate, onSelectDay: (LocalDate) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.ground)) {
        val compact = maxWidth < NARROW_DP.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(state, today, onSelectDay)
            CostPanel(state.cost, compact)
            Body(state)
        }
    }
}

@Composable
private fun Header(state: FeedState, today: LocalDate, onSelectDay: (LocalDate) -> Unit) {
    val isToday = state.day >= today
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(Texts.FEED_TITLE, style = TextStyle(fontFamily = sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text))
        if (state.connection == FeedConnection.Reconnecting) {
            BasicText(
                Texts.RECONNECTING,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.warn),
            )
        }
        Box(Modifier.weight(1f))
        FocusableText("‹", Texts.PREVIOUS_DAY, enabled = true) { onSelectDay(state.day.minusDays(1)) }
        BasicText(
            if (isToday) Texts.TODAY else DAY_FORMAT.format(state.day),
            style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
        )
        FocusableText("›", if (isToday) Texts.CURRENT_DAY else Texts.NEXT_DAY, enabled = !isToday) { onSelectDay(state.day.plusDays(1)) }
        if (!isToday) FocusableText(Texts.TODAY, Texts.TODAY, enabled = true) { onSelectDay(today) }
    }
}

/** A text button of at least 32dp with the `sand` focus ring (the only place the accent is used). */
@Composable
private fun FocusableText(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    BasicText(
        text,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .widthIn(min = 32.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Tokens.sand else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = if (enabled) Tokens.soft else Tokens.faint),
    )
}

@Composable
private fun Body(state: FeedState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.earlierHistoryUnavailable) {
            BasicText(Texts.EARLIER_HISTORY_UNAVAILABLE, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted))
        }
        when {
            state.loading -> Note(Texts.LOADING)
            state.failed -> Note(Texts.FEED_ERROR)
            state.rows.isEmpty() -> Note(Texts.EMPTY_DAY)
            else -> EventList(state.rows)
        }
    }
}

@Composable
private fun Note(text: String) {
    BasicText(text, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft))
}

@Composable
private fun EventList(rows: List<FeedRow>) {
    val listState = rememberLazyListState()
    val atEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= rows.lastIndex } ?: true } }
    var seen by remember { mutableStateOf(rows.size) }
    if (atEnd) seen = rows.size
    LaunchedEffect(rows.size, atEnd) {
        if (atEnd && rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }
    val unseen = rows.size - seen
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.seq }) { EventRow(it) }
        }
        if (unseen > 0) {
            Box(Modifier.align(Alignment.BottomCenter).background(Tokens.panel, RoundedCornerShape(8.dp))) {
                FocusableText(Texts.newEvents(unseen), Texts.newEvents(unseen), enabled = true) { seen = rows.size }
            }
        }
    }
    LaunchedEffect(seen) {
        if (seen == rows.size && rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }
}

@Composable
private fun EventRow(row: FeedRow) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(row.time, style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Tokens.muted))
        Mark(row.outcome)
        BasicText(row.type, style = TextStyle(fontFamily = sans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text))
        BasicText(row.card, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft))
        BasicText(row.actor, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted))
    }
}

/** Dot for ok, diamond for failure, dash for a row without outcome: shape and color together. */
@Composable
private fun Mark(outcome: FeedOutcome) {
    when (outcome) {
        FeedOutcome.Ok -> Box(Modifier.size(8.dp).background(Tokens.ok, CircleShape))
        FeedOutcome.Failed ->
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer(rotationZ = 45f)
                    .background(Tokens.bad)
                    .semantics { contentDescription = Texts.FAILED },
            )
        FeedOutcome.None -> Box(Modifier.size(width = 8.dp, height = 2.dp).background(Tokens.faint))
    }
}
