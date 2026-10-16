package sfv2.daemon

import java.io.IOException
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
        val process = try {
            ProcessBuilder(
                "git", "-C", repo.toString(), "-c", "core.fsmonitor=false", "show", "main:$CONFIG_PATH",
            ).redirectErrorStream(false)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .also { it.environment()["GIT_TERMINAL_PROMPT"] = "0" }
                .start()
        } catch (e: IOException) {
            throw InvalidProjectConfigException(CONFIG_FIELD, "cannot run git to read $CONFIG_FIELD")
        }
        try {
            process.outputStream.close()
            val bytes = process.inputStream.readNBytes(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) {
                throw InvalidProjectConfigException(CONFIG_FIELD, "$CONFIG_FIELD exceeds the $MAX_BYTES byte limit")
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw InvalidProjectConfigException(CONFIG_FIELD, "reading $CONFIG_FIELD timed out")
            }
            return if (process.exitValue() == 0) bytes.toString(Charsets.UTF_8) else null
        } finally {
            process.destroyForcibly()
        }
    }

    private companion object {
        const val CONFIG_PATH = ".shipfloor/factory.yml"
        const val CONFIG_FIELD = "factory.yml"
        const val MAX_BYTES = 64 * 1024
        const val TIMEOUT_SECONDS = 10L
    }
}
