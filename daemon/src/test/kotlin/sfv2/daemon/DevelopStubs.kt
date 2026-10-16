package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import sfv2.ports.Runtime
import sfv2.store.CardEventStore

// shipfloor:qa-stub - only so the red gate compiles; the implementer deletes this file when the real
// DevelopGates, DevelopPipeline and DevelopStageLauncher exist in src/main (spec "Pipeline de develop", Architecture).

interface DevelopGates {
    fun red(cardId: String, worktree: Path): List<GateResult>

    fun green(cardId: String, layer: ProjectLayer, worktree: Path): List<GateResult>
}

@Suppress("LongParameterList", "UNUSED_PARAMETER")
class DevelopPipeline(
    project: Project,
    store: CardEventStore,
    recorder: EventRecorder,
    worktrees: WorktreeManager,
    sessions: McpSessions,
    mcpPort: Int,
    runtime: Runtime,
    costRecorder: RunCostRecorder,
    gates: DevelopGates,
    modelStepUp: ModelStepUp,
    configDir: Path,
    clock: Clock,
    maxRounds: Int = 3,
    log: DaemonLog = StderrDaemonLog,
) {
    fun run(cardId: String) = Unit
}

@Suppress("UNUSED_PARAMETER")
class DevelopStageLauncher(
    pipelineFor: (String) -> DevelopPipeline,
    pool: RunPool,
    log: DaemonLog = StderrDaemonLog,
    onRunFinished: (String) -> Unit = {},
) : StageLauncher {
    override fun launch(stage: DispatchStage, cardId: String): LaunchedRun =
        LaunchedRun(ProcessHandle.current().pid(), Instant.now())
}
