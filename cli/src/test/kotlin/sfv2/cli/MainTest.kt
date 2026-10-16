package sfv2.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MainTest {
    private val out = StringBuilder()
    private val err = StringBuilder()

    private fun run(vararg args: String): Int =
        runCli(args.toList(), "1.2.3", { out.appendLine(it) }, { err.appendLine(it) })

    @Test
    fun `--version prints the version and exits 0`() {
        assertThat(run("--version")).isZero()
        assertThat(out.toString().trim()).isEqualTo("sfv2 1.2.3")
        assertThat(err).isEmpty()
    }

    @Test
    fun `an unknown argument prints the usage and exits 2`() {
        assertThat(run("--nope")).isEqualTo(2)
        assertThat(err.toString()).contains("Usage: sfv2").contains("--nope")
        assertThat(out).isEmpty()
    }

    @Test
    fun `--help prints the usage and exits 0`() {
        assertThat(run("--help")).isZero()
        assertThat(out.toString()).contains("Usage: sfv2").contains("--version")
    }

    @Test
    fun `the version comes from the build`() {
        assertThat(BuildInfo.version).isNotBlank().doesNotContain("\${")
    }
}
