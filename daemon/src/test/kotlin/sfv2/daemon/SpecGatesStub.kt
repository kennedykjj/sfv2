package sfv2.daemon

// shipfloor:qa-stub — only so the red gate compiles; the implementer deletes this file
// and adds the real daemon/src/main/kotlin/sfv2/daemon/SpecGates.kt.

data class SpecLayer(val name: String, val path: String, val tests: List<String>)

enum class SpecWeight { LIGHT, STANDARD, HEAVY }

enum class SpecGate { TEST_PLAN, WEIGHT }

data class SpecFinding(val gate: SpecGate, val ac: String?, val message: String)

data class SpecCheck(val weight: SpecWeight, val findings: List<SpecFinding>)

object SpecGates {
    fun check(specText: String, layers: List<SpecLayer>): SpecCheck =
        SpecCheck(SpecWeight.STANDARD, emptyList())
}
