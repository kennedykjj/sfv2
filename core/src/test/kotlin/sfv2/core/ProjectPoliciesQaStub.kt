// shipfloor:qa-stub — compile-only stub so the red gate fails by assertion; the implementer deletes this file.
package sfv2.core

enum class DeliveryMode { AUTONOMOUS, PR_HANDOFF }

enum class ExternalEffect { IMPORT, MILESTONE, CLOSE }

enum class Signer { OWNER, POLICY }

data class Conventions(val commitFormat: String, val branchFormat: String)

class InvalidPolicyException(val field: String, message: String) : RuntimeException(message)

data class ProjectPolicies(
    val deliveryMode: DeliveryMode,
    val signers: Map<ExternalEffect, Signer>,
    val conventions: Conventions,
) {
    companion object {
        val DEFAULT = ProjectPolicies(DeliveryMode.PR_HANDOFF, emptyMap(), Conventions("", ""))

        @Suppress("UNUSED_PARAMETER")
        fun from(raw: Map<String, Any?>?): ProjectPolicies = DEFAULT
    }
}
