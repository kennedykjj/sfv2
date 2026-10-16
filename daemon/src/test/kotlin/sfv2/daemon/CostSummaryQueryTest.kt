package sfv2.daemon

import java.math.BigDecimal
import java.nio.file.Path
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore
import sfv2.store.Database

class CostSummaryQueryTest {

    private val from = Instant.parse("2026-10-16T00:00:00Z")
    private val to = Instant.parse("2026-10-17T00:00:00Z")
    private val at = Instant.parse("2026-10-16T10:00:00Z")

    private fun summary(dir: Path, limits: CostLimits, record: (CardEventStore) -> Unit): Response.CostSummaryResult {
        Database.open(dir.resolve("q.db")).use { db ->
            val store = CardEventStore(db)
            record(store)
            return CostSummaryQuery(store, limits).handle(Request.CostSummary(from, to)) as Response.CostSummaryResult
        }
    }

    private fun CardEventStore.cost(card: String, id: String, payload: Map<String, String>) =
        recordChange(
            card,
            Card.create(card, Column.CODING),
            Event(EventId(id), at, Actor.Factory("cost"), EventType.RUN_COST_RECORDED, payload + ("run_id" to id)),
        )

    @Test
    fun `no recorded cost is NONE with a zero total and nothing reached`(@TempDir dir: Path) {
        val result = summary(dir, CostLimits(BigDecimal("1"), BigDecimal("1"))) {}

        assertThat(result.status).isEqualTo(CostStatus.NONE)
        assertThat(result.totalUsd).isEqualByComparingTo("0")
        assertThat(result.cards).isEmpty()
        assertThat(result.dayReached).isFalse()
        assertThat(result.cardsReached).isEmpty()
    }

    @Test
    fun `known and unknown runs in one period make it PARTIAL`(@TempDir dir: Path) {
        val result = summary(dir, CostLimits.NONE) {
            it.cost("a", "e1", mapOf("status" to "KNOWN", "cost_usd" to "1.50"))
            it.cost("b", "e2", mapOf("status" to "UNKNOWN", "reason" to "transcript unreadable"))
        }

        assertThat(result.status).isEqualTo(CostStatus.PARTIAL)
        assertThat(result.totalUsd).isEqualByComparingTo("1.50")
        assertThat(result.cards.associate { it.card to it.status })
            .containsEntry("a", CostStatus.KNOWN).containsEntry("b", CostStatus.UNKNOWN)
    }

    @Test
    fun `a model with no price is UNKNOWN and reaches both limits`(@TempDir dir: Path) {
        val result = summary(dir, CostLimits(BigDecimal("100"), BigDecimal("100"))) {
            it.cost("a", "e1", mapOf("status" to "UNKNOWN", "reason" to "no price for models: x"))
        }

        assertThat(result.status).isEqualTo(CostStatus.UNKNOWN)
        assertThat(result.totalUsd).isEqualByComparingTo("0")
        assertThat(result.dayReached).isTrue()
        assertThat(result.cardsReached).containsExactly("a")
    }
}
