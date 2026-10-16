package sfv2.app

// Red-step stubs: only signatures with neutral bodies so the acceptance tests compile and fail by assertion.
// The engineer replaces these with real members of CostSummaryView and empties this file.

/** Neutral stand-in for `CostSummaryView.stages`: the "Por estágio" lines, largest first. */
val CostSummaryView.stages: List<CostLine> get() = emptyList()

/** Neutral stand-in for `CostSummaryView.days`: the "Por dia" lines, chronological; empty for a single day. */
val CostSummaryView.days: List<CostLine> get() = emptyList()
