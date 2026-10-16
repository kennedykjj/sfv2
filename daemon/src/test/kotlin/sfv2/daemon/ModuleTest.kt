package sfv2.daemon

import org.assertj.core.api.Assertions.assertThat
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModuleTest {

    @TempDir
    lateinit var runtimeDir: Path

    private fun launch(env: Map<String, String>): Pair<DaemonLaunch, String> {
        val buffer = ByteArrayOutputStream()
        val launch = PrintStream(buffer, true, Charsets.UTF_8).use { launchDaemon(env, it) }
        return launch to buffer.toString(Charsets.UTF_8)
    }

    @Test
    fun `AC-5 hardening launch with a daemon already running fails with code 1 and a stderr message`() {
        val env = mapOf("XDG_RUNTIME_DIR" to runtimeDir.toString())
        val (first, _) = launch(env)
        val running = (first as DaemonLaunch.Started).server
        try {
            val (second, err) = launch(env)

            assertThat(second).isEqualTo(DaemonLaunch.Failed(exitCode = 1))
            assertThat(err).isNotBlank()
        } finally {
            running.close()
        }
    }

    @Test
    fun `AC-6 hardening launch without XDG_RUNTIME_DIR fails with code 1 and a stderr message`() {
        val (launch, err) = launch(emptyMap())

        assertThat(launch).isEqualTo(DaemonLaunch.Failed(exitCode = 1))
        assertThat(err).contains("XDG_RUNTIME_DIR")
    }

    @Test
    fun `AC-7 hardening launch logic returns a started server instead of exiting the process`() {
        val (launch, err) = launch(mapOf("XDG_RUNTIME_DIR" to runtimeDir.toString()))

        val started = launch as DaemonLaunch.Started
        try {
            assertThat(Files.exists(resolveSocketPath(mapOf("XDG_RUNTIME_DIR" to runtimeDir.toString())))).isTrue()
            assertThat(err).isEmpty()
        } finally {
            started.server.close()
        }
    }

    @Test
    fun `module is named after its Gradle project`() {
        assertThat(MODULE_NAME).isEqualTo("daemon")
    }
}
