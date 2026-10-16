package sfv2.app

import java.math.BigDecimal
import java.math.RoundingMode
import sfv2.core.AssistantState

/** The daemon's `factory.pulse` reading. [active] holds card labels (`#n` or 8 hex), never titles. */
data class Pulse(val state: AssistantState, val phrase: String, val poolCapacity: Int, val active: List<String>)

enum class PulseFreshness { Loading, Live, Stale }

sealed interface PulseCeiling {
    data class Unlimited(val text: String) : PulseCeiling

    /** [percent] is spent over the day limit, rounded; it may pass 100. */
    data class Meter(val spent: String, val percent: Int, val limitState: LimitState) : PulseCeiling
}

internal object PulseTexts {
    const val TITLE = "A fábrica diz"
    const val NOW = "Agora"
    const val CEILING_TITLE = "Teto do dia"
    const val LOADING = "Lendo a fábrica…"
    const val STALE = "Leitura desatualizada: o daemon não respondeu."
    const val NO_CEILING = "sem teto"
    const val NO_CARDS = "nenhum card em execução"

    fun state(state: AssistantState): String =
        when (state) {
            AssistantState.IDLE -> "▸ ocioso"
            AssistantState.WORKING -> "▸ trabalhando"
            AssistantState.WAITING_OWNER -> "▸ esperando você"
            AssistantState.BLOCKED -> "▸ bloqueado"
            AssistantState.NIGHT -> "▸ descansando"
            AssistantState.TIRED -> "▸ cansado"
        }

    fun sessions(used: Int, total: Int): String = "$used de $total sessões"

    fun meter(percent: Int): String = "$percent% do teto do dia"
}

/**
 * What the strip shows. Words, squares (`true` = in use, one per pool slot) and the meter come straight from the
 * daemon's numbers; nothing is invented before a reading arrives.
 */
data class PulseView(
    val freshness: PulseFreshness,
    val stateText: String?,
    val phrase: String?,
    val sessionsText: String?,
    val squares: List<Boolean>,
    val activeCards: List<String>,
    val ceiling: PulseCeiling?,
    val notice: String?,
) {
    /** Keeps the last reading and says it is out of date. */
    fun markStale(): PulseView = copy(freshness = PulseFreshness.Stale, notice = PulseTexts.STALE)

    companion object {
        fun loading(): PulseView = PulseView(PulseFreshness.Loading, null, null, null, emptyList(), emptyList(), null, null)

        fun of(pulse: Pulse, cost: CostSummary): PulseView {
            val capacity = pulse.poolCapacity.coerceAtLeast(0)
            val used = pulse.active.size.coerceAtMost(capacity)
            return PulseView(
                freshness = PulseFreshness.Live,
                stateText = PulseTexts.state(pulse.state),
                phrase = pulse.phrase,
                sessionsText = PulseTexts.sessions(pulse.active.size, capacity),
                squares = List(capacity) { it < used },
                activeCards = pulse.active,
                ceiling = ceilingOf(cost),
                notice = null,
            )
        }

        private fun ceilingOf(cost: CostSummary): PulseCeiling {
            val limit = cost.limits.perDayUsd?.takeIf { it.signum() > 0 } ?: return PulseCeiling.Unlimited(PulseTexts.NO_CEILING)
            val spent = cost.totalUsd
            val percent = spent?.multiply(HUNDRED)?.divide(limit, 0, RoundingMode.HALF_UP)?.toInt() ?: 0
            val text = spent?.let { "${Texts.ESTIMATE_SUFFIX} ${CostSummaryView.money(it)}" } ?: "${Texts.ESTIMATE_SUFFIX} US$ ?"
            return PulseCeiling.Meter(text, percent, if (cost.dayReached) LimitState.Reached else LimitState.Within)
        }

        private val HUNDRED = BigDecimal(100)
    }
}
