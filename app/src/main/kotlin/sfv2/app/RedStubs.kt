package sfv2.app

import sfv2.core.AssistantState

// Red-step stubs: signatures only, neutral bodies. The engineer replaces them with the real code and empties this file.

/** The daemon's `factory.pulse` reading. */
data class Pulse(val state: AssistantState, val phrase: String, val poolCapacity: Int, val active: List<String>)

enum class PulseFreshness { Loading, Live, Stale }

sealed interface PulseCeiling {
    data class Unlimited(val text: String) : PulseCeiling
    data class Meter(val spent: String, val percent: Int, val limitState: LimitState) : PulseCeiling
}

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
    fun markStale(): PulseView = this

    companion object {
        fun loading(): PulseView = PulseView(PulseFreshness.Loading, null, null, null, emptyList(), emptyList(), null, null)

        fun of(pulse: Pulse, cost: CostSummary): PulseView = loading()
    }
}
