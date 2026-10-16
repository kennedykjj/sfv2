package sfv2.ports

// shipfloor:qa-stub - lets the acceptance tests compile; the implementer deletes this file and defines the type in Forge.kt.
data class PullRequestSummary(
    val number: Int,
    val branch: String,
    val title: String,
    val state: PullRequestState,
    val author: String,
    val fromFork: Boolean,
)
