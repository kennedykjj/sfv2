package sfv2.daemon

// shipfloor:qa-stub — only so the acceptance tests compile; the implementer deletes this file and writes the real CostBudget.kt and Dispatcher parameter.

import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import sfv2.store.CardEventStore

fun interface LaunchGuard {
    fun allows(cardId: String): Boolean

    companion object {
        val Open: LaunchGuard = LaunchGuard { true }
    }
}

data class CostLimits(val perCardUsd: BigDecimal?, val perDayUsd: BigDecimal?) {
    companion object {
        val NONE = CostLimits(null, null)
    }
}

class CostBudget(
    store: CardEventStore,
    recorder: EventRecorder,
    limits: CostLimits,
    clock: Clock,
    zone: ZoneId,
    log: DaemonLog,
) : LaunchGuard {
    fun onRunFinished(cardId: String) {}

    fun isBlocked(cardId: String): Boolean = false

    fun dayLimitReached(): Boolean = false

    override fun allows(cardId: String): Boolean = true
}

/** Stands in for the Dispatcher constructor that takes launchGuard; ignores it. */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun Dispatcher(
    store: CardEventStore,
    pool: RunPool,
    launcher: StageLauncher,
    pause: PauseSwitch,
    schedule: WorkSchedule,
    limits: DispatchLimits,
    clock: Clock,
    tick: Duration,
    log: DaemonLog,
    launchGuard: LaunchGuard,
): Dispatcher = Dispatcher(store, pool, launcher, pause, schedule, limits, clock, tick, log)
