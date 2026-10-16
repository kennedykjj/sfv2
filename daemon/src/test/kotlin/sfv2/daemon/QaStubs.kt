package sfv2.daemon

import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import sfv2.store.CardEventStore
import sfv2.store.Database

// shipfloor:qa-stub
// Test-only stand-ins so the acceptance tests of #159 compile before the implementation exists. They behave as the
// code does today (nothing configured, extra arguments ignored), so the tests fail by assertion. The implementer
// deletes this file: the real members win over these extensions and factory function once they exist.

class InvalidDispatcherSettingsException(val field: String, message: String) : RuntimeException(message)

data class DispatcherSettings(
    val enabled: Boolean,
    val limits: CostLimits,
    val unlimited: Boolean,
    val schedule: WorkSchedule,
    val zone: ZoneId,
) {
    companion object {
        fun resolve(factoryYaml: Map<String, Any?>?, env: Map<String, String>): DispatcherSettings =
            DispatcherSettings(false, CostLimits(BigDecimal.ONE, BigDecimal.ONE), false, AlwaysWorkTime, ZoneOffset.UTC)
    }
}

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
