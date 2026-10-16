package sfv2.core

// Test-only stub so MoveRulesTest compiles before the implementation exists (red by failure).
// DELETE this file when core/src/main/kotlin/sfv2/core/MoveRules.kt is created: it declares the same names.

enum class MoveAuthority { OWNER_ONLY, FACTORY_OR_OWNER }

sealed interface MoveDecision {
    data object Allowed : MoveDecision

    data class Refused(val reason: String) : MoveDecision
}

object MoveRules {
    val table: Map<Pair<Column, Column>, MoveAuthority> = emptyMap()

    fun check(from: Column, to: Column, actor: Actor): MoveDecision = MoveDecision.Refused("stub")
}
