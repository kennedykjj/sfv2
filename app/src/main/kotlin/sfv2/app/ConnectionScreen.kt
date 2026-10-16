package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sfv2.core.NotificationType

/** Every user-facing string of the shell, in one place for the future i18n. */
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

    fun cardCount(count: Int): String = if (count == 1) "1 card" else "$count cards"

    const val OPEN_SETTINGS = "Configurações"
    const val BACK = "Voltar"
    const val SETTINGS_TITLE = "Configurações"
    const val SETTINGS_LOADING = "Lendo as configurações…"
    const val SETTINGS_SAVING = "Salvando…"
    const val SETTINGS_SAVED = "Salvo. Vale a partir do próximo ciclo."
    const val UNREACHABLE = "Não consegui falar com o daemon. Seu rascunho continua aqui."
    const val UNREACHABLE_LOAD = "Não consegui falar com o daemon."
    const val REFUSED = "O daemon recusou o salvamento. Nada foi alterado."
    const val RETRY = "Tentar de novo"
    const val DISCARD = "Descartar"
    const val SAVE = "Salvar"
    const val ON = "Ligado"
    const val OFF = "Desligado"
    const val GROUP_POOL = "Pool de sessões"
    const val GROUP_HOURS = "Horário de trabalho"
    const val GROUP_COST = "Limites de custo"
    const val GROUP_NOTIFICATIONS = "Notificações"
    const val POOL_LABEL = "Sessões simultâneas"
    const val POOL_DESCRIPTION = "Total de sessões que a fábrica roda ao mesmo tempo."
    const val HOURS_LABEL = "Restringir ao horário"
    const val HOURS_DESCRIPTION = "Fora da janela, a fábrica não despacha trabalho novo."
    const val HOURS_START = "Início"
    const val HOURS_END = "Fim"
    const val COST_CARD = "Limite por card"
    const val COST_DAY = "Limite por dia"
    const val COST_DESCRIPTION = "Vazio: sem limite"
    const val COST_PREFIX = "US$"
    const val COST_SUFFIX = "est."
    const val NOTIFICATIONS_NOTE = "Só guarda as preferências. As notificações ainda não são entregues."
    const val QUIET_LABEL = "Horário silencioso"
    const val QUIET_DESCRIPTION = "Sem avisos entre o início e o fim."
    const val BAD_TIME = "use HH:MM"
    const val BAD_AMOUNT = "use um número"

    val DAY_NAMES = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    fun notificationLabel(type: NotificationType): String =
        when (type) {
            NotificationType.DECISION_WAITING -> "Decisão espera por você"
            NotificationType.RUN_FAILED -> "Run falhou"
            NotificationType.FACTORY_STOPPED -> "Fábrica parou"
        }

    fun changes(count: Int): String = if (count == 1) "1 alteração não salva" else "$count alterações não salvas"

    fun fixFields(count: Int): String = if (count == 1) "Corrija 1 campo para salvar" else "Corrija $count campos para salvar"

    fun fieldLabel(field: String): String =
        when {
            field == "poolSize" -> GROUP_POOL
            field.startsWith("workHours") -> GROUP_HOURS
            field == "cost.perCardUsd" -> COST_CARD
            field == "cost.perDayUsd" -> COST_DAY
            else -> field
        }

    fun issueReason(field: String, reason: String): String =
        when {
            field == "poolSize" -> "use de 1 a 64."
            field == "workHours" -> "o fim deve ser depois do início."
            field == "workHours.days" -> "escolha ao menos um dia."
            field == "workHours.zone" -> "fuso desconhecido."
            "required" in reason -> "informe um limite."
            else -> "deve ser maior que zero."
        }

    const val FEED_TITLE = "Feed do dia"
    const val TODAY = "Hoje"
    const val PREVIOUS_DAY = "Dia anterior"
    const val NEXT_DAY = "Próximo dia"
    const val CURRENT_DAY = "Este é o dia atual"
    const val RECONNECTING = "Reconectando…"
    const val COST_TITLE = "Custo do dia"
    const val COST_BY_CARD = "Por card"
    const val COST_TOTAL = "Total do dia"
    const val ESTIMATE_TAG = "estimativa"
    const val ESTIMATE_SUFFIX = "est."
    const val PARTIAL = "parcial"
    const val UNKNOWN = "desconhecido"
    const val LIMIT_REACHED = "limite atingido"
    const val LIMIT_EXCEEDED = "limite ultrapassado"
    const val LIMIT_OF = "de"
    const val LOADING = "Lendo o feed…"
    const val EMPTY_DAY = "Nenhum evento neste dia."
    const val FEED_ERROR = "Não consegui ler o feed. Tentando de novo."
    const val PARTIAL_EXPLANATION = "Sem preço ou transcrição completa; o valor real pode ser maior."
    const val EARLIER_HISTORY_UNAVAILABLE = "Histórico anterior indisponível"
    const val FAILED = "falhou"

    fun newEvents(count: Int): String = if (count == 1) "1 novo evento" else "$count novos eventos"

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

internal val sans = FontFamily.SansSerif
internal val mono = FontFamily.Monospace

@Composable
fun ConnectionScreen(state: ConnectionState, onOpenSettings: (() -> Unit)? = null) {
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
            if (state == ConnectionState.Connected && onOpenSettings != null) {
                SettingsButton(onOpenSettings)
            }
        }
    }
}

@Composable
internal fun SettingsButton(onClick: () -> Unit) {
    BasicText(
        Texts.OPEN_SETTINGS,
        modifier = Modifier
            .border(1.dp, Tokens.edge, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Tokens.text),
    )
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
