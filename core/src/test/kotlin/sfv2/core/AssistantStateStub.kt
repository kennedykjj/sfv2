package sfv2.core

// shipfloor:qa-stub - lets the acceptance tests compile; the implementer deletes this file
// and adds the real core/src/main/kotlin/sfv2/core/AssistantState.kt.

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class AssistantState { IDLE, WORKING, WAITING_OWNER, BLOCKED, NIGHT, TIRED }

data class AssistantStateRules(
    val nightStart: LocalTime = LocalTime.of(22, 0),
    val nightEnd: LocalTime = LocalTime.of(7, 0),
    val zone: ZoneId,
    val tiredFailures: Int = 3,
    val tiredWindow: Duration = Duration.ofMinutes(60),
)

data class AssistantCard(val column: Column, val events: List<Event>)

object AssistantStates {
    fun derive(cards: List<AssistantCard>, now: Instant, rules: AssistantStateRules): AssistantState =
        AssistantState.IDLE
}
