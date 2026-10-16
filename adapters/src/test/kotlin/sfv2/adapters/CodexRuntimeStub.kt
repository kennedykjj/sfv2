package sfv2.adapters

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult

// shipfloor:qa-stub - lets the red gate compile; the implementer deletes this file
// and adds the real classes in src/main.
interface ProcessRunner {
    fun run(command: List<String>, workingDir: Path, stdoutFile: Path, timeout: Duration): ProcessOutcome
}

sealed interface ProcessOutcome {
    data class Exited(val code: Int) : ProcessOutcome

    data object TimedOut : ProcessOutcome
}

class CodexRuntime(
    private val processRunner: ProcessRunner,
    private val transcriptDir: Path,
    private val clock: Clock,
    private val timeout: Duration,
) : Runtime {
    override fun launch(request: SessionRequest): SessionResult =
        SessionResult.Hung(transcriptDir.resolve("stub"))
}
