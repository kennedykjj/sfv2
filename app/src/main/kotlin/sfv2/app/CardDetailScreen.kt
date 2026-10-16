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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.launch

private val COMPACT_WIDTH = 860.dp
private val OWNER_DECISIONS = setOf("OWNER_APPROVED", "OWNER_CHANGE_REQUESTED")
private const val PASS = "PASS"
private const val FAIL = "FAIL"
private const val NO_GATE = "none"
private const val MANUAL = "manual"
private val INSTANT_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("pt-BR"))

/** `16 out 14:32`, in the given zone. */
fun formatInstant(at: Instant, zone: ZoneId): String =
    INSTANT_FORMAT.format(at.atZone(zone)).replace(".", "")

/** `850 ms`, `12 s`, `3 min 20 s`; `—` when the gate ran no process. */
fun formatDuration(durationMs: Long?): String {
    if (durationMs == null) return Texts.NO_DURATION
    if (durationMs < 1_000) return "$durationMs ms"
    val seconds = durationMs / 1_000
    if (seconds < 60) return "$seconds s"
    val rest = seconds % 60
    return if (rest == 0L) "${seconds / 60} min" else "${seconds / 60} min $rest s"
}

/** An approval or change request signed by the owner, as opposed to an event written by an agent or the factory. */
internal fun TimelineEvent.isOwnerDecision(): Boolean = type in OWNER_DECISIONS && actor == "owner"

private fun body(color: Color = Tokens.soft) = TextStyle(fontFamily = sans, fontSize = 14.sp, color = color)
private fun meta(color: Color = Tokens.muted) = TextStyle(fontFamily = sans, fontSize = 13.sp, color = color)
private fun tabular(style: TextStyle) = style.copy(fontFeatureSettings = "tnum")

/** [specReview] is null without a daemon; the owner's actions show only for a card in `SPEC_REVIEW`. */
@Composable
fun CardDetailScreen(
    cardId: String,
    state: CardDetailState,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    specReview: SpecReviewController? = null,
) {
    when (state) {
        CardDetailState.Disconnected -> ConnectionScreen(ConnectionState.Disconnected(DisconnectReason.NOT_RESPONDING))
        CardDetailState.NotFound -> NotFound(cardId, onBack)
        CardDetailState.Loading -> Page { Header(cardId, null, null); Section(Texts.SPEC) { Muted(Texts.LOADING_CARD) } }
        is CardDetailState.Loaded -> {
            LaunchedEffect(state.detail) { specReview?.onDetail(state.detail) }
            Page { Detail(state.detail, zone, specReview.takeIf { specReviewActionsVisible(state) }) }
        }
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
private fun Detail(detail: CardDetail, zone: ZoneId, specReview: SpecReviewController?) {
    Header(detail.id, detail, detail.total)
    if (specReview != null) SpecReviewActions(detail.id, specReview)
    Section(Texts.SPEC) {
        if (detail.spec.status == "missing" || detail.spec.text.isBlank()) {
            Faint(Texts.NO_SPEC)
        } else {
            SelectionContainer { BasicText(detail.spec.text, modifier = Modifier.widthIn(max = 640.dp), style = body()) }
        }
    }
    Section(Texts.GATES) {
        if (detail.gates.isEmpty() && detail.criteria.isEmpty()) Faint(Texts.NO_GATES)
        detail.gates.forEach { GateLine(it) }
        if (detail.criteria.isNotEmpty()) {
            BasicText(Texts.CRITERIA, style = meta(Tokens.soft).copy(fontWeight = FontWeight.SemiBold))
            detail.criteria.forEach { CriterionLine(it) }
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

/** The owner's decision on a spec in `SPEC_REVIEW`; the column only changes when the daemon event arrives. */
@Composable
private fun SpecReviewActions(cardId: String, controller: SpecReviewController) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var askedForChange by remember { mutableStateOf(false) }
    val busy = state.sending || state.sent
    val status = when {
        state.sending -> Texts.SENDING
        state.sent -> if (askedForChange) Texts.CHANGE_SENT else Texts.APPROVAL_SENT
        else -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.panel, RoundedCornerShape(16.dp))
            .border(1.dp, Tokens.edge, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(Texts.APPROVE, primary = true, enabled = !busy) {
                askedForChange = false
                scope.launch { controller.approve(cardId) }
            }
            ActionButton(Texts.REQUEST_CHANGE, primary = false, enabled = !busy && !state.notePending) {
                askedForChange = true
                scope.launch { controller.requestChange(cardId, null) }
            }
        }
        Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (status.isNotEmpty()) BasicText(status, style = body(Tokens.text))
        }
        if (state.notePending && !state.sent) {
            ChangeNote(
                enabled = !state.sending,
                onSend = { note -> scope.launch { controller.requestChange(cardId, note) } },
                onCancel = controller::cancel,
            )
        }
        state.notice?.let { NoticeLine(it, controller::dismissNotice) }
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicText(
        label,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 32.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (primary) Tokens.text else Color.Transparent, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Tokens.sand else if (primary) Tokens.text else Tokens.edgeHi, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = TextStyle(
            fontFamily = sans,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Tokens.ground else Tokens.text,
        ),
    )
}

/** The mandatory note of a change request; Enter sends, Shift+Enter breaks the line, Esc cancels. */
@Composable
private fun ChangeNote(enabled: Boolean, onSend: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(Texts.NOTE_LABEL, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.soft))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            enabled = enabled,
            cursorBrush = SolidColor(Tokens.text),
            textStyle = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .heightIn(min = 64.dp)
                .border(1.dp, Tokens.edgeHi, RoundedCornerShape(6.dp))
                .padding(8.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Escape -> onCancel()
                        event.key == Key.Enter && !event.isShiftPressed -> onSend(text)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        )
        if (text.isBlank()) BasicText(Texts.NOTE_REQUIRED, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(Texts.SEND_NOTE, primary = true, enabled = enabled) { onSend(text) }
            ActionButton(Texts.CANCEL, primary = false, enabled = enabled, onClick = onCancel)
        }
    }
}

/** A refusal or a "not sent": a textual label besides the color, and a button to dismiss it. */
@Composable
private fun NoticeLine(notice: MoveNotice, onDismiss: () -> Unit) {
    val label = if (notice.kind == NoticeKind.REFUSED) Texts.NOTICE_REFUSED else Texts.NOTICE_NOT_SENT
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .border(1.dp, Tokens.bad, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(label, style = body(Tokens.bad).copy(fontWeight = FontWeight.SemiBold))
        BasicText(notice.text, style = body(Tokens.text))
        Row { ActionButton(Texts.DISMISS_NOTICE, primary = false, enabled = true, onClick = onDismiss) }
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
private fun GateLine(gate: GateRow) {
    val passed = gate.verdict == PASS
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            if (passed) Texts.GATE_OK else Texts.GATE_BAD,
            style = meta(if (passed) Tokens.ok else Tokens.bad).copy(fontWeight = FontWeight.SemiBold),
        )
        BasicText(gate.name, style = TextStyle(fontFamily = mono, fontSize = 13.sp, color = Tokens.text))
        BasicText(formatDuration(gate.durationMs), style = tabular(meta()))
        gate.reason?.let { BasicText(it, style = meta()) }
    }
}

@Composable
private fun CriterionLine(criterion: CriterionRow) {
    val (label, color) = when (criterion.gate) {
        PASS -> Texts.GATE_OK to Tokens.ok
        FAIL -> Texts.GATE_BAD to Tokens.bad
        MANUAL -> Texts.GATE_MANUAL to Tokens.muted
        NO_GATE -> Texts.GATE_NOT_RUN to Tokens.faint
        else -> criterion.gate to Tokens.muted
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(criterion.ac, style = meta(Tokens.text).copy(fontWeight = FontWeight.SemiBold))
        BasicText(criterion.testFile, style = TextStyle(fontFamily = mono, fontSize = 13.sp, color = Tokens.soft))
        BasicText(label, style = meta(color))
    }
}

@Composable
private fun EventRow(event: TimelineEvent, zone: ZoneId) {
    val owner = event.isOwnerDecision()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(formatInstant(event.at, zone), style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Tokens.faint))
        // Filled mark for the owner's signed decision, hollow for agents: the state does not rely on color alone.
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
