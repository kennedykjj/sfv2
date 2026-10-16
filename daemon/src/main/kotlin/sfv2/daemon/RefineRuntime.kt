package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import sfv2.adapters.ClaudeRuntime
import sfv2.store.CardEventStore
import sfv2.store.Database
import sfv2.store.SpecStore

/** The running parts of the refine stage: the dispatcher restricted to REFINE and the MCP endpoint its agents use. */
class RefineRuntime private constructor(
    val dispatcher: Dispatcher,
    private val endpoint: McpEndpoint,
) : AutoCloseable {

    override fun close() {
        dispatcher.close()
        endpoint.close()
    }

    companion object {
        private const val POOL_CAPACITY = 2
        private const val MODEL = "claude-sonnet-5-5"
        private val TICK: Duration = Duration.ofSeconds(30)
        private val SESSION_TIMEOUT: Duration = Duration.ofMinutes(30)

        /** Wires dispatcher, launcher, pipeline and MCP endpoint; the dispatcher is returned stopped. */
        @Suppress("LongParameterList")
        fun start(
            dataDir: Path,
            store: CardEventStore,
            commands: CardCommands,
            registry: ProjectRegistry,
            files: ProjectFileSource,
            database: Database,
            clock: Clock,
        ): RefineRuntime {
            val recorder = commands.recorder
            val worktrees = WorktreeManager(dataDir.resolve("worktrees"), SystemWorktreeGit())
            val sessions = McpSessions(clock)
            val endpoint = McpServerFactory(
                sessions, store, SpecStore(database, dataDir.resolve("specs")), registry, files, recorder, worktrees, clock,
            ).start()
            val runtime = ClaudeRuntime(transcriptDir = dataDir.resolve("transcripts"), timeout = SESSION_TIMEOUT, clock = clock)
            val costRecorder = RunCostRecorder(store, recorder, PriceTable.parse("models: {}"), clock)
            val pool = RunPool(POOL_CAPACITY, store, clock, SystemProcessProbe, recorder = recorder)
            val changedPaths = GitChangedPaths()
            val launcher = RefineStageLauncher(
                pipelineFor = {
                    val project = checkNotNull(registry.list().singleOrNull()) { "refine needs exactly one registered project" }
                    RefinePipeline(
                        project = project,
                        store = store,
                        recorder = recorder,
                        worktrees = worktrees,
                        sessions = sessions,
                        mcpPort = endpoint.port,
                        runtime = runtime,
                        costRecorder = costRecorder,
                        gates = SpecScopeRefineGates(project, store, recorder, changedPaths, clock),
                        configDir = dataDir.resolve("mcp"),
                        clock = clock,
                        model = MODEL,
                    )
                },
                pool = pool,
            )
            val dispatcher = Dispatcher(
                store = store,
                pool = pool,
                launcher = launcher,
                pause = FilePauseSwitch(dataDir.resolve("PAUSE")),
                schedule = AlwaysWorkTime,
                limits = DispatchLimits(emptyMap()),
                clock = clock,
                tick = TICK,
                stages = setOf(DispatchStage.REFINE),
            )
            return RefineRuntime(dispatcher, endpoint)
        }
    }
}
