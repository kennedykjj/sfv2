package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import sfv2.store.CardEventStore
import sfv2.store.Database

// shipfloor:qa-stub
// Test-only stand-ins so the acceptance tests of #159 compile before the implementation exists. They behave as the
// code does today (nothing configured, extra arguments ignored), so the tests fail by assertion. The implementer
// deletes this file: the real members win over these extensions and factory function once they exist.

@Suppress("LongParameterList", "UNUSED_PARAMETER")
fun RefineRuntime.Companion.start(
    dataDir: Path,
    store: CardEventStore,
    commands: CardCommands,
    registry: ProjectRegistry,
    files: ProjectFileSource,
    database: Database,
    clock: Clock,
    source: ProjectConfigSource,
    env: Map<String, String>,
    log: DaemonLog,
): RefineRuntime = start(dataDir, store, commands, registry, files, database, clock)

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun RefineStageLauncher(
    pipelineFor: (String) -> RefinePipeline,
    pool: RunPool,
    onRunFinished: (String) -> Unit,
): RefineStageLauncher = RefineStageLauncher(pipelineFor, pool)
