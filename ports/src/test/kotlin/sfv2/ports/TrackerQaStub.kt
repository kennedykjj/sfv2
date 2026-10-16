package sfv2.ports

// shipfloor:qa-stub — compile-only stub so the red gate compiles; the implementer deletes it
// when creating the real Tracker.kt and FakeTracker.kt in src/main.

interface Tracker {
    fun fetch(id: String): TrackerItem?
    fun search(query: String): List<TrackerItem>
    fun writeMilestone(id: String, milestone: String)
    fun close(id: String, comment: String): CloseResult
}

sealed interface CloseResult {
    data object Closed : CloseResult
    data object AlreadyClosed : CloseResult
}

open class TrackerException(message: String) : RuntimeException(message)

class UnknownTrackerItemException(message: String) : TrackerException(message)

enum class TrackerItemState { OPEN, CLOSED }

@JvmInline
value class ExternalText(val value: String)

data class TrackerItem(
    val id: String,
    val title: ExternalText,
    val body: ExternalText,
    val labels: List<ExternalText>,
    val url: String,
    val state: TrackerItemState,
    val textHash: String,
) {
    companion object {
        fun of(
            id: String,
            title: ExternalText,
            body: ExternalText,
            labels: List<ExternalText>,
            url: String,
            state: TrackerItemState,
        ): TrackerItem = TrackerItem(id, title, body, labels, url, state, "")
    }
}

class FakeTracker : Tracker {
    val calls: MutableMap<String, Int> = mutableMapOf()
    val milestones: MutableMap<String, MutableList<String>> = mutableMapOf()
    val closures: MutableMap<String, String> = mutableMapOf()

    fun seed(item: TrackerItem) {}

    fun failNext(operation: String) {}

    override fun fetch(id: String): TrackerItem? = null

    override fun search(query: String): List<TrackerItem> = emptyList()

    override fun writeMilestone(id: String, milestone: String) {}

    override fun close(id: String, comment: String): CloseResult = CloseResult.AlreadyClosed
}
