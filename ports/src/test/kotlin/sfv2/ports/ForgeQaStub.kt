package sfv2.ports

// shipfloor:qa-stub - compile-only stub so the red gate fails by assertion; the implementer deletes this file.

interface Forge {
    fun openPullRequest(request: PullRequestRequest): PullRequestRef

    fun checks(head: String): ChecksState

    fun merge(pr: PullRequestRef, expectedHead: String): MergeResult

    fun findOpenPullRequest(branch: String): PullRequestRef?

    fun state(pr: PullRequestRef): PullRequestState
}

data class PullRequestRequest(val branch: String, val base: String, val title: String, val body: String)

data class PullRequestRef(val number: Int, val branch: String, val head: String)

data class FailedCheck(val name: String, val summary: String)

sealed interface ChecksState {
    data object Pending : ChecksState

    data object Passed : ChecksState

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

    data object Conflict : MergeResult

    data class NotOpen(val state: PullRequestState) : MergeResult
}

class ForgeException(message: String) : RuntimeException(message)

class UnknownPullRequestException(message: String) : RuntimeException(message)

class FakeForge : Forge {
    val opened: List<PullRequestRequest> = emptyList()
    val merges: List<PullRequestRef> = emptyList()

    fun scriptChecks(head: String, state: ChecksState) {}

    fun mergedOutside(pr: PullRequestRef) {}

    fun closeOutside(pr: PullRequestRef) {}

    fun pushHead(pr: PullRequestRef, head: String) {}

    fun scriptConflict(pr: PullRequestRef) {}

    fun failNext(operation: String) {}

    override fun openPullRequest(request: PullRequestRequest) = PullRequestRef(0, "", "")

    override fun checks(head: String): ChecksState = ChecksState.Passed

    override fun merge(pr: PullRequestRef, expectedHead: String): MergeResult = MergeResult.Conflict

    override fun findOpenPullRequest(branch: String): PullRequestRef? = PullRequestRef(0, "", "")

    override fun state(pr: PullRequestRef): PullRequestState = PullRequestState.Merged
}
