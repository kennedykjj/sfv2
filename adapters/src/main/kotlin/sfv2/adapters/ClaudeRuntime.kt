package sfv2.adapters

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult

class RuntimeBinaryNotFoundException(binary: String, stage: String) :
    RuntimeException("claude binary not found: $binary (stage=$stage)")

/** Runs one session as `claude -p` in the worktree; returns facts (transcript, exit code), never interprets them. */
class ClaudeRuntime(
    private val binary: String = "claude",
    private val transcriptDir: Path,
    private val timeout: Duration,
    private val clock: Clock = Clock.systemUTC(),
) : Runtime {

    override fun launch(request: SessionRequest): SessionResult {
        require(STAGE_PATTERN.matches(request.stage)) {
            "stage must match [a-z][a-z0-9-]*: '${request.stage}'"
        }
        require(Files.isDirectory(request.worktree)) {
            "worktree does not exist: ${request.worktree}"
        }

        Files.createDirectories(transcriptDir)
        val instant = clock.instant().toString().replace(':', '-')
        val transcript = transcriptDir.resolve("${request.stage}-$instant.jsonl")

        val process = try {
            ProcessBuilder(command(request))
                .directory(request.worktree.toFile())
                .redirectErrorStream(true)
                .redirectOutput(transcript.toFile())
                .start()
        } catch (e: IOException) {
            Files.deleteIfExists(transcript)
            throw RuntimeBinaryNotFoundException(binary, request.stage)
        }

        process.outputStream.use { it.write("/sf:${request.stage}\n".toByteArray()) }

        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            process.waitFor()
            return SessionResult.Hung(transcript)
        }
        return SessionResult.Exited(process.exitValue(), transcript)
    }

    private fun command(request: SessionRequest): List<String> = listOf(
        binary,
        "-p",
        "--permission-mode", "acceptEdits",
        "--strict-mcp-config",
        "--mcp-config", request.mcpConfig.toString(),
        "--model", request.model,
        "--output-format", "stream-json",
        "--verbose",
    )

    private companion object {
        val STAGE_PATTERN = Regex("[a-z][a-z0-9-]*")
    }
}
