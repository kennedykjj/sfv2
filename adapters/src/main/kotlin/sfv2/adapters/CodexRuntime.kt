package sfv2.adapters

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult

/** Runs an external command and sends its stdout to a file. No shell is involved. */
interface ProcessRunner {
    fun run(command: List<String>, workingDir: Path, stdoutFile: Path, timeout: Duration): ProcessOutcome
}

sealed interface ProcessOutcome {
    data class Exited(val code: Int) : ProcessOutcome

    data object TimedOut : ProcessOutcome
}

class InvalidSessionRequestException(message: String) : RuntimeException(message)

@Serializable
private data class McpConfigFile(val mcpServers: Map<String, McpServerEntry> = emptyMap())

@Serializable
private data class McpServerEntry(val command: String, val args: List<String> = emptyList())

/** Runs one agent session through `codex exec`, applying the daemon's MCP servers as `-c` overrides. */
class CodexRuntime(
    private val processRunner: ProcessRunner,
    private val transcriptDir: Path,
    private val clock: Clock,
    private val timeout: Duration,
) : Runtime {
    override fun launch(request: SessionRequest): SessionResult {
        if (!STAGE_PATTERN.matches(request.stage)) {
            throw InvalidSessionRequestException("invalid stage '${request.stage}': must match ${STAGE_PATTERN.pattern}")
        }
        val servers = readServers(request.mcpConfig)
        servers.keys.forEach { name ->
            if (!SERVER_NAME_PATTERN.matches(name)) {
                throw InvalidSessionRequestException(
                    "invalid MCP server name '$name': must match ${SERVER_NAME_PATTERN.pattern}",
                )
            }
        }

        val command = buildList {
            addAll(listOf("codex", "exec", "--json", "--cd", request.worktree.toString(), "--model", request.model))
            servers.forEach { (name, server) ->
                addAll(listOf("-c", "mcp_servers.$name.command=${tomlString(server.command)}"))
                addAll(listOf("-c", "mcp_servers.$name.args=${tomlArray(server.args)}"))
            }
        }
        Files.createDirectories(transcriptDir)
        val transcript = transcriptDir.resolve("${request.stage}-${clock.millis()}.jsonl")

        return when (val outcome = processRunner.run(command, request.worktree, transcript, timeout)) {
            is ProcessOutcome.Exited -> SessionResult.Exited(outcome.code, transcript)
            ProcessOutcome.TimedOut -> SessionResult.Hung(transcript)
        }
    }

    private fun readServers(mcpConfig: Path): Map<String, McpServerEntry> =
        try {
            Json { ignoreUnknownKeys = true }.decodeFromString<McpConfigFile>(Files.readString(mcpConfig)).mcpServers
        } catch (e: IOException) {
            throw InvalidSessionRequestException("cannot read mcpConfig $mcpConfig: ${e.message}")
        } catch (e: SerializationException) {
            throw InvalidSessionRequestException("invalid mcpConfig $mcpConfig: ${e.message}")
        }

    private fun tomlString(value: String): String {
        val escaped = buildString {
            value.forEach { c ->
                when {
                    c == '\\' -> append("\\\\")
                    c == '"' -> append("\\\"")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c.code < 0x20 || c.code == 0x7f -> append("\\u%04X".format(c.code))
                    else -> append(c)
                }
            }
        }
        return "\"$escaped\""
    }

    private fun tomlArray(values: List<String>): String = values.joinToString(", ", "[", "]") { tomlString(it) }

    private companion object {
        val STAGE_PATTERN = Regex("[a-z][a-z0-9-]*")
        val SERVER_NAME_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
