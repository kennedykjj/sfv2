package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens of DESIGN.md (Charcoal) used by this screen. */
private object Tokens {
    val ground = Color(0xFF0A0A0A)
    val panel = Color(0xFF14171B)
    val edge = Color(0xFF2A2F39)
    val text = Color(0xFFF2F1EE)
    val soft = Color(0xFFD6D8DA)
    val muted = Color(0xFFA3ABB6)
    val ok = Color(0xFF9FCBAA)
    val warn = Color(0xFFDCB872)
}

/** Every user-facing string of the shell, in one place for the future i18n. */
internal object Texts {
    const val TITLE = "sfv2"
    const val CONNECTING = "Conectando ao daemon…"
    const val CONNECTED = "Conectado ao daemon"
    const val DISCONNECTED = "Desconectado do daemon"

    /** What went wrong and the next step; the command, when there is one, is shown apart in mono. */
    data class Explanation(val reason: String, val step: String, val command: String?)

    fun explain(reason: DisconnectReason): Explanation =
        when (reason) {
            DisconnectReason.SERVICE_NOT_INSTALLED ->
                Explanation("O serviço sfv2 não está instalado.", "Instale com", "sfv2 service install")
            DisconnectReason.START_FAILED ->
                Explanation("O serviço sfv2 não iniciou.", "Veja o estado com", "sfv2 doctor")
            DisconnectReason.START_TIMED_OUT ->
                Explanation("O daemon não respondeu a tempo.", "Veja o estado com", "sfv2 doctor")
            DisconnectReason.NOT_RESPONDING ->
                Explanation("O daemon parou de responder.", "A janela volta sozinha quando ele voltar.", null)
            DisconnectReason.NO_RUNTIME_DIR ->
                Explanation("Não encontrei o diretório de runtime do usuário (XDG_RUNTIME_DIR).", "Veja o estado com", "sfv2 doctor")
        }
}

private val sans = FontFamily.SansSerif
private val mono = FontFamily.Monospace

@Composable
fun ConnectionScreen(state: ConnectionState) {
    Box(
        modifier = Modifier.fillMaxSize().background(Tokens.ground).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .background(Tokens.panel, RoundedCornerShape(16.dp))
                .border(1.dp, Tokens.edge, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                Texts.TITLE,
                style = TextStyle(fontFamily = sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text),
            )
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(8.dp).background(dotColor(state), CircleShape))
                BasicText(headline(state), style = TextStyle(fontFamily = sans, fontSize = 15.sp, color = Tokens.text))
            }
            if (state is ConnectionState.Disconnected) Explanation(Texts.explain(state.reason))
        }
    }
}

@Composable
private fun Explanation(explanation: Texts.Explanation) {
    val small = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.soft)
    BasicText(explanation.reason, style = small)
    BasicText(explanation.step, style = small)
    explanation.command?.let { command ->
        SelectionContainer {
            BasicText(
                command,
                modifier = Modifier.background(Tokens.ground, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Tokens.text),
            )
        }
    }
}

private fun headline(state: ConnectionState): String =
    when (state) {
        ConnectionState.Connecting -> Texts.CONNECTING
        ConnectionState.Connected -> Texts.CONNECTED
        is ConnectionState.Disconnected -> Texts.DISCONNECTED
    }

private fun dotColor(state: ConnectionState): Color =
    when (state) {
        ConnectionState.Connecting -> Tokens.muted
        ConnectionState.Connected -> Tokens.ok
        is ConnectionState.Disconnected -> Tokens.warn
    }
