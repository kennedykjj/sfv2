package sfv2.daemon

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Where a project's `factory.yml` text comes from. */
interface ProjectConfigSource {
    /** The file as committed on `main`, or null when the repository has no such file there. */
    fun read(repo: Path): String?
}

/** Reads `.shipfloor/factory.yml` from the `main` commit, never from the working tree. */
class GitProjectConfigSource(private val timeoutSeconds: Long = TIMEOUT_SECONDS) : ProjectConfigSource {

    override fun read(repo: Path): String? {
        // Output goes to a file so the wait below bounds the whole run: a pipe read would block until EOF.
        val output = Files.createTempFile("sfv2-factory-yml", ".txt")
        try {
            val process = try {
                ProcessBuilder(
                    "git", "-C", repo.toString(), "-c", "core.fsmonitor=false", "show", "main:$CONFIG_PATH",
                ).redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .also { it.environment()["GIT_TERMINAL_PROMPT"] = "0" }
                    .start()
            } catch (e: IOException) {
                throw InvalidProjectConfigException(CONFIG_FIELD, "cannot run git to read $CONFIG_FIELD")
            }
            try {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw InvalidProjectConfigException(CONFIG_FIELD, "reading $CONFIG_FIELD timed out")
                }
            } finally {
                process.destroyForcibly()
            }
            if (process.exitValue() != 0) return null
            if (Files.size(output) > MAX_BYTES) {
                throw InvalidProjectConfigException(CONFIG_FIELD, "$CONFIG_FIELD exceeds the $MAX_BYTES byte limit")
            }
            return Files.readAllBytes(output).toString(Charsets.UTF_8)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private companion object {
        const val CONFIG_PATH = ".shipfloor/factory.yml"
        const val CONFIG_FIELD = "factory.yml"
        const val MAX_BYTES = 64 * 1024
        const val TIMEOUT_SECONDS = 10L
    }
}
