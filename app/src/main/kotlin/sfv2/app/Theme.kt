package sfv2.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import sfv2.core.NotificationType
import sfv2.core.Column as BoardColumn

/** Color tokens of DESIGN.md (charcoal theme). */
object Tokens {
    val ground = Color(0xFF0A0A0A)
    val panel = Color(0xFF14171B)
    val raise = Color(0xFF242830)
    val edge = Color(0xFF2A2F39)
    val edgeHi = Color(0xFF3D444F)
    val text = Color(0xFFF2F1EE)
    val soft = Color(0xFFD6D8DA)
    val muted = Color(0xFFA3ABB6)
    val faint = Color(0xFF8F98A3)
    val sand = Color(0xFFC7B9A6)
    val ok = Color(0xFF9FCBAA)
    val warn = Color(0xFFDCB872)
    val bad = Color(0xFFE58F80)
    val refine = Color(0xFF8FA8C7)
    val develop = Color(0xFF7FCDBF)
    val validate = Color(0xFFC896EC)
}

val sans = FontFamily.SansSerif
val mono = FontFamily.Monospace

/** Every user-facing text of the app, in pt-BR. */
object Texts {
    const val TITLE = "sfv2"
    const val CONNECTING = "Conectando ao daemon…"
    const val CONNECTED = "Conectado ao daemon"
    const val DISCONNECTED = "Desconectado do daemon"

    const val BOARD = "Esteira"
    const val COLUMN_MAP = "Mapa das colunas"
    const val WAITS_FOR_YOU = "Aguarda você"
    const val MEDIAN = "mediana"
    const val MEAN_WAIT = "espera média"
    const val AGO = "há"
    const val BLOCKED = "bloqueado"
    const val COST_UNKNOWN = "custo desconhecido"
    const val EMPTY_COLUMN = "Vazia"
    const val EMPTY_BOARD = "Nenhum card na fábrica ainda."
    const val LOADING_BOARD = "Carregando a esteira…"
    const val STALE_BOARD = "Desconectado do daemon. Mostrando o último estado conhecido; pode estar desatualizado."

    fun goToColumn(column: BoardColumn, count: Int): String = "Ir para ${column.displayName}, ${cardCount(count)}"

    const val TAB_SUMMARY = "Resumo"
    const val TAB_SPEC = "Spec"
    const val TAB_EVENTS = "Eventos"
    const val TAB_RUNS = "Runs"
    const val CLOSE_PANEL = "Fechar painel"
    const val RESIZE_PANEL = "Redimensionar painel"
    const val PANEL_DISCONNECTED = "Desconectado do daemon. O quadro ao lado segue utilizável; o painel volta quando o daemon voltar."
    const val PANEL_NOT_FOUND_EXIT = "O daemon não conhece este card. Feche o painel com Esc ou com o botão Fechar."
    const val FOOTER_DECISION = "Decisão"
    const val FOOTER_STAGED_HINT = "Mova o cartão no quadro (tecla M ou arraste) para decidir."

    fun panelTitle(id: String): String = "Card #$id"

    fun approveTo(column: BoardColumn): String = "Aprovar leva a ${column.displayName}"

    fun openCardHint(): String = "aberto no painel"

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

    const val GATES = "Gates"
    const val NO_GATES = "Nenhum gate rodou ainda"
    const val CRITERIA = "Critérios"
    const val NO_DURATION = "—"
    const val GATE_OK = "ok"
    const val GATE_BAD = "bad"
    const val GATE_NOT_RUN = "não rodou"
    const val GATE_MANUAL = "manual"

    const val FEED_TITLE = "Feed do dia"
    const val TODAY = "Hoje"
    const val PREVIOUS_DAY = "Dia anterior"
    const val NEXT_DAY = "Próximo dia"
    const val CURRENT_DAY = "Este é o dia atual"
    const val RECONNECTING = "Reconectando…"

    const val COST_TITLE = "Custo do dia"
    const val COST_BY_CARD = "Por card"
    const val COST_BY_STAGE = "Por estágio"
    const val COST_BY_DAY = "Por dia"
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

    const val NO_OWNER_MOVE = "Este card não tem movimento seu."
    const val NOTHING_SENT = "Nada enviado."
    const val NOTE_LABEL = "Nota para a refinagem"
    const val SEND_BACK = "Devolver"
    const val CANCEL = "Cancelar"
    const val DISMISS = "Entendi"

    const val SPEC_DECISION = "Decisão sobre a spec"
    const val APPROVE = "Aprovar"
    const val REQUEST_CHANGE = "Pedir mudança"
    const val SEND_NOTE = "Enviar"
    const val SENDING = "Enviando…"
    const val APPROVAL_SENT = "Aprovação enviada"
    const val CHANGE_SENT = "Pedido de mudança enviado"
    const val DISMISS_NOTICE = "Dispensar"
    const val NOTICE_REFUSED = "Recusado"
    const val NOTICE_NOT_SENT = "Não enviado"
    const val NOTE_REQUIRED = "A nota é obrigatória para pedir mudança."

    const val CLOSURE_OPEN = "Fechamento no tracker"
    const val CLOSURE_TITLE = "Fechamento"
    const val CLOSURE_LOADING = "Pedindo o rascunho ao daemon…"
    const val CLOSURE_STATUS = "Status"
    const val CLOSURE_SUMMARY = "Resumo"
    const val CLOSURE_PR = "Pull request"
    const val CLOSURE_NOTICE = "Isto é o que será postado no tracker; o título do card não é enviado."
    const val CLOSURE_CONFIRM = "Confirmar fechamento"
    const val CLOSURE_CONFIRMING = "Confirmando…"
    const val CLOSURE_CLOSED = "Fechado no tracker"

    fun closureRefused(reason: String): String = "O daemon não montou o rascunho de fechamento: $reason"

    fun closureFailed(reason: String): String =
        "A escrita no tracker falhou e o card segue em Done. Tente de novo. Resposta do daemon: $reason"

    fun closureNotSent(cause: NotSentCause): String =
        when (cause) {
            NotSentCause.DAEMON_UNAVAILABLE -> "Nada foi enviado: o daemon está indisponível. Confira com sfv2 doctor e tente de novo."
            NotSentCause.NO_OWNER_PROOF -> "Nada foi enviado: o arquivo do token do dono não foi encontrado. Rode sfv2 service install e tente de novo."
            NotSentCause.NO_ANSWER -> "O daemon não respondeu a tempo: confira o card antes de tentar de novo."
        }

    const val OPEN_SETTINGS = "Configurações"
    const val RELOAD = "Recarregar"
    const val RELOADING = "Recarregando…"
    const val RELOAD_FAILED = "Não foi possível ler o quadro do daemon. Confira se o daemon está ativo e tente de novo."
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

    private const val SHORT_ID = 8

    fun newEvents(count: Int): String = if (count == 1) "1 novo evento" else "$count novos eventos"

    fun holding(id: String): String = "Segurando #${id.take(SHORT_ID)}. Setas escolhem o destino, Enter confirma, Esc cancela."

    fun refused(reason: String): String = "Não movido: $reason"

    fun notSent(cause: NotSentCause): String =
        when (cause) {
            NotSentCause.DAEMON_UNAVAILABLE -> "Movimento não enviado: o daemon está indisponível."
            NotSentCause.NO_OWNER_PROOF -> "Movimento não enviado: prova do dono não encontrada."
            NotSentCause.NO_ANSWER -> "Movimento sem resposta do daemon: confira o quadro antes de repetir."
        }

    /** What the move means to the owner, for the destination chooser and the drop button. */
    fun moveMeaning(from: BoardColumn, to: BoardColumn): String =
        when {
            from == BoardColumn.SPEC_REVIEW && to == BoardColumn.READY_FOR_DEV -> "Aprovar a spec"
            to == BoardColumn.READY_FOR_REFINEMENT && from == BoardColumn.SPEC_REVIEW -> "Devolver à refinagem"
            to == BoardColumn.READY_FOR_PROD -> "Liberar a entrega"
            else -> "Enviar à refinagem"
        }

    fun cardNotFound(id: String): String = "Card #$id não encontrado."

    fun cardCount(count: Int): String = if (count == 1) "1 card" else "$count cards"

    fun notificationLabel(type: NotificationType): String =
        when (type) {
            NotificationType.DECISION_WAITING -> "Decisão espera por você"
            NotificationType.RUN_FAILED -> "Run falhou"
            NotificationType.FACTORY_STOPPED -> "Fábrica parou"
        }

    fun changes(count: Int): String = if (count == 1) "1 alteração não salva" else "$count alterações não salvas"

    fun fixFields(count: Int): String = if (count == 1) "Corrija 1 campo para salvar" else "Corrija $count campos para salvar"

    private fun fieldLabel(field: String): String =
        when {
            field == "poolSize" -> GROUP_POOL
            field.startsWith("workHours") -> GROUP_HOURS
            field == "cost.perCardUsd" -> COST_CARD
            field == "cost.perDayUsd" -> COST_DAY
            else -> field
        }

    /** The daemon's refusal of one settings field, in the owner's words. */
    fun issueReason(field: String, reason: String): String {
        val message = when {
            field == "poolSize" -> "use de 1 a 64."
            field == "workHours" -> "o fim deve ser depois do início."
            field == "workHours.days" -> "escolha ao menos um dia."
            field == "workHours.zone" -> "fuso desconhecido."
            field.startsWith("cost.") && reason.contains("required") -> "informe um limite."
            field.startsWith("cost.") -> "deve ser maior que zero."
            else -> reason
        }
        return "${fieldLabel(field)}: $message"
    }

    /** Why the daemon is unreachable, what to do, and the command that does it, when there is one. */
    data class Explanation(val reason: String, val step: String, val command: String? = null)

    fun explain(reason: DisconnectReason): Explanation =
        when (reason) {
            DisconnectReason.SERVICE_NOT_INSTALLED -> Explanation("O serviço sfv2 não está instalado.", "Instale com", "sfv2 service install")
            DisconnectReason.START_FAILED -> Explanation("O serviço sfv2 não iniciou.", "Veja o estado com", "sfv2 doctor")
            DisconnectReason.START_TIMED_OUT -> Explanation("O daemon não respondeu a tempo.", "Veja o estado com", "sfv2 doctor")
            DisconnectReason.NOT_RESPONDING -> Explanation("O daemon parou de responder.", "A janela volta sozinha quando ele voltar.")
            DisconnectReason.NO_RUNTIME_DIR ->
                Explanation("Não encontrei o diretório de runtime do usuário (XDG_RUNTIME_DIR).", "Veja o estado com", "sfv2 doctor")
        }
}
