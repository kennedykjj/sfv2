package sfv2.daemon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpecGatesTest {

    private val daemonTest = "daemon/src/test/kotlin/sfv2/daemon/XTest.kt"
    private val layers = listOf(
        SpecLayer("daemon", "daemon/", listOf("daemon/src/test/**")),
        SpecLayer("cli", "cli/", listOf("cli/src/test/**")),
    )

    private fun aSpec(
        weight: String? = "light",
        criteria: String = "- **AC-1** — primeiro criterio",
        testPlan: String = "| AC-1 | daemon | `$daemonTest` |",
        architecture: String = "Camada unica. Arquivos: `daemon/src/main/kotlin/A.kt`, `daemon/src/main/kotlin/B.kt`.",
        security: String = "_Cybersec_ — n/a",
        rollback: String = "n/a",
        touchesUi: String = "no",
    ): String {
        val weightLine = if (weight == null) "" else "**Weight:** $weight\n"
        return """
            # Spec

            **Issue:** #1
            $weightLine
            ## Product
            **Acceptance criteria**
            $criteria
            **Touches UI:** $touchesUi

            ## Architecture
            $architecture

            ## Test plan
            | AC | Layer | Test file |
            |---|---|---|
            $testPlan

            ## Security
            $security

            ## Rollback
            $rollback

            ## Decisions
            - nada
        """.trimIndent()
    }

    private fun check(spec: String) = SpecGates.check(spec, layers)

    @Test
    fun `AC-1 approves a spec whose criterion has a known layer and a matching test path`() {
        val result = check(aSpec())

        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `AC-1 reports a criterion without a test plan row naming the AC`() {
        val result = check(aSpec(
            criteria = "- **AC-1** — um\n- **AC-2** — dois",
            testPlan = "| AC-1 | daemon | `$daemonTest` |",
        ))

        assertThat(result.findings).hasSize(1)
        assertThat(result.findings.single().gate).isEqualTo(SpecGate.TEST_PLAN)
        assertThat(result.findings.single().ac).isEqualTo("AC-2")
    }

    @Test
    fun `AC-1 reports a criterion whose layer does not exist in the project`() {
        val result = check(aSpec(testPlan = "| AC-1 | mobile | `mobile/src/test/X.kt` |"))

        assertThat(result.findings.map { it.gate to it.ac }).containsExactly(SpecGate.TEST_PLAN to "AC-1")
        assertThat(result.findings.single().message).contains("mobile")
    }

    @Test
    fun `AC-1 reports a real layer row without a test path`() {
        val result = check(aSpec(testPlan = "| AC-1 | daemon | |"))

        assertThat(result.findings.map { it.gate to it.ac }).containsExactly(SpecGate.TEST_PLAN to "AC-1")
    }

    @Test
    fun `AC-1 reports a test path that matches no test glob of the layer`() {
        val result = check(aSpec(testPlan = "| AC-1 | daemon | `daemon/src/main/kotlin/X.kt` |"))

        assertThat(result.findings.map { it.gate to it.ac }).containsExactly(SpecGate.TEST_PLAN to "AC-1")
    }

    @Test
    fun `AC-1 accepts a manual layer row without a path`() {
        val result = check(aSpec(testPlan = "| AC-1 | manual | |"))

        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `AC-2 calculates light for one layer, few paths, no UI and no risk signals`() {
        assertThat(check(aSpec()).weight).isEqualTo(SpecWeight.LIGHT)
    }

    @Test
    fun `AC-2 calculates heavy when Architecture paths touch two layers`() {
        val result = check(aSpec(
            weight = "heavy",
            architecture = "Arquivos: `daemon/src/main/kotlin/A.kt`, `cli/src/main/kotlin/B.kt`.",
        ))

        assertThat(result.weight).isEqualTo(SpecWeight.HEAVY)
    }

    @Test
    fun `AC-2 calculates heavy when Security is filled in`() {
        val result = check(aSpec(weight = "heavy", security = "_Cybersec_\nToken passa por log; mascarar."))

        assertThat(result.weight).isEqualTo(SpecWeight.HEAVY)
    }

    @Test
    fun `AC-2 calculates heavy when Rollback is not n-a`() {
        val result = check(aSpec(weight = "heavy", rollback = "Reverter a migracao com drop da coluna."))

        assertThat(result.weight).isEqualTo(SpecWeight.HEAVY)
    }

    @Test
    fun `AC-2 calculates heavy with more than eight paths in one layer`() {
        val paths = (1..9).joinToString(", ") { "`daemon/src/main/kotlin/F$it.kt`" }
        val result = check(aSpec(weight = "heavy", architecture = "Arquivos: $paths."))

        assertThat(result.weight).isEqualTo(SpecWeight.HEAVY)
    }

    @Test
    fun `AC-2 calculates standard when there are four paths in one layer`() {
        val paths = (1..4).joinToString(", ") { "`daemon/src/main/kotlin/F$it.kt`" }
        val result = check(aSpec(weight = "standard", architecture = "Arquivos: $paths."))

        assertThat(result.weight).isEqualTo(SpecWeight.STANDARD)
    }

    @Test
    fun `AC-2 calculates standard when the card touches UI`() {
        val result = check(aSpec(weight = "standard", touchesUi = "yes"))

        assertThat(result.weight).isEqualTo(SpecWeight.STANDARD)
    }

    @Test
    fun `AC-2 does not let the number of criteria change the weight`() {
        val criteria = (1..12).joinToString("\n") { "- **AC-$it** — criterio $it" }
        val plan = (1..12).joinToString("\n") { "| AC-$it | daemon | `$daemonTest` |" }
        val result = check(aSpec(criteria = criteria, testPlan = plan))

        assertThat(result.weight).isEqualTo(SpecWeight.LIGHT)
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `AC-3 reports a missing declared weight with the calculated value`() {
        val result = check(aSpec(weight = null))

        assertThat(result.findings.map { it.gate }).containsExactly(SpecGate.WEIGHT)
        assertThat(result.findings.single().message.lowercase()).contains("light")
    }

    @Test
    fun `AC-3 reports an invalid declared weight with both values`() {
        val result = check(aSpec(weight = "gigantic"))

        assertThat(result.findings.map { it.gate }).containsExactly(SpecGate.WEIGHT)
        assertThat(result.findings.single().message.lowercase()).contains("gigantic").contains("light")
    }

    @Test
    fun `AC-3 reports a declared weight below the calculated one with both values`() {
        val result = check(aSpec(weight = "light", security = "_Cybersec_\nToken em log."))

        assertThat(result.weight).isEqualTo(SpecWeight.HEAVY)
        assertThat(result.findings.map { it.gate }).containsExactly(SpecGate.WEIGHT)
        assertThat(result.findings.single().message.lowercase()).contains("light").contains("heavy")
    }

    @Test
    fun `AC-3 accepts a declared weight above the calculated one`() {
        val result = check(aSpec(weight = "heavy"))

        assertThat(result.weight).isEqualTo(SpecWeight.LIGHT)
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `AC-4 finds criteria written in every format used by factory specs`() {
        val criteria = listOf(
            "- **AC-1** — bold dash",
            "- AC-2: colon",
            "AC-3 — bare dash",
        ).joinToString("\n")
        val result = check(aSpec(criteria = criteria, testPlan = "| AC-1 | daemon | `$daemonTest` |"))

        assertThat(result.findings.map { it.ac }).containsExactlyInAnyOrder("AC-2", "AC-3")
        assertThat(result.findings.map { it.gate }.toSet()).containsExactly(SpecGate.TEST_PLAN)
    }

    @Test
    fun `AC-4 reports a TEST_PLAN finding when no criterion is recognizable`() {
        val result = check(aSpec(criteria = "- o sistema deve funcionar", testPlan = ""))

        assertThat(result.findings.map { it.gate }).contains(SpecGate.TEST_PLAN)
    }
}
