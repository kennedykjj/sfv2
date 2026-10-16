package sfv2.daemon

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import sfv2.adapters.GhForge
import sfv2.ports.Forge
import sfv2.ports.ForgeException
import sfv2.ports.MergeResult
import sfv2.ports.MergedPullRequest
import sfv2.ports.PullRequestRef
import sfv2.ports.PullRequestRequest
import sfv2.ports.PullRequestState
import sfv2.ports.ChecksState

/** Builds the production [PullRequestDelivery] of a project: GitHub through `gh`, the push through plain git. */
object ProjectDelivery {
    private val GH_TIMEOUT: Duration = Duration.ofSeconds(60)
    private val GITHUB_REMOTE = Regex("""github\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?/?""")

    fun create(project: Project, tmpDir: Path): PullRequestDelivery {
        return PullRequestDelivery(forgeOf(project, tmpDir), SystemBranchPublisher(), GitWorktreeHead(), ParkingCiPause())
    }

    /** The project's [Forge]: GitHub through `gh` when origin is on GitHub, otherwise one that fails every call. */
    fun forgeOf(project: Project, tmpDir: Path): Forge {
        val repo = Path.of(project.repo)
        val slug = originSlug(repo) ?: return UnavailableForge
        Files.createDirectories(tmpDir)
        return GhForge(repo = slug, workDir = repo, tmpDir = tmpDir, timeout = GH_TIMEOUT)
    }

    private fun originSlug(repo: Path): String? {
        val output = Files.createTempFile("sfv2-origin", ".txt")
        try {
            val process = try {
                ProcessBuilder("git", "-C", repo.toString(), "config", "--get", "remote.origin.url")
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: IOException) {
                return null
            }
            try {
                if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return null
            } finally {
                process.destroyForcibly()
            }
            return GITHUB_REMOTE.find(Files.readString(output).trim())?.groupValues?.get(1)
        } finally {
            Files.deleteIfExists(output)
        }
    }
}

/** Stands in when the project has no GitHub origin: every call fails, so the card is blocked and never staged. */
private object UnavailableForge : Forge {
    private fun unavailable(operation: String): Nothing = throw ForgeException("$operation: project has no GitHub origin")

    override fun openPullRequest(request: PullRequestRequest): PullRequestRef = unavailable("openPullRequest")

    override fun checks(head: String): ChecksState = unavailable("checks")

    override fun merge(pr: PullRequestRef, expectedHead: String): MergeResult = unavailable("merge")

    override fun findOpenPullRequest(branch: String): PullRequestRef? = unavailable("findOpenPullRequest")

    override fun state(pr: PullRequestRef): PullRequestState = unavailable("state")

    override fun rerunChecks(head: String) = unavailable("rerunChecks")

    override fun revert(pr: PullRequestRef): PullRequestRef = unavailable("revert")

    override fun findMergedPullRequest(branch: String): MergedPullRequest? = unavailable("findMergedPullRequest")
}
