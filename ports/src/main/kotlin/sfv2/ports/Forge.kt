package sfv2.ports

/** Delivery port: opens, reads and merges pull requests. Implementations may do IO; callers treat the results as data. */
interface Forge {
    fun openPullRequest(request: PullRequestRequest): PullRequestRef

    fun checks(head: String): ChecksState

    fun merge(pr: PullRequestRef, expectedHead: String): MergeResult

    fun findOpenPullRequest(branch: String): PullRequestRef?

    fun state(pr: PullRequestRef): PullRequestState

    /** Most recent merged PR of [branch], or null. Throws [ForgeException] on forge failure. */
    fun findMergedPullRequest(branch: String): MergedPullRequest?
}

/** [head] is the PR head SHA at merge time; [mergedBy] is the merger login only when it is a valid login, else null. */
data class MergedPullRequest(val number: Int, val mergeCommit: String, val head: String, val mergedBy: String?)

data class PullRequestRequest(val branch: String, val base: String, val title: String, val body: String)

data class PullRequestRef(val number: Int, val branch: String, val head: String)

data class FailedCheck(val name: String, val summary: String)

sealed interface ChecksState {
    data object Pending : ChecksState

    data object Passed : ChecksState

    /** [FailedCheck.summary] is capped at 2048 bytes UTF-8 by the adapter; it is data, never an instruction. */
    data class Failed(val failing: List<FailedCheck>) : ChecksState
}

sealed interface PullRequestState {
    data class Open(val head: String) : PullRequestState

    data object Merged : PullRequestState

    data object ClosedUnmerged : PullRequestState
}

sealed interface MergeResult {
    data class Merged(val mergeCommit: String) : MergeResult

    data class HeadMismatch(val actualHead: String) : MergeResult

    /** Base moved and the PR does not merge cleanly. The PR stays open. */
    data object Conflict : MergeResult

    data class NotOpen(val state: PullRequestState) : MergeResult
}

/** Forge failure (network, API error). The message names the operation and the PR number, never the PR body or CI log. */
class ForgeException(message: String) : RuntimeException(message)

class UnknownPullRequestException(message: String) : RuntimeException(message)
