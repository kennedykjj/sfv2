package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val COMPACT_WIDTH = 860.dp
private const val OWNER_APPROVED = "OWNER_APPROVED"
private val INSTANT_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("pt-BR"))

/** `16 out 14:32`, in the given zone. */
fun formatInstant(at: Instant, zone: ZoneId): String =
    INSTANT_FORMAT.format(at.atZone(zone)).replace(".", "")

/** An approval signed by the owner, as opposed to an event written by an agent or the factory. */
internal fun TimelineEvent.isOwnerApproval(): Boolean = type == OWNER_APPROVED && actor == "owner"

private fun body(color: Color = Tokens.soft) = TextStyle(fontFamily = sans, fontSize = 14.sp, color = color)
private fun tabular(style: TextStyle) = style.copy(fontFeatureSettings = "tnum")

@Composable
fun CardDetailScreen(
    cardId: String,
    state: CardDetailState,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    when (state) {
        CardDetailState.Disconnected -> ConnectionScreen(ConnectionState.Disconnected(DisconnectReason.NOT_RESPONDING))
        CardDetailState.NotFound -> NotFound(cardId, onBack)
        CardDetailState.Loading -> Page { Header(cardId, null, null); Section(Texts.SPEC) { Muted(Texts.LOADING_CARD) } }
        is CardDetailState.Loaded -> Page { Detail(state.detail, zone) }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.ground)) {
        val gap = if (maxWidth < COMPACT_WIDTH) 16.dp else 24.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun NotFound(cardId: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Tokens.ground).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(Texts.cardNotFound(cardId), style = body(Tokens.text).copy(fontSize = 15.sp))
            BasicText(
                Texts.BACK,
                modifier = Modifier
                    .heightIn(min = 24.dp)
                    .border(1.dp, Tokens.edge, RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = body(Tokens.text),
            )
        }
    }
}

@Composable
private fun Header(cardId: String, detail: CardDetail?, total: CostTotal?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("#$cardId", style = TextStyle(fontFamily = mono, fontSize = 13.sp, color = Tokens.text))
            detail?.let {
                BasicText(
                    it.title,
                    modifier = Modifier.semantics { heading() },
                    style = TextStyle(fontFamily = sans, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text),
                )
                BasicText(it.column, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted))
            }
        }
        total?.let {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                BasicText(
                    formatCost(it.costUsd),
                    style = tabular(TextStyle(fontFamily = sans, fontSize = 17.sp, color = Tokens.text)),
                )
                if (it.status != CostStatus.KNOWN) {
                    BasicText(Texts.COST_UNKNOWN_REASON, style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.muted))
                }
            }
        }
    }
}

@Composable
private fun Detail(detail: CardDetail, zone: ZoneId) {
    Header(detail.id, detail, detail.total)
    Section(Texts.SPEC) {
        if (detail.spec.status == "missing" || detail.spec.text.isBlank()) {
            Faint(Texts.NO_SPEC)
        } else {
            SelectionContainer { BasicText(detail.spec.text, modifier = Modifier.widthIn(max = 640.dp), style = body()) }
        }
    }
    Section(Texts.TIMELINE) {
        BasicText(
            "${detail.events.size}",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.faint),
        )
        if (detail.eventsTruncated) Muted(Texts.EVENTS_TRUNCATED)
        if (detail.events.isEmpty()) Faint(Texts.NO_EVENTS) else detail.events.forEach { EventRow(it, zone) }
    }
    Section(Texts.RUNS) {
        if (detail.runs.isEmpty()) Faint(Texts.NO_RUNS)
        detail.runs.forEach { RunLine(it) }
        if (detail.runs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val bold = TextStyle(fontFamily = sans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                BasicText(Texts.TOTAL, style = bold)
                BasicText(formatCost(detail.total.costUsd), style = tabular(bold))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.panel, RoundedCornerShape(16.dp))
            .border(1.dp, Tokens.edge, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            title,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text),
        )
        content()
    }
}

@Composable
private fun EventRow(event: TimelineEvent, zone: ZoneId) {
    val owner = event.isOwnerApproval()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(formatInstant(event.at, zone), style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Tokens.faint))
        // Filled mark for the owner's signed approval, hollow for agents: the state does not rely on color alone.
        val mark = Modifier.size(8.dp)
        Box(if (owner) mark.background(Tokens.text) else mark.border(1.dp, Tokens.muted))
        BasicText(event.actor, style = body(Tokens.text).copy(fontWeight = FontWeight.SemiBold))
        BasicText(event.type, style = body())
        if (owner) {
            BasicText(
                Texts.SIGNED_BY_OWNER,
                modifier = Modifier.border(1.dp, Tokens.edge, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.text),
            )
        }
    }
}

@Composable
private fun RunLine(run: RunRow) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("${run.stage} · ${run.runtime}", style = body())
            BasicText(formatCost(run.costUsd), style = tabular(body(Tokens.text)))
        }
        run.reason?.let { BasicText(it, style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.muted)) }
    }
}

@Composable
private fun Muted(text: String) = BasicText(text, style = body(Tokens.muted))

@Composable
private fun Faint(text: String) = BasicText(text, style = body(Tokens.faint))
