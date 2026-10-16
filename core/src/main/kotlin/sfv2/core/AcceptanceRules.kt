package sfv2.core

/** Extra per-layer patterns; they add to the default patterns. */
data class AcceptancePatterns(
    val ran: Regex,
    val assertions: Regex? = null,
    val setupErrors: Regex? = null,
) {
    companion object {
        fun of(ran: String, assertions: String? = null, setupErrors: String? = null): AcceptancePatterns =
            AcceptancePatterns(
                ran = Regex(ran),
                assertions = assertions?.let { Regex(it) },
                setupErrors = setupErrors?.let { Regex(it) },
            )
    }
}

sealed interface AcceptanceVerdict {
    data object Ok : AcceptanceVerdict

    data class Rejected(val reason: AcceptanceReason) : AcceptanceVerdict
}

enum class AcceptanceReason {
    TESTS_PASSED_BEFORE_IMPL,
    SETUP_ERROR,
    NO_ASSERTION_FAILURE,
    TESTS_FAILED,
    NO_TEST_RAN,
    ASSERTION_EDITED,
}

object AcceptanceRules {
    fun red(exitCode: Int?, output: Sequence<String>, patterns: AcceptancePatterns): AcceptanceVerdict =
        AcceptanceVerdict.Ok

    fun green(exitCode: Int?, output: Sequence<String>, patterns: AcceptancePatterns): AcceptanceVerdict =
        AcceptanceVerdict.Ok

    fun assertionEdit(diff: Sequence<String>, keep: Regex): List<String> = emptyList()
}

object GateInputs {
    fun selector(value: String): String = value

    fun qaCommit(value: String): String = value

    fun file(value: String): String = value
}

class InvalidAcceptancePatternException(message: String) : RuntimeException(message)

class InvalidGateInputException(message: String) : RuntimeException(message)
