package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import sfv2.ports.Forge
import sfv2.ports.Runtime
import sfv2.store.CardEventStore

// shipfloor:qa-stub — only so the red gate of #45 compiles; the implementer deletes this file and writes the real types.

sealed interface CiResult {
    data object Green : CiResult

    data class Red(val summary: String) : CiResult

    data object TimedOut : CiResult

    data object ForgeFailed : CiResult
}

sealed interface PushResult {
    data object Pushed : PushResult

    data object Refused : PushResult

    data object Failed : PushResult
}

fun interface BranchPublisher {
    fun push(worktree: Path, branch: String): PushResult
}

fun interface WorktreeHead {
    fun of(worktree: Path): String
}

fun interface CiPause {
    fun await()
}

@Suppress("UNUSED_PARAMETER")
class PullRequestDelivery(
    forge: Forge,
    publisher: BranchPublisher,
    head: WorktreeHead,
    pause: CiPause,
    maxPolls: Int = 40,
    base: String = "main",
)

class SystemBranchPublisher : BranchPublisher {
    override fun push(worktree: Path, branch: String): PushResult = PushResult.Failed
}

/** Stub: ignores [ci]. */
@Suppress("UNUSED_PARAMETER")
fun ValidationOutcome.apply(cardId: String, report: String, gates: GatesState, ci: () -> CiResult): ValidationDecision =
    apply(cardId, report, gates)

/** Stub: the real constructor gains `delivery`; this ignores it. */
@Suppress("LongParameterList", "FunctionNaming", "UNUSED_PARAMETER")
fun ValidatePipeline(
    project: Project,
    store: CardEventStore,
    recorder: EventRecorder,
    worktrees: WorktreeManager,
    sessions: McpSessions,
    mcpPort: Int,
    runtime: Runtime,
    costRecorder: RunCostRecorder,
    branchSync: BranchSync,
    gates: ValidateGates,
    configDir: Path,
    clock: Clock,
    model: String,
    maxRounds: Int,
    log: DaemonLog,
    delivery: PullRequestDelivery?,
): ValidatePipeline = ValidatePipeline(
    project, store, recorder, worktrees, sessions, mcpPort, runtime, costRecorder, branchSync, gates, configDir, clock, model,
    maxRounds, log,
)
