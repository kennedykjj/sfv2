package sfv2.daemon

import java.time.Duration
import sfv2.core.AssistantState

// shipfloor:qa-stub - exists only so AssistantPhrasesTest compiles for the red gate.
// The implementer deletes this file and adds the real declarations in daemon/src/main.

data class AssistantFacts(
    val waitingOwner: List<String>,
    val blocked: List<String>,
    val working: List<String>,
    val recentFailures: Int,
)

object AssistantPhrases {
    fun assemble(state: AssistantState, facts: AssistantFacts): String = ""
}

fun interface AssistantVoice {
    fun rewrite(phrase: String): String
}

enum class VoiceMode { OFF, ON }

class AssistantSpeaker(
    private val voice: AssistantVoice?,
    private val voiceEnabled: VoiceMode,
    private val deadline: Duration = Duration.ofSeconds(3),
) {
    fun say(state: AssistantState, facts: AssistantFacts): String = ""
}
