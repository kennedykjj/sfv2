package sfv2.daemon

// shipfloor:qa-stub - lets DispatcherTest compile before the production code exists; the implementer deletes this file.

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import sfv2.core.Column
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

enum class DispatchStage(val column: Column) {
    REFINE(Column.READY_FOR_REFINEMENT),
    DEVELOP(Column.READY_FOR_DEV),
    VALIDATE(Column.VALIDATION),
}

data class LaunchedRun(val pid: Long, val startedAt: Instant)

fun interface StageLauncher {
    fun launch(stage: DispatchStage, cardId: String): LaunchedRun
}

fun interface PauseSwitch {
    fun isPaused(): Boolean
}

class FilePauseSwitch(private val path: Path) : PauseSwitch {
    override fun isPaused(): Boolean = false
}

fun interface WorkSchedule {
    fun isWorkTime(now: Instant): Boolean
}

object AlwaysWorkTime : WorkSchedule {
    override fun isWorkTime(now: Instant): Boolean = true
}

data class DispatchLimits(val wip: Map<DispatchStage, Int>)

data class DispatchedRun(val stage: DispatchStage, val cardId: String)

@Suppress("UNUSED_PARAMETER")
class Dispatcher(
    store: CardEventStore,
    pool: RunPool,
    launcher: StageLauncher,
    pause: PauseSwitch,
    schedule: WorkSchedule,
    limits: DispatchLimits,
    clock: Clock,
    tick: Duration,
    log: DaemonLog = StderrDaemonLog,
) : AutoCloseable {
    fun runCycle(): List<DispatchedRun> = emptyList()
    fun wake() {}
    fun start() {}
    override fun close() {}
    fun onEvent(event: StoredEvent) {}
}

fun RunPool.isLocked(cardId: String): Boolean = false

fun RunPool.hasRoom(): Boolean = false

@Suppress("UNUSED_PARAMETER")
fun EventStream.addListener(listener: (StoredEvent) -> Unit) {}
