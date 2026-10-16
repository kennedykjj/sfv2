package sfv2.app

import java.math.BigDecimal
import java.time.Instant

// Temporary signatures so the acceptance tests compile and fail by assertion; the engineer replaces them.

/** One gate line of the Summary tab: verdict as text, duration text and the failure reason. */
data class GateLine(val name: String, val verdict: String, val duration: String, val reason: String?)

fun gateLine(gate: GateRow): GateLine = GateLine("", "", "", null)

enum class PrState { OPEN, MERGED }

data class PullRequestView(val number: Int, val state: PrState)

fun cardPullRequest(events: List<TimelineEvent>): PullRequestView? = null

data class OpenBlock(val reason: String, val at: Instant)

fun openBlock(events: List<TimelineEvent>): OpenBlock? = null

/** Runs of one stage with the stage subtotal; [status] is PARTIAL when only some runs have a known cost. */
data class StageRunCost(val stage: String, val status: CostStatus, val costUsd: BigDecimal?, val runs: List<RunRow>)

fun costByStage(runs: List<RunRow>): List<StageRunCost> = emptyList()

fun criterionResult(criterion: CriterionRow): String = ""
