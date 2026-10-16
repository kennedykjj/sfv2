package sfv2.adapters

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult

object CodexRuntimeFixtures {
    val now: Instant = Instant.ofEpochMilli(1_700_000_000_000)
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val timeout: Duration = Duration.ofMinutes(30)

    fun aMcpConfig(dir: Path, servers: String): Path {
        val file = dir.resolve("mcp-${servers.hashCode()}.json")
        Files.writeString(file, """{"mcpServers": {$servers}}""")
        return file
    }

    fun aServer(name: String, command: String, args: String): String =
        """"$name": {"command": $command, "args": $args}"""

    fun aRequest(
        mcpConfig: Path,
        worktree: Path,
        stage: String = "implement",
        model: String = "gpt-test",
    ) = SessionRequest(stage = stage, worktree = worktree, model = model, mcpConfig = mcpConfig)
}

class RecordingProcessRunner(private val outcome: ProcessOutcome) : ProcessRunner {
    data class Call(
        val command: List<String>,
        val workingDir: Path,
        val stdoutFile: Path,
        val timeout: Duration,
        val env: Map<String, String> = emptyMap(),
    )

    val calls = mutableListOf<Call>()

    override fun run(
        command: List<String>,
        workingDir: Path,
        stdoutFile: Path,
        timeout: Duration,
        env: Map<String, String>,
    ): ProcessOutcome {
        calls += Call(command, workingDir, stdoutFile, timeout, env)
        return outcome
    }
}

class CodexRuntimeTest {
    @TempDir
    lateinit var tmp: Path

    private fun transcripts(): Path = tmp.resolve("transcripts").also { Files.createDirectories(it) }

    private fun runtime(runner: ProcessRunner): CodexRuntime =
        CodexRuntime(runner, transcripts(), CodexRuntimeFixtures.clock, CodexRuntimeFixtures.timeout)

    private fun defaultConfig(): Path =
        CodexRuntimeFixtures.aMcpConfig(
            tmp,
            CodexRuntimeFixtures.aServer("daemon", "\"sfv2-mcp\"", "[\"serve\", \"--stdio\"]"),
        )

    private fun overrides(command: List<String>): List<String> =
        command.withIndex().filter { it.value == "-c" }.map { command[it.index + 1] }

    @Test
    fun `AC-1 runs codex exec in the worktree with the daemon MCP config as overrides`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(0))
        val worktree = tmp.resolve("wt")

        runtime(runner).launch(CodexRuntimeFixtures.aRequest(defaultConfig(), worktree))

        val call = runner.calls.single()
        assertThat(call.command.take(3)).containsExactly("codex", "exec", "--json")
        assertThat(call.command).containsSubsequence("--cd", worktree.toString())
        assertThat(call.command).containsSubsequence("--model", "gpt-test")
        assertThat(call.workingDir).isEqualTo(worktree)
        assertThat(call.timeout).isEqualTo(CodexRuntimeFixtures.timeout)
        assertThat(overrides(call.command)).containsExactlyInAnyOrder(
            "mcp_servers.daemon.command=\"sfv2-mcp\"",
            "mcp_servers.daemon.args=[\"serve\", \"--stdio\"]",
        )
    }

    @Test
    fun `AC-2 returns transcript path and exit code on normal end`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(0))

        val result = runtime(runner).launch(CodexRuntimeFixtures.aRequest(defaultConfig(), tmp.resolve("wt")))

        val expected = transcripts().resolve("implement-1700000000000.jsonl")
        assertThat(result).isEqualTo(SessionResult.Exited(0, expected))
        assertThat(runner.calls.single().stdoutFile).isEqualTo(expected)
    }

    @Test
    fun `AC-3 non-zero exit is returned with the transcript path`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(3))

        val result = runtime(runner).launch(CodexRuntimeFixtures.aRequest(defaultConfig(), tmp.resolve("wt")))

        assertThat(result).isEqualTo(
            SessionResult.Exited(3, transcripts().resolve("implement-1700000000000.jsonl")),
        )
    }

    @Test
    fun `AC-3 timeout is reported as Hung with the transcript path`() {
        val runner = RecordingProcessRunner(ProcessOutcome.TimedOut)

        val result = runtime(runner).launch(CodexRuntimeFixtures.aRequest(defaultConfig(), tmp.resolve("wt")))

        assertThat(result).isEqualTo(
            SessionResult.Hung(transcripts().resolve("implement-1700000000000.jsonl")),
        )
    }

    @Test
    fun `AC-4 is usable wherever the Runtime port is expected`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(0))
        val port: Runtime = runtime(runner)

        val result = port.launch(CodexRuntimeFixtures.aRequest(defaultConfig(), tmp.resolve("wt")))

        assertThat(result).isInstanceOf(SessionResult.Exited::class.java)
        assertThat(runner.calls).hasSize(1)
    }

    @Test
    fun `AC-5 refuses a server name outside the allowed charset`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(0))
        val config = CodexRuntimeFixtures.aMcpConfig(
            tmp,
            CodexRuntimeFixtures.aServer("bad.name=x", "\"cmd\"", "[]"),
        )

        assertThatThrownBy { runtime(runner).launch(CodexRuntimeFixtures.aRequest(config, tmp.resolve("wt"))) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("bad.name=x")
        assertThat(runner.calls).isEmpty()
        assertThat(Files.list(transcripts()).use { it.count() }).isZero()
    }

    @Test
    fun `AC-5 refuses a stage outside the allowed charset`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(0))
        val request = CodexRuntimeFixtures.aRequest(defaultConfig(), tmp.resolve("wt"), stage = "../evil")

        assertThatThrownBy { runtime(runner).launch(request) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("stage")
        assertThat(runner.calls).isEmpty()
        assertThat(Files.list(transcripts()).use { it.count() }).isZero()
    }

    @Test
    fun `AC-5 encodes quotes and backslashes in command and args as TOML text`() {
        val runner = RecordingProcessRunner(ProcessOutcome.Exited(0))
        val config = CodexRuntimeFixtures.aMcpConfig(
            tmp,
            CodexRuntimeFixtures.aServer("daemon", "\"a\\\"b\\\\c\"", "[\"x\\\"y\", \"p\\\\q\"]"),
        )

        runtime(runner).launch(CodexRuntimeFixtures.aRequest(config, tmp.resolve("wt")))

        assertThat(overrides(runner.calls.single().command)).containsExactlyInAnyOrder(
            "mcp_servers.daemon.command=\"a\\\"b\\\\c\"",
            "mcp_servers.daemon.args=[\"x\\\"y\", \"p\\\\q\"]",
        )
    }
}
