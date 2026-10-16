package sfv2.daemon

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** The git operations the worktree manager needs; the fake in the tests stands in for a real repository. */
interface WorktreeGit {
    /** Adds a worktree at [dir] on [branch], reusing the branch from `origin` when it exists, else from `origin/main`. */
    fun addWorktree(repo: Path, dir: Path, branch: String)

    fun hasWorktree(repo: Path, dir: Path): Boolean

    fun isDirty(dir: Path): Boolean

    /** Removes the registered worktree at [dir]; refuses a directory git does not know and never forces. */
    fun removeWorktree(repo: Path, dir: Path)

    fun deleteBranch(repo: Path, branch: String)
}

/** Runs `git -C` without a shell, with a timeout and without any prompt. */
class SystemWorktreeGit(private val timeoutSeconds: Long = TIMEOUT_SECONDS) : WorktreeGit {

    override fun addWorktree(repo: Path, dir: Path, branch: String) {
        val args = when {
            succeeds(repo, "rev-parse", "--verify", "--quiet", "refs/heads/$branch") ->
                listOf("worktree", "add", dir.toString(), branch)
            succeeds(repo, "rev-parse", "--verify", "--quiet", "refs/remotes/origin/$branch") ->
                listOf("worktree", "add", "-b", branch, dir.toString(), "origin/$branch")
            else -> listOf("worktree", "add", "-b", branch, dir.toString(), "origin/main")
        }
        run(repo, *args.toTypedArray())
    }

    override fun hasWorktree(repo: Path, dir: Path): Boolean {
        val listed = run(repo, "worktree", "list", "--porcelain").lines()
        val wanted = dir.toAbsolutePath().normalize()
        return listed.any { line ->
            line.startsWith("worktree ") && Path.of(line.removePrefix("worktree ")).normalize() == wanted
        }
    }

    override fun isDirty(dir: Path): Boolean = run(dir, "status", "--porcelain").isNotBlank()

    override fun removeWorktree(repo: Path, dir: Path) {
        run(repo, "worktree", "remove", dir.toString())
    }

    override fun deleteBranch(repo: Path, branch: String) {
        run(repo, "branch", "-D", branch)
    }

    private fun succeeds(repo: Path, vararg args: String): Boolean = exec(repo, *args).first == 0

    private fun run(repo: Path, vararg args: String): String {
        val (code, output) = exec(repo, *args)
        if (code != 0) throw WorktreeException("git ${args.first()} failed with exit code $code")
        return output
    }

    private fun exec(repo: Path, vararg args: String): Pair<Int, String> {
        // Output goes to a file so the wait bounds the whole run: a pipe read would block until EOF.
        val output = Files.createTempFile("sfv2-worktree", ".txt")
        try {
            val process = try {
                ProcessBuilder(listOf("git", "-C", repo.toString(), "-c", "core.fsmonitor=false") + args)
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .also { it.environment()["GIT_TERMINAL_PROMPT"] = "0" }
                    .start()
            } catch (e: IOException) {
                throw WorktreeException("cannot run git ${args.first()}")
            }
            try {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw WorktreeException("git ${args.first()} timed out")
                }
            } finally {
                process.destroyForcibly()
            }
            return process.exitValue() to Files.readAllBytes(output).toString(Charsets.UTF_8)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
    }
}
