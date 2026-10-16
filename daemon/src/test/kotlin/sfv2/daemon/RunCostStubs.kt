package sfv2.daemon

// shipfloor:qa-stub — only so the red gate compiles; the implementer deletes this file and writes the real classes.

import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import sfv2.ports.SessionResult
import sfv2.store.CardEventStore

data class TokenCounts(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite5m: Long = 0,
    val cacheWrite1h: Long = 0,
)

class PriceTable {
    companion object {
        fun parse(yaml: String): PriceTable = PriceTable()
    }
}

sealed interface RunCost {
    data class Known(val usd: BigDecimal) : RunCost
    data class Partial(val usd: BigDecimal, val unpricedModels: List<String>) : RunCost
    data class Unknown(val reason: String) : RunCost
}

object RunCostCalculator {
    fun cost(usage: Map<String, TokenCounts>, prices: PriceTable): RunCost = RunCost.Unknown("stub")
}

class RunCostRecorder(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val prices: PriceTable,
    private val clock: Clock,
) {
    fun record(cardId: String, runId: String, runtime: String, stage: String, model: String, result: SessionResult) {
        // stub: records nothing
    }
}
