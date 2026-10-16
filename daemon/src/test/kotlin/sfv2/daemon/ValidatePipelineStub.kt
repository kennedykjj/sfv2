package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import sfv2.ports.Runtime
import sfv2.store.CardEventStore

// shipfloor:qa-stub — only so ValidatePipelineTest compiles and fails by assertion; the implementer deletes this file
// and writes the real types in main (BranchSync in WorktreeGit.kt, the rest in ValidatePipeline.kt).

sealed interface MergeResult {
    data object Merged : MergeResult

    data object Conflict : MergeResult

    data object Failed : MergeResult
}

fun interface BranchSync {
    fun mergeMain(worktree: Path): MergeResult
}

fun interface ValidateGates {
    fun evaluate(cardId: String, worktree: Path): List<GateResult>
}

@Suppress("LongParameterList", "UnusedPrivateProperty")
class ValidatePipeline(
    private val project: Project,
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val worktrees: WorktreeManager,
    private val sessions: McpSessions,
    private val mcpPort: Int,
    private val runtime: Runtime,
    private val costRecorder: RunCostRecorder,
    private val branchSync: BranchSync,
    private val gates: ValidateGates,
    private val configDir: Path,
    private val clock: Clock,
    private val model: String,
    private val maxRounds: Int = 3,
    private val log: DaemonLog = StderrDaemonLog,
    private val runtimeName: String = "claude",
) {
    fun run(cardId: String) = Unit
}

@Suppress("UnusedPrivateProperty")
class ValidateStageLauncher(
    private val pipelineFor: (String) -> ValidatePipeline,
    private val pool: RunPool,
    private val log: DaemonLog = StderrDaemonLog,
    private val onRunFinished: (String) -> Unit = {},
) : StageLauncher {
    override fun launch(stage: DispatchStage, cardId: String): LaunchedRun = LaunchedRun(0L, Instant.EPOCH)
}
