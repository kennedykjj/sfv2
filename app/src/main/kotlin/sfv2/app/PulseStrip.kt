package sfv2.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TWO_BY_TWO_BELOW = 1100.dp
private val ONE_COLUMN_BELOW = 880.dp
private const val CURSOR_PERIOD_MILLIS = 1060

/** True when the owner asked the desktop for less motion; the phrase cursor then stays still. */
private val reducedMotion: Boolean = System.getenv("SFV2_REDUCED_MOTION")?.isNotBlank() == true

/**
 * The pulse strip: what the factory says, how many sessions are in use and the day ceiling. Everything comes from
 * [view] and is drawn as plain text; card labels are `#n` or 8 hex, never titles.
 */
@Composable
fun PulseStrip(view: PulseView, modifier: Modifier = Modifier) {
    val dim = if (view.freshness == PulseFreshness.Stale) 0.55f else 1f
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        view.notice?.let { notice ->
            BasicText(
                notice,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.warn),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().alpha(dim)) {
            val cells: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> SaysCell(view, m) },
                { m -> NowCell(view, m) },
                { m -> CeilingCell(view, m) },
            )
            when {
                maxWidth >= TWO_BY_TWO_BELOW -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.forEach { it(Modifier.weight(1f)) }
                }
                maxWidth >= ONE_COLUMN_BELOW -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { it(Modifier.weight(1f)) }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.forEach { it(Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun Cell(title: String, description: String, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .background(Tokens.panel, shape)
            .border(1.dp, Tokens.edge, shape)
            .padding(12.dp)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(title, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.muted))
        content()
    }
}

@Composable
private fun SaysCell(view: PulseView, modifier: Modifier) {
    val description = listOfNotNull(PulseTexts.TITLE, view.stateText, view.phrase).joinToString(", ")
    Cell(PulseTexts.TITLE, description, modifier) {
        if (view.stateText == null) {
            BasicText(PulseTexts.LOADING, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.faint))
        } else {
            BasicText(view.stateText, style = TextStyle(fontFamily = mono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    view.phrase.orEmpty(),
                    modifier = Modifier.weight(1f, fill = false),
                    style = TextStyle(fontFamily = mono, fontSize = 13.sp, color = Tokens.soft),
                )
                Cursor()
            }
        }
    }
}

@Composable
private fun Cursor() {
    val level = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse-cursor")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = CURSOR_PERIOD_MILLIS
                    1f at 0
                    0f at CURSOR_PERIOD_MILLIS / 2
                },
                RepeatMode.Restart,
            ),
            label = "pulse-cursor-level",
        )
        animated
    }
    Box(Modifier.size(width = 7.dp, height = 14.dp).alpha(level).background(Tokens.text))
}

@Composable
private fun NowCell(view: PulseView, modifier: Modifier) {
    val sessions = view.sessionsText
    val description = listOfNotNull(PulseTexts.NOW, sessions, view.activeCards.takeIf { it.isNotEmpty() }?.joinToString(" ")).joinToString(", ")
    Cell(PulseTexts.NOW, description, modifier) {
        if (sessions == null) {
            BasicText(PulseTexts.LOADING, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.faint))
        } else {
            BasicText(sessions, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text, fontFeatureSettings = "tnum"))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                view.squares.forEach { used ->
                    Box(Modifier.size(12.dp).border(1.dp, Tokens.edgeHi).background(if (used) Tokens.text else Color.Transparent))
                }
            }
            if (view.activeCards.isEmpty()) {
                BasicText(PulseTexts.NO_CARDS, style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Tokens.faint))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    view.activeCards.forEach { label ->
                        BasicText(
                            label,
                            modifier = Modifier.border(1.dp, Tokens.edge, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                            style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Tokens.soft),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CeilingCell(view: PulseView, modifier: Modifier) {
    val ceiling = view.ceiling
    val description = when (ceiling) {
        null -> PulseTexts.CEILING_TITLE
        is PulseCeiling.Unlimited -> "${PulseTexts.CEILING_TITLE}, ${ceiling.text}"
        is PulseCeiling.Meter -> "${PulseTexts.CEILING_TITLE}, ${PulseTexts.meter(ceiling.percent)}, ${ceiling.spent}"
    }
    Cell(PulseTexts.CEILING_TITLE, description, modifier) {
        when (ceiling) {
            null -> BasicText(PulseTexts.LOADING, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.faint))
            is PulseCeiling.Unlimited -> BasicText(ceiling.text, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.muted))
            is PulseCeiling.Meter -> {
                val color = when (ceiling.limitState) {
                    LimitState.Reached -> Tokens.bad
                    LimitState.Within -> if (ceiling.percent >= WARN_PERCENT) Tokens.warn else Tokens.ok
                    LimitState.NoLimit -> Tokens.muted
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(ceiling.spent, style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text, fontFeatureSettings = "tnum"))
                    BasicText("${ceiling.percent}%", style = TextStyle(fontFamily = sans, fontSize = 13.sp, color = color, fontFeatureSettings = "tnum"))
                }
                Meter(ceiling.percent / 100f, color, PulseTexts.meter(ceiling.percent))
            }
        }
    }
}

private const val WARN_PERCENT = 80

@Composable
private fun Meter(fraction: Float, fill: Color, description: String) {
    val shown = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Tokens.edge)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .height(6.dp)
                .layout { measurable, constraints ->
                    val width = (constraints.maxWidth * shown).toInt()
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .background(fill),
        )
    }
}
