package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import sfv2.ports.Runtime
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets the acceptance tests compile before the real RefinePipeline.kt exists; the implementer deletes this file.

data class GateResult(val name: String, val passed: Boolean, val reason: String?)

fun interface RefineGates {
    fun evaluate(cardId: String, worktree: Path): List<GateResult>
}

@Suppress("unused", "LongParameterList")
class RefinePipeline(
    project: Project,
    store: CardEventStore,
    recorder: EventRecorder,
    worktrees: WorktreeManager,
    sessions: McpSessions,
    mcpPort: Int,
    runtime: Runtime,
    costRecorder: RunCostRecorder,
    gates: RefineGates,
    configDir: Path,
    clock: Clock,
    model: String,
    maxRounds: Int = 3,
    log: DaemonLog = StderrDaemonLog,
) {
    fun run(cardId: String) {
        throw NotImplementedError("qa stub: RefinePipeline.run($cardId)")
    }
}

@Suppress("unused")
class RefineStageLauncher(pipelineFor: (String) -> RefinePipeline, pool: RunPool) : StageLauncher {
    override fun launch(stage: DispatchStage, cardId: String): LaunchedRun {
        throw NotImplementedError("qa stub: RefineStageLauncher.launch($stage, $cardId)")
    }
}
