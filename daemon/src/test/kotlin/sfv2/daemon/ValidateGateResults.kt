package sfv2.daemon

// shipfloor:qa-stub - lets the red gate compile; the implementer deletes this file and adds the real type in main.
const val DEFAULT_VALIDATE_MAX_ROUNDS = 3

class ValidateGateResults(val results: List<GateResult>) : List<GateResult> by results {
    val state: GatesState get() = GatesState.GREEN
    val failedNames: List<String> get() = emptyList()

    companion object {
        fun fromExecutor(name: String, verdict: GateVerdict) = ValidateGateResults(emptyList())

        fun fromScope(verdict: ScopeVerdict) = ValidateGateResults(emptyList())

        fun fromSpec(check: SpecCheck) = ValidateGateResults(emptyList())
    }
}
