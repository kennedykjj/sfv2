package sfv2.ports

// shipfloor:qa-stub — compile-only stub so the red gate compiles; the implementer deletes it
// when creating the real FakeTracker.kt in src/main (T2).

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
