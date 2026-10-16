package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class SystemWorktreeGitTest {

    @TempDir
    lateinit var tmp: Path

    private lateinit var repo: Path
    private val git = SystemWorktreeGit()

    private fun sh(dir: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", "-C", dir.toString()) + args).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor() == 0) { "git ${args.toList()} failed: $output" }
        return output
    }

    @BeforeEach
    fun initRepo() {
        repo = Files.createDirectory(tmp.resolve("repo"))
        sh(repo, "init", "-q", "-b", "main")
        sh(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "init")
        sh(repo, "update-ref", "refs/remotes/origin/main", "HEAD")
    }

    @Test
    fun `adds, detects, removes a worktree and deletes its branch`() {
        val dir = tmp.resolve("wt").resolve("c1")

        git.addWorktree(repo, dir, "feat/c1-x")

        assertThat(git.hasWorktree(repo, dir)).isTrue()
        assertThat(git.isDirty(dir)).isFalse()
        assertThat(sh(repo, "branch", "--list", "feat/c1-x")).contains("feat/c1-x")

        git.removeWorktree(repo, dir)
        git.deleteBranch(repo, "feat/c1-x")

        assertThat(git.hasWorktree(repo, dir)).isFalse()
        assertThat(sh(repo, "branch", "--list", "feat/c1-x")).isBlank()
    }

    @Test
    fun `a worktree with an untracked file is dirty and is not removed`() {
        val dir = tmp.resolve("wt").resolve("c1")
        git.addWorktree(repo, dir, "feat/c1-x")
        Files.writeString(dir.resolve("work.txt"), "unsaved")

        assertThat(git.isDirty(dir)).isTrue()
        assertThrows<WorktreeException> { git.removeWorktree(repo, dir) }
        assertThat(Files.exists(dir.resolve("work.txt"))).isTrue()
    }
}
