package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sfv2.core.Column as BoardColumn

private val sans = FontFamily.SansSerif
private val mono = FontFamily.Monospace
private const val ID_LENGTH = 8

/**
 * The conveyor belt: ten columns in product order. [view] is null until the first snapshot; [connected] false dims
 * the last known state and says so. With a [controller] the owner moves cards (drag, or `M` on a focused card);
 * without one the belt is read only. Titles are external text: drawn as plain text, never interpreted.
 */
@Composable
fun BoardScreen(view: BoardView?, connected: Boolean, controller: OwnerMoveController? = null) {
    val scope = rememberCoroutineScope()
    val laneBounds = remember { mutableStateMapOf<BoardColumn, Rect>() }
    val move by (controller?.state ?: remember { MutableStateFlow(OwnerMoveState()) }).collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().background(Tokens.ground).padding(16.dp).semantics { contentDescription = Texts.BOARD },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) StaleBanner()
        move.status?.let { StatusLine(it) }
        move.notice?.let { notice -> NoticeLine(notice) { controller?.dismissNotice() } }
        val board = view?.board
        if (board != null && board.cards.isEmpty()) {
            BasicText(Texts.EMPTY_BOARD, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft))
        }
        val held = move.held
        if (controller != null && held != null) {
            if (move.notePending) {
                NoteBox(
                    onSend = { note -> scope.launch { controller.submit(note) } },
                    onCancel = controller::cancel,
                )
            } else {
                DestinationChooser(held, move, controller, scope)
            }
        }
        Row(
            modifier = Modifier.fillMaxSize().alpha(if (connected) 1f else 0.55f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoardColumn.entries.forEach { column ->
                val accepts = held != null && column in move.targets
                Lane(
                    column = column,
                    cards = board?.cardsIn(column),
                    controller = controller,
                    accepts = accepts,
                    dimmed = held != null && !accepts,
                    laneBounds = laneBounds,
                    scope = scope,
                )
            }
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

@Composable
private fun StatusLine(text: String) {
    BasicText(
        text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted),
    )
}

@Composable
private fun NoticeLine(notice: MoveNotice, onDismiss: () -> Unit) {
    val color = if (notice.kind == NoticeKind.REFUSED) Tokens.bad else Tokens.warn
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(notice.text, style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = color))
        BasicText(
            Texts.DISMISS,
            modifier = Modifier.clickable(onClick = onDismiss),
            style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.soft),
        )
    }
}

/** The keyboard chooser opened by `M`: arrows pick, Enter sends, Esc cancels. */
@Composable
private fun DestinationChooser(
    held: BoardCard,
    move: OwnerMoveState,
    controller: OwnerMoveController,
    scope: CoroutineScope,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(held.id) {
        if (move.selected == null) move.targets.firstOrNull()?.let(controller::chooseTarget)
        focus.requestFocus()
    }
    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(Tokens.raise, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val index = move.targets.indexOf(move.selected)
                when (event.key) {
                    Key.DirectionRight, Key.DirectionDown -> move.targets.getOrNull((index + 1) % move.targets.size)?.let(controller::chooseTarget)
                    Key.DirectionLeft, Key.DirectionUp -> move.targets.getOrNull((index - 1 + move.targets.size) % move.targets.size)?.let(controller::chooseTarget)
                    Key.Enter -> scope.launch { controller.submit() }
                    Key.Escape -> controller.cancel()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        move.targets.forEach { target ->
            val selected = target == move.selected
            BasicText(
                Texts.moveMeaning(held.column, target) + " → " + target.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(if (selected) 2.dp else 1.dp, if (selected) Tokens.edgeHi else Tokens.edge, RoundedCornerShape(6.dp))
                    .clickable { controller.chooseTarget(target) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
            )
        }
    }
}

/** The mandatory note of a spec sent back; Enter sends, Shift+Enter breaks the line, Esc cancels. */
@Composable
private fun NoteBox(onSend: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val blank = text.isBlank()
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = Modifier.widthIn(max = 320.dp).background(Tokens.raise, RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(Texts.NOTE_LABEL, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.soft))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            cursorBrush = SolidColor(Tokens.text),
            textStyle = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Tokens.edgeHi, RoundedCornerShape(6.dp))
                .padding(6.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Escape -> onCancel()
                        event.key == Key.Enter && !event.isShiftPressed -> if (!blank) onSend(text)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                Texts.SEND_BACK,
                modifier = Modifier
                    .alpha(if (blank) 0.45f else 1f)
                    .semantics { if (blank) contentDescription = Texts.SEND_BACK + " (desabilitado)" }
                    .clickable(enabled = !blank) { onSend(text) },
                style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
            )
            BasicText(
                Texts.CANCEL,
                modifier = Modifier.clickable(onClick = onCancel),
                style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft),
            )
        }
    }
}

/** [cards] is null while the first snapshot has not arrived. */
@Composable
private fun Lane(
    column: BoardColumn,
    cards: List<BoardCard>?,
    controller: OwnerMoveController?,
    accepts: Boolean,
    dimmed: Boolean,
    laneBounds: MutableMap<BoardColumn, Rect>,
    scope: CoroutineScope,
) {
    val waiting = isWaitingForOwner(column) && !cards.isNullOrEmpty()
    val accent = if (waiting) Tokens.sand else Tokens.text
    val shape = RoundedCornerShape(12.dp)
    val count = cards?.size
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .alpha(if (dimmed) 0.45f else 1f)
            .onGloballyPositioned { laneBounds[column] = it.boundsInRoot() }
            .background(Tokens.panel, shape)
            .border(if (accepts) 2.dp else 1.dp, if (accepts) Tokens.edgeHi else if (waiting) Tokens.sand else Tokens.edge, shape)
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
                else -> cards.forEach { Card(it, waiting, controller, laneBounds, scope) }
            }
        }
    }
}

@Composable
private fun Meta(text: String, color: Color) {
    BasicText(text, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = color))
}

@Composable
private fun Card(
    card: BoardCard,
    waiting: Boolean,
    controller: OwnerMoveController?,
    laneBounds: Map<BoardColumn, Rect>,
    scope: CoroutineScope,
) {
    val shape = RoundedCornerShape(8.dp)
    val shortId = card.id.take(ID_LENGTH)
    val movable = controller != null && ownerTargets(card.column).isNotEmpty()
    var focused by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val description = "${card.title}, $shortId" + if (movable) ", pode mover" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .background(Tokens.ground, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Tokens.edgeHi else Tokens.edge, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = description }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (controller != null && event.type == KeyEventType.KeyDown && event.key == Key.M) {
                    controller.hold(card.id)
                    true
                } else {
                    false
                }
            }
            .pointerInput(card.id, card.column, controller) {
                if (controller == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        pointer = origin + start
                        controller.hold(card.id)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        pointer += amount
                    },
                    onDragCancel = controller::cancel,
                    onDragEnd = {
                        val target = laneBounds.entries.firstOrNull { pointer in it.value }?.key
                        if (target != null && target in controller.state.value.targets) {
                            controller.chooseTarget(target)
                            scope.launch { controller.submit() }
                        } else {
                            controller.cancel()
                        }
                    },
                )
            },
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
