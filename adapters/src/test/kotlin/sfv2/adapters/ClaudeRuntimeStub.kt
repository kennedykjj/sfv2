package sfv2.adapters

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult

// shipfloor:qa-stub - lets the acceptance tests compile; the implementer deletes this file
// and creates adapters/src/main/kotlin/sfv2/adapters/ClaudeRuntime.kt.
class ClaudeRuntime(
    val binary: String = "claude",
    val transcriptDir: Path,
    val timeout: Duration,
    val clock: Clock = Clock.systemUTC(),
) : Runtime {
    override fun launch(request: SessionRequest): SessionResult =
        SessionResult.Exited(-1, Path.of("/nonexistent/stub.jsonl"))
}

class RuntimeBinaryNotFoundException(binary: String, stage: String) :
    RuntimeException("claude binary not found: $binary (stage=$stage)")
