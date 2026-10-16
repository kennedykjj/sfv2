package sfv2.daemon

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Runs a gate as a child process without a shell; stdout and stderr go straight to the output file. */
class ProcessGateRunner(private val log: DaemonLog = StderrDaemonLog) : GateRunner {

    override fun run(spec: GateSpec, outputFile: Path): GateRun {
        val process = try {
            ProcessBuilder(spec.command)
                .directory(spec.workingDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start()
        } catch (e: IOException) {
            log.log("gate_start_error", mapOf("gate" to spec.name))
            return GateRun(exitCode = null, timedOut = false)
        }
        if (process.waitFor(spec.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            return GateRun(exitCode = process.exitValue(), timedOut = false)
        }
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor()
        return GateRun(exitCode = null, timedOut = true)
    }
}
