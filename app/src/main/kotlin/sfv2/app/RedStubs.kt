package sfv2.app

import java.time.Instant

// Temporary red-gate stubs: minimal signatures with neutral bodies so the acceptance tests compile and fail by assertion.

data class GateRow(val name: String, val verdict: String, val reason: String?, val durationMs: Long?, val at: Instant)

data class CriterionRow(val ac: String, val layer: String, val testFile: String, val gate: String)

val CardDetail.gates: List<GateRow> get() = emptyList()

val CardDetail.criteria: List<CriterionRow> get() = emptyList()
