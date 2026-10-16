package sfv2.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Tokens of DESIGN.md (Charcoal) shared by the screens. */
internal object Tokens {
    val ground = Color(0xFF0A0A0A)
    val panel = Color(0xFF14171B)
    val edge = Color(0xFF2A2F39)
    val text = Color(0xFFF2F1EE)
    val soft = Color(0xFFD6D8DA)
    val muted = Color(0xFFA3ABB6)
    val faint = Color(0xFF8F98A3)
    val ok = Color(0xFF9FCBAA)
    val warn = Color(0xFFDCB872)
    val sand = Color(0xFFC7B9A6)
}

internal val sans = FontFamily.SansSerif
internal val mono = FontFamily.Monospace

/** Every user-facing string of the app, in one place for the future i18n. */
internal object Texts {
    const val TITLE = "sfv2"
    const val CONNECTING = "Conectando ao daemon…"
    const val CONNECTED = "Conectado ao daemon"
    const val DISCONNECTED = "Desconectado do daemon"
    const val BOARD = "Esteira"
    const val WAITS_FOR_YOU = "Aguarda você"
    const val EMPTY_COLUMN = "Vazia"
    const val EMPTY_BOARD = "Nenhum card na fábrica ainda."
    const val LOADING_BOARD = "Carregando a esteira…"
    const val STALE_BOARD = "Desconectado do daemon. Mostrando o último estado conhecido; pode estar desatualizado."

    const val SPEC = "Spec"
    const val TIMELINE = "Linha do tempo"
    const val RUNS = "Runs"
    const val TOTAL = "Total"
    const val SIGNED_BY_OWNER = "assinada pelo dono"
    const val LOADING_CARD = "Lendo o card…"
    const val BACK = "Voltar"
    const val NO_SPEC = "Este card ainda não tem spec."
    const val NO_EVENTS = "Nenhum evento registrado."
    const val NO_RUNS = "Nenhum run ainda."
    const val EVENTS_TRUNCATED = "Mostrando só os 500 eventos mais recentes."
    const val COST_UNKNOWN_REASON = "falta preço de um modelo"

    fun cardNotFound(id: String) = "Card #$id não encontrado."

    fun cardCount(count: Int): String = if (count == 1) "1 card" else "$count cards"

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
