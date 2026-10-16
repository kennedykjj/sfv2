package sfv2.adapters

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/** [ProcessRunner] on top of [ProcessBuilder]: stdout to a file, stderr merged, kill of the whole tree on timeout. */
class ProcessRunnerImpl : ProcessRunner {
    override fun run(command: List<String>, workingDir: Path, stdoutFile: Path, timeout: Duration): ProcessOutcome {
        val process = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(stdoutFile.toFile())
            .start()
        if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            return ProcessOutcome.Exited(process.exitValue())
        }
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor()
        return ProcessOutcome.TimedOut
    }
}
