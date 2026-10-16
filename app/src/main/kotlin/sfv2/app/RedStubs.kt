package sfv2.app

// Temporary red-gate stubs: minimal signatures with neutral bodies. The engineer replaces them with real members
// of FeedRow, FeedState and EventFeedClient (members win over these extensions) and empties this file.

/** `stage == null` is the "Todos" chip. */
data class StageChip(val stage: String?, val count: Int)

val FeedRow.actorKind: String get() = ""
val FeedRow.actorDetail: String? get() = null
val FeedRow.cost: String? get() = null
val FeedRow.costTag: CostTag? get() = null
val FeedRow.stage: String? get() = null

val FeedState.stages: List<StageChip> get() = emptyList()
val FeedState.stage: String? get() = null

fun EventFeedClient.selectStage(stage: String?) = Unit
