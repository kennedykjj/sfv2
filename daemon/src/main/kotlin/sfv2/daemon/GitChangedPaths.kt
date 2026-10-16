package sfv2.daemon

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** The worktree's changed files against [base] could not be listed. */
class ChangedPathsException(message: String) : RuntimeException(message)

interface ChangedPaths {
    fun between(worktree: Path, base: String): List<String>
}

/** Lists the changed paths with `git diff --name-only --no-renames`, so a rename yields origin and destination. */
class GitChangedPaths(private val timeoutSeconds: Long = 30) : ChangedPaths {

    override fun between(worktree: Path, base: String): List<String> {
        if (base.isBlank() || base.startsWith("-")) {
            throw ChangedPathsException("invalid base revision: $base")
        }
        val output = Files.createTempFile("changed-paths", ".out")
        try {
            val process = ProcessBuilder("git", "-C", worktree.toString(), "diff", "--name-only", "--no-renames", "-z", base, "--")
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw ChangedPathsException("git diff timed out after ${timeoutSeconds}s in $worktree")
            }
            if (process.exitValue() != 0) {
                throw ChangedPathsException("git diff failed with exit ${process.exitValue()} in $worktree")
            }
            if (Files.size(output) > MAX_BYTES) {
                throw ChangedPathsException("git diff output exceeds $MAX_BYTES bytes in $worktree")
            }
            return String(Files.readAllBytes(output), StandardCharsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024L
    }
}
