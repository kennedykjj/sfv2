package sfv2.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProjectPoliciesTest {

    private val defaults = ProjectPolicies.DEFAULT

    @Test
    fun `AC-1 absent policies yield the defaults`() {
        assertThat(ProjectPolicies.from(null)).isEqualTo(defaults)
        assertThat(ProjectPolicies.from(emptyMap())).isEqualTo(defaults)
        assertThat(defaults.deliveryMode).isEqualTo(DeliveryMode.AUTONOMOUS)
        assertThat(defaults.signers).isEqualTo(ExternalEffect.entries.associateWith { Signer.OWNER })
        assertThat(defaults.conventions.commitFormat).isEqualTo("{type}({scope}): {subject}")
        assertThat(defaults.conventions.branchFormat).isEqualTo("feat/{id}-{slug}")
    }

    @Test
    fun `AC-2 delivery mode is exposed as a typed enum`() {
        assertThat(ProjectPolicies.from(mapOf("delivery_mode" to "autonomous")).deliveryMode)
            .isEqualTo(DeliveryMode.AUTONOMOUS)
        assertThat(ProjectPolicies.from(mapOf("delivery_mode" to "pr")).deliveryMode)
            .isEqualTo(DeliveryMode.PR_HANDOFF)
    }

    @Test
    fun `AC-3 signer is exposed per external effect`() {
        val policies = ProjectPolicies.from(
            mapOf("signers" to mapOf("import" to "owner", "milestone" to "policy", "close" to "policy")),
        )

        assertThat(policies.signers[ExternalEffect.IMPORT]).isEqualTo(Signer.OWNER)
        assertThat(policies.signers[ExternalEffect.MILESTONE]).isEqualTo(Signer.POLICY)
        assertThat(policies.signers[ExternalEffect.CLOSE]).isEqualTo(Signer.POLICY)
    }

    @Test
    fun `AC-4 commit and branch formats are exposed`() {
        val policies = ProjectPolicies.from(
            mapOf(
                "conventions" to mapOf(
                    "commit_format" to "[{id}] {subject}",
                    "branch_format" to "work/{id}",
                ),
            ),
        )

        assertThat(policies.conventions.commitFormat).isEqualTo("[{id}] {subject}")
        assertThat(policies.conventions.branchFormat).isEqualTo("work/{id}")
    }

    @Test
    fun `AC-5 invalid delivery mode names the field`() {
        val ex = assertThrows<InvalidPolicyException> {
            ProjectPolicies.from(mapOf("delivery_mode" to "yolo"))
        }
        assertThat(ex.field).isEqualTo("delivery_mode")
        assertThat(ex.message).contains("delivery_mode").contains("yolo")
    }

    @Test
    fun `AC-5 invalid signer and unknown effect name the field`() {
        val badSigner = assertThrows<InvalidPolicyException> {
            ProjectPolicies.from(mapOf("signers" to mapOf("close" to "nobody")))
        }
        assertThat(badSigner.field).isEqualTo("signers.close")
        assertThat(badSigner.message).contains("signers.close")

        val unknown = assertThrows<InvalidPolicyException> {
            ProjectPolicies.from(mapOf("signers" to mapOf("merge" to "owner")))
        }
        assertThat(unknown.field).isEqualTo("signers.merge")
    }

    @Test
    fun `AC-5 invalid formats name the field`() {
        val invalidCommits = listOf("", "{type}: text", "{subject} {nope}")
        invalidCommits.forEach { value ->
            val ex = assertThrows<InvalidPolicyException> {
                ProjectPolicies.from(mapOf("conventions" to mapOf("commit_format" to value)))
            }
            assertThat(ex.field).isEqualTo("conventions.commit_format")
        }
        val invalidBranches = listOf("", "work/{slug}", "{id}-{nope}")
        invalidBranches.forEach { value ->
            val ex = assertThrows<InvalidPolicyException> {
                ProjectPolicies.from(mapOf("conventions" to mapOf("branch_format" to value)))
            }
            assertThat(ex.field).isEqualTo("conventions.branch_format")
        }
    }

    @Test
    fun `AC-6 milestones list turns milestones on and no list leaves all off`() {
        assertThat(milestonesOf(defaults)).isEmpty()
        assertThat(milestonesOf(ProjectPolicies.from(mapOf("delivery_mode" to "pr")))).isEmpty()
        assertThat(milestonesOf(ProjectPolicies.from(mapOf("milestones" to listOf("started", "pr_opened")))))
            .containsExactlyInAnyOrder("STARTED", "PR_OPENED")
        assertThat(milestonesOf(ProjectPolicies.from(mapOf("milestones" to listOf("pr_opened")))))
            .containsExactly("PR_OPENED")
        assertThat(milestonesOf(ProjectPolicies.from(mapOf("milestones" to emptyList<String>())))).isEmpty()
    }

    /** Reads the new field by name so its absence fails an assertion, not the compilation. */
    private fun milestonesOf(policies: ProjectPolicies): Set<String> {
        val getter = policies.javaClass.methods.firstOrNull { it.name == "getMilestones" }
        assertThat(getter).describedAs("ProjectPolicies.milestones exists").isNotNull()
        return (requireNotNull(getter).invoke(policies) as Set<*>).map { (it as Enum<*>).name }.toSet()
    }

    @Test
    fun `AC-6 milestones that are not a list or name an unknown milestone are rejected`() {
        listOf<Any>("started", mapOf("started" to true), listOf("started", "merged")).forEach { value ->
            val ex = assertThrows<InvalidPolicyException> {
                ProjectPolicies.from(mapOf("milestones" to value))
            }
            assertThat(ex.field).describedAs("value $value").isEqualTo("milestones")
        }
    }

    @Test
    fun `AC-6 partial policies keep present fields and default the rest`() {
        val policies = ProjectPolicies.from(
            mapOf(
                "delivery_mode" to "pr",
                "signers" to mapOf("milestone" to "policy"),
                "conventions" to mapOf("branch_format" to "work/{id}"),
            ),
        )

        assertThat(policies.deliveryMode).isEqualTo(DeliveryMode.PR_HANDOFF)
        assertThat(policies.signers).isEqualTo(
            mapOf(
                ExternalEffect.IMPORT to Signer.OWNER,
                ExternalEffect.MILESTONE to Signer.POLICY,
                ExternalEffect.CLOSE to Signer.OWNER,
            ),
        )
        assertThat(policies.conventions.branchFormat).isEqualTo("work/{id}")
        assertThat(policies.conventions.commitFormat).isEqualTo(defaults.conventions.commitFormat)
    }
}
