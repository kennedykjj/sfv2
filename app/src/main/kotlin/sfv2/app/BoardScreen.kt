package sfv2.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Clock
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sfv2.core.ColumnTime
import sfv2.core.Column as BoardColumn

private const val ID_LENGTH = 8
private const val LANE_WIDTH = 220
private const val LANE_GAP = 8
private val MAP_BOX_MIN_WIDTH = 12.dp
private val MAP_COUNT_MIN_WIDTH = 24.dp
private val EDGE_FADE_WIDTH = 24.dp
private val STAGE_BAR = 3.dp

/**
 * The conveyor belt: ten columns in product order. [view] is null until the first snapshot; [connected] false dims
 * the last known state and says so. With a [controller] the owner moves cards (drag, or `M` on a focused card);
 * without one the belt is read only. Titles are external text: drawn as plain text, never interpreted.
 */
@Composable
fun BoardScreen(
    view: BoardView?,
    connected: Boolean,
    controller: OwnerMoveController? = null,
    onOpenSettings: (() -> Unit)? = null,
    onReload: (suspend () -> ResyncResult)? = null,
    openCardId: String? = null,
    focusReturnCardId: String? = null,
    onOpenCard: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var reloading by remember { mutableStateOf(false) }
    var reloadFailed by remember { mutableStateOf(false) }
    val laneBounds = remember { mutableStateMapOf<BoardColumn, Rect>() }
    val move by (controller?.state ?: remember { MutableStateFlow(OwnerMoveState()) }).collectAsState()
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().background(Tokens.ground).padding(16.dp).semantics { contentDescription = Texts.BOARD },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) StaleBanner()
        if (connected && (onOpenSettings != null || onReload != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (onReload != null) {
                    ReloadButton(reloading) {
                        if (!reloading) {
                            reloading = true
                            reloadFailed = false
                            scope.launch {
                                try {
                                    reloadFailed = onReload() == ResyncResult.Failed
                                } finally {
                                    reloading = false
                                }
                            }
                        }
                    }
                }
                if (onOpenSettings != null) SettingsButton(onOpenSettings)
            }
        }
        if (reloadFailed) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(Texts.RELOAD_FAILED, style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.warn))
                BasicText(
                    Texts.DISMISS,
                    modifier = Modifier.clickable { reloadFailed = false },
                    style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.soft),
                )
            }
        }
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
        val density = LocalDensity.current.density
        val widths = remember { BoardColumn.entries.associateWith { LANE_WIDTH } }
        val viewportDp = (viewportPx / density).roundToInt()
        Column(modifier = Modifier.fillMaxSize().alpha(if (connected) 1f else 0.55f)) {
            ColumnMap(
                map = boardMap(
                    board ?: Board(emptyList(), null),
                    widths,
                    LANE_GAP,
                    viewportDp,
                    (scrollState.value / density).roundToInt(),
                ),
                enabled = connected,
                onGo = { column ->
                    val target = scrollTarget(column, widths, LANE_GAP, viewportDp)
                    scope.launch { scrollState.scrollTo((target * density).roundToInt()) }
                },
            )
            Box(modifier = Modifier.fillMaxSize().onSizeChanged { viewportPx = it.width }) {
                Row(
                    modifier = Modifier.fillMaxSize().horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(LANE_GAP.dp),
                ) {
                    BoardColumn.entries.forEach { column ->
                        val accepts = held != null && column in move.targets
                        Lane(
                            column = column,
                            cards = board?.cardsIn(column),
                            time = view?.columnTimes?.get(column),
                            controller = controller,
                            accepts = accepts,
                            dimmed = held != null && !accepts,
                            laneBounds = laneBounds,
                            scope = scope,
                            openCardId = openCardId,
                            focusReturnCardId = focusReturnCardId,
                            onOpenCard = onOpenCard,
                        )
                    }
                }
                EdgeFadeOverlays(scrollState)
            }
        }
    }
}

/** The map strip above the board: a box per column, sized like the column, and the outline of the visible window. */
@Composable
private fun ColumnMap(map: BoardMap, enabled: Boolean, onGo: (BoardColumn) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = Texts.COLUMN_MAP },
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            map.boxes.forEach { box ->
                MapBoxItem(box, enabled, Modifier.weight(box.share.coerceAtLeast(Float.MIN_VALUE)).fillMaxHeight()) { onGo(box.column) }
            }
        }
        Box(
            Modifier
                .offset(x = maxWidth * map.viewStart)
                .width(maxWidth * map.viewShare)
                .fillMaxHeight()
                .border(2.dp, Tokens.edgeHi),
        )
    }
}

@Composable
private fun MapBoxItem(box: MapBox, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val waiting = isWaitingForOwner(box.column) && box.count > 0
    var focused by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .widthIn(min = MAP_BOX_MIN_WIDTH)
            .background(Tokens.panel, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused || waiting) Tokens.sand else Tokens.edge, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = Texts.goToColumn(box.column, box.count) },
        contentAlignment = Alignment.Center,
    ) {
        if (maxWidth >= MAP_COUNT_MIN_WIDTH) {
            BasicText(
                box.count.toString(),
                style = TextStyle(
                    fontFamily = sans,
                    fontSize = 13.sp,
                    color = if (waiting) Tokens.sand else Tokens.muted,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

/** Gradient strips over the board edges, only on the side that has more to see; they never take clicks. */
@Composable
private fun BoxScope.EdgeFadeOverlays(scrollState: ScrollState) {
    val fades = edgeFades(scrollState.value, scrollState.maxValue)
    if (fades.start) EdgeFade(Modifier.align(Alignment.CenterStart), Brush.horizontalGradient(listOf(Tokens.ground, Color.Transparent)))
    if (fades.end) EdgeFade(Modifier.align(Alignment.CenterEnd), Brush.horizontalGradient(listOf(Color.Transparent, Tokens.ground)))
}

@Composable
private fun EdgeFade(modifier: Modifier, brush: Brush) {
    Box(modifier.width(EDGE_FADE_WIDTH).fillMaxHeight().background(brush))
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
private fun ReloadButton(reloading: Boolean, onClick: () -> Unit) {
    BasicText(
        if (reloading) Texts.RELOADING else Texts.RELOAD,
        modifier = Modifier
            .alpha(if (reloading) 0.6f else 1f)
            .border(1.dp, Tokens.edge, RoundedCornerShape(8.dp))
            .clickable(enabled = !reloading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
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
            .claimsEscape()
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
                .claimsEscape()
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

/** [cards] is null while the first snapshot has not arrived; [time] is null when the column has no statistic. */
@Composable
private fun Lane(
    column: BoardColumn,
    cards: List<BoardCard>?,
    time: ColumnTime?,
    controller: OwnerMoveController?,
    accepts: Boolean,
    dimmed: Boolean,
    laneBounds: MutableMap<BoardColumn, Rect>,
    scope: CoroutineScope,
    openCardId: String?,
    focusReturnCardId: String?,
    onOpenCard: (String) -> Unit,
) {
    val waiting = isWaitingForOwner(column) && !cards.isNullOrEmpty()
    val accent = if (waiting) Tokens.sand else Tokens.text
    val shape = RoundedCornerShape(12.dp)
    val hue = stageHue(column)
    val count = cards?.size
    val timeLabel = time?.let(ColumnTimeLabel::of)
    Column(
        modifier = Modifier
            .width(LANE_WIDTH.dp)
            .fillMaxHeight()
            .alpha(if (dimmed) 0.45f else 1f)
            .onGloballyPositioned { laneBounds[column] = it.boundsInRoot() }
            .background(Tokens.panel, shape)
            .border(if (accepts) 2.dp else 1.dp, if (accepts) Tokens.edgeHi else if (waiting) Tokens.sand else Tokens.edge, shape)
            .drawBehind {
                if (hue != StageHue.NEUTRAL) drawRect(hueColor(hue), size = Size(size.width, STAGE_BAR.toPx()))
            }
            .padding(12.dp)
            .semantics { contentDescription = laneDescription(column, count ?: 0, waiting, timeLabel) },
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
        timeLabel?.let { Meta(it, Tokens.muted) }
        if (waiting) BasicText(Texts.WAITS_FOR_YOU, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.sand))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                cards == null -> Meta(Texts.LOADING_BOARD, Tokens.faint)
                cards.isEmpty() -> Meta(Texts.EMPTY_COLUMN, Tokens.faint)
                else -> cards.forEach {
                    Card(it, waiting, controller, laneBounds, scope, it.id == openCardId, it.id == focusReturnCardId, onOpenCard)
                }
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
    open: Boolean,
    takesFocus: Boolean,
    onOpenCard: (String) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val shortId = card.id.take(ID_LENGTH)
    val movable = controller != null && ownerTargets(card.column).isNotEmpty()
    var focused by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val face = CardFace.of(card, Clock.systemUTC())
    val description = face.description + (if (movable) ", pode mover" else "") + (if (open) ", ${Texts.openCardHint()}" else "")
    val focus = remember { FocusRequester() }
    LaunchedEffect(takesFocus) { if (takesFocus) focus.requestFocus() }
    val openColor = hueColor(stageHue(card.column))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .background(if (open) Tokens.raise else Tokens.ground, shape)
            .border(
                if (focused || open) 2.dp else 1.dp,
                if (focused) Tokens.edgeHi else if (open) openColor else Tokens.edge,
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = description }
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button) { onOpenCard(card.id) }
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(shortId, style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = hueColor(stageHue(card.column))))
                face.blockedLabel?.let { BasicText(it, style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.bad)) }
            }
            BasicText(
                card.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft),
            )
            val meta = listOfNotNull(face.stage, face.age, face.cost, face.costNote).joinToString(" · ")
            BasicText(
                meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Tokens.muted),
            )
        }
    }
}

/** The color of a stage hue; the accent is the sand of the owner-decision columns, neutral is plain muted text. */
internal fun hueColor(hue: StageHue): Color =
    when (hue) {
        StageHue.REFINE -> Tokens.refine
        StageHue.DEVELOP -> Tokens.develop
        StageHue.VALIDATE -> Tokens.validate
        StageHue.OK -> Tokens.ok
        StageHue.ACCENT -> Tokens.sand
        StageHue.NEUTRAL -> Tokens.muted
    }

private fun laneDescription(column: BoardColumn, count: Int, waiting: Boolean, timeLabel: String?): String {
    val base = "${column.displayName}, ${Texts.cardCount(count)}"
    val withTime = if (timeLabel != null) "$base, $timeLabel" else base
    return if (waiting) "$withTime, ${Texts.WAITS_FOR_YOU.replaceFirstChar { it.lowercase() }}" else withTime
}
