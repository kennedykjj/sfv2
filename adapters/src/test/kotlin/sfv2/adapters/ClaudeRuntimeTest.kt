package sfv2.adapters

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult

object ClaudeRuntimeFixtures {
    val fixedClock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)

    /** Writes an executable sh script and returns its path. */
    fun aScript(dir: Path, name: String, body: String): Path {
        val file = dir.resolve(name)
        Files.writeString(file, "#!/bin/sh\n$body\n")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
        return file
    }

    /** A fake claude that records cwd, args and stdin under [rec] and exits with [exitCode]. */
    fun aRecordingClaude(dir: Path, rec: Path, exitCode: Int = 0, extra: String = ""): Path =
        aScript(
            dir,
            "fake-claude",
            """
            echo started > "${rec}/started"
            pwd > "${rec}/cwd"
            for a in "${'$'}@"; do printf '%s\n' "${'$'}a"; done > "${rec}/args"
            cat > "${rec}/stdin"
            echo stdout-line
            echo stderr-line >&2
            $extra
            exit $exitCode
            """.trimIndent(),
        )

    fun aRequest(
        stage: String = "implement",
        worktree: Path,
        model: String = "model-x",
        mcpConfig: Path = Path.of("/tmp/mcp.json"),
    ): SessionRequest = SessionRequest(stage, worktree, model, mcpConfig)

    fun runtime(binary: Path, transcriptDir: Path, timeout: Duration = Duration.ofSeconds(30)) =
        ClaudeRuntime(binary.toString(), transcriptDir, timeout, fixedClock)

    fun fileCount(dir: Path): Long =
        if (Files.exists(dir)) Files.list(dir).use { it.count() } else 0L
}

class ClaudeRuntimeTest {

    @TempDir
    lateinit var tmp: Path

    private fun dirs(): Triple<Path, Path, Path> {
        val wt = Files.createDirectory(tmp.resolve("wt"))
        val rec = Files.createDirectory(tmp.resolve("rec"))
        val transcripts = tmp.resolve("transcripts")
        return Triple(wt, rec, transcripts)
    }

    @Test
    fun `AC-1 runs claude headless in the worktree with the stage prompt on stdin`() {
        val (wt, rec, transcripts) = dirs()
        val binary = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec)

        ClaudeRuntimeFixtures.runtime(binary, transcripts)
            .launch(ClaudeRuntimeFixtures.aRequest(worktree = wt))

        assertThat(Path.of(Files.readString(rec.resolve("cwd")).trim()).toRealPath())
            .isEqualTo(wt.toRealPath())
        assertThat(Files.readAllLines(rec.resolve("args"))).contains("-p")
        assertThat(Files.readString(rec.resolve("stdin")).trim()).isEqualTo("/sf:implement")
    }

    @Test
    fun `AC-2 passes the daemon mcp config strictly and the model`() {
        val (wt, rec, transcripts) = dirs()
        val mcp = tmp.resolve("daemon-mcp.json")
        val binary = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec)

        ClaudeRuntimeFixtures.runtime(binary, transcripts)
            .launch(ClaudeRuntimeFixtures.aRequest(worktree = wt, mcpConfig = mcp, model = "model-y"))

        val args = Files.readAllLines(rec.resolve("args"))
        assertThat(args).contains("--strict-mcp-config")
        assertThat(args[args.indexOf("--mcp-config") + 1]).isEqualTo(mcp.toString())
        assertThat(args[args.indexOf("--model") + 1]).isEqualTo("model-y")
    }

    @Test
    fun `AC-3 returns the transcript path holding stdout and stderr`() {
        val (wt, rec, transcripts) = dirs()
        val binary = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec)

        val result = ClaudeRuntimeFixtures.runtime(binary, transcripts)
            .launch(ClaudeRuntimeFixtures.aRequest(worktree = wt))

        val path = result.transcriptPath
        assertThat(path.parent.toRealPath()).isEqualTo(transcripts.toRealPath())
        assertThat(path.fileName.toString()).startsWith("implement-").endsWith(".jsonl").contains("2026")
        assertThat(Files.readString(path)).contains("stdout-line").contains("stderr-line")
    }

    @Test
    fun `AC-4 returns the exit code including non-zero`() {
        val (wt, rec, transcripts) = dirs()
        val ok = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec, exitCode = 0)
        val okResult = ClaudeRuntimeFixtures.runtime(ok, transcripts)
            .launch(ClaudeRuntimeFixtures.aRequest(worktree = wt))
        assertThat(okResult).isInstanceOf(SessionResult.Exited::class.java)
        assertThat((okResult as SessionResult.Exited).exitCode).isEqualTo(0)

        val failDir = Files.createDirectory(tmp.resolve("failbin"))
        val failing = ClaudeRuntimeFixtures.aRecordingClaude(failDir, rec, exitCode = 3)
        val failResult = ClaudeRuntimeFixtures.runtime(failing, transcripts)
            .launch(ClaudeRuntimeFixtures.aRequest(worktree = wt))
        assertThat(failResult).isInstanceOf(SessionResult.Exited::class.java)
        assertThat((failResult as SessionResult.Exited).exitCode).isEqualTo(3)
    }

    @Test
    fun `AC-5 missing binary raises a clean error naming binary and stage`() {
        val (wt, _, transcripts) = dirs()
        val missing = tmp.resolve("no-such-claude")

        val error = assertThrows<RuntimeBinaryNotFoundException> {
            ClaudeRuntimeFixtures.runtime(missing, transcripts)
                .launch(ClaudeRuntimeFixtures.aRequest(stage = "spec", worktree = wt))
        }

        assertThat(error.message).isEqualTo("claude binary not found: $missing (stage=spec)")
        assertThat(error.cause).isNull()
    }

    @Test
    fun `AC-6 usable as the Runtime port and returns Exited`() {
        val (wt, rec, transcripts) = dirs()
        val binary = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec, exitCode = 0)
        val runtime: Runtime = ClaudeRuntimeFixtures.runtime(binary, transcripts)

        val result = runtime.launch(ClaudeRuntimeFixtures.aRequest(worktree = wt))

        assertThat(result).isInstanceOf(SessionResult.Exited::class.java)
        assertThat(Files.exists(result.transcriptPath)).isTrue()
    }

    @Test
    fun `AC-7 timeout kills the process and its descendants and returns Hung`() {
        val (wt, rec, transcripts) = dirs()
        val binary = ClaudeRuntimeFixtures.aScript(
            tmp,
            "hanging-claude",
            """
            echo partial-output
            sleep 300 &
            echo ${'$'}! > "${rec}/child.pid"
            wait
            """.trimIndent(),
        )

        val result = ClaudeRuntimeFixtures.runtime(binary, transcripts, Duration.ofSeconds(2))
            .launch(ClaudeRuntimeFixtures.aRequest(worktree = wt))

        assertThat(result).isInstanceOf(SessionResult.Hung::class.java)
        assertThat(Files.readString(result.transcriptPath)).contains("partial-output")
        val childPid = Files.readString(rec.resolve("child.pid")).trim().toLong()
        ProcessHandle.of(childPid).ifPresent { it.onExit().get(10, TimeUnit.SECONDS) }
        assertThat(ProcessHandle.of(childPid).map { it.isAlive }.orElse(false)).isFalse()
    }

    @Test
    fun `AC-8 invalid stage is refused naming the field with no process and no file`() {
        val (wt, rec, transcripts) = dirs()
        val binary = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec)

        for (stage in listOf("Bad_Stage", "", "1abc", "a b", "../x")) {
            val error = assertThrows<RuntimeException> {
                ClaudeRuntimeFixtures.runtime(binary, transcripts)
                    .launch(ClaudeRuntimeFixtures.aRequest(stage = stage, worktree = wt))
            }
            assertThat(error).isNotInstanceOf(RuntimeBinaryNotFoundException::class.java)
            assertThat(error.message).contains("stage")
        }

        assertThat(Files.exists(rec.resolve("started"))).isFalse()
        assertThat(ClaudeRuntimeFixtures.fileCount(transcripts)).isEqualTo(0)
    }

    @Test
    fun `AC-8 nonexistent worktree is refused naming the field with no process and no file`() {
        val (_, rec, transcripts) = dirs()
        val binary = ClaudeRuntimeFixtures.aRecordingClaude(tmp, rec)

        val error = assertThrows<RuntimeException> {
            ClaudeRuntimeFixtures.runtime(binary, transcripts)
                .launch(ClaudeRuntimeFixtures.aRequest(worktree = tmp.resolve("missing-wt")))
        }

        assertThat(error).isNotInstanceOf(RuntimeBinaryNotFoundException::class.java)
        assertThat(error.message).contains("worktree")
        assertThat(Files.exists(rec.resolve("started"))).isFalse()
        assertThat(ClaudeRuntimeFixtures.fileCount(transcripts)).isEqualTo(0)
    }
}
