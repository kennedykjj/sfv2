package sfv2.app

import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sfv2.core.AssistantState
import sfv2.core.Column
import sfv2.core.EventType

/**
 * Shape the implementation must follow (pure, in `BoardState.kt` and `BoardSource.kt`):
 *
 * - `BoardCard(id: String, title: String, column: Column)`; `Board(cards: List<BoardCard>, lastSeq: Long?)`.
 * - `BoardEvent(seq: Long, cardId: String, type: EventType, payload: Map<String, String>)`.
 * - `Board.applySnapshot(cards): Board`, `Board.cardsIn(column): List<BoardCard>` (insertion order).
 * - `Board.applyEvent(event: BoardEvent, fetched: BoardCard? = null): Board`: for `CARD_CREATED`, `fetched` is the
 *   card as `BoardSource.fetchCard` reported it (real column); without it the created card is not placed (never Inbox).
 *   `CARD_MOVED`/`OWNER_APPROVED` put the card in `payload["to"]` (a `Column` name), at the end of that column.
 * - `isWaitingForOwner(column)` and `OWNER_DECISION_COLUMNS`.
 *
 * Pulse strip (pure, in `PulseModel.kt`):
 *
 * - `Pulse(state: AssistantState, phrase: String, poolCapacity: Int, active: List<String>)`: the daemon's reading.
 * - `PulseView.of(pulse, cost: CostSummary)` is the live strip; `PulseView.loading()` is the strip before any reading;
 *   `PulseView.markStale()` keeps the last reading and says it is out of date. Nothing is computed beyond words,
 *   squares (`true` = in use, one per slot) and the meter (`percent` = spent over the day limit, rounded).
 * - `ceiling` is `PulseCeiling.Meter` when the day has a limit, `PulseCeiling.Unlimited` otherwise, null before a reading.
 */
class BoardStateTest {
    @Test
    fun `AC-1 lists every column in product order with the snapshot cards in the right one`() {
        val board = BoardFixtures.empty().applySnapshot(
            listOf(
                BoardFixtures.aCard("a", Column.INBOX),
                BoardFixtures.aCard("b", Column.CODING),
                BoardFixtures.aCard("c", Column.CODING),
                BoardFixtures.aCard("d", Column.DONE),
            ),
        )

        assertThat(Column.entries).hasSize(10)
        assertThat(board.cardsIn(Column.INBOX).map { it.id }).containsExactly("a")
        assertThat(board.cardsIn(Column.CODING).map { it.id }).containsExactly("b", "c")
        assertThat(board.cardsIn(Column.DONE).map { it.id }).containsExactly("d")
        assertThat(board.cardsIn(Column.SPEC_REVIEW)).isEmpty()
    }

    @Test
    fun `AC-2 a created card appears in the column the daemon reports, not in Inbox by assumption`() {
        val board = BoardFixtures.empty().applySnapshot(listOf(BoardFixtures.aCard("a", Column.INBOX)))
        val created = BoardFixtures.aCard("n", Column.REFINEMENT)

        val after = board.applyEvent(BoardFixtures.anEvent(1, "n", EventType.CARD_CREATED), fetched = created)

        assertThat(after.cardsIn(Column.REFINEMENT)).containsExactly(created)
        assertThat(after.cardsIn(Column.INBOX).map { it.id }).containsExactly("a")
        assertThat(after.lastSeq).isEqualTo(1L)
    }

    @Test
    fun `AC-3 a moved card leaves its column and joins the destination at the end`() {
        val board = BoardFixtures.empty().applySnapshot(
            listOf(
                BoardFixtures.aCard("a", Column.CODING),
                BoardFixtures.aCard("b", Column.VALIDATION),
                BoardFixtures.aCard("c", Column.CODING),
            ),
        )

        val after = board.applyEvent(BoardFixtures.aMove(2, "a", Column.VALIDATION))

        assertThat(after.cardsIn(Column.CODING).map { it.id }).containsExactly("c")
        assertThat(after.cardsIn(Column.VALIDATION).map { it.id }).containsExactly("b", "a")
    }

    @Test
    fun `AC-3 events are idempotent, unknown cards are ignored and unrelated events only advance the cursor`() {
        val board = BoardFixtures.empty()
            .applySnapshot(listOf(BoardFixtures.aCard("a", Column.CODING)))
            .applyEvent(BoardFixtures.aMove(5, "a", Column.VALIDATION))

        val replayed = board.applyEvent(BoardFixtures.aMove(5, "a", Column.DONE))
        val older = board.applyEvent(BoardFixtures.aMove(4, "a", Column.DONE))
        val unknown = board.applyEvent(BoardFixtures.aMove(6, "ghost", Column.DONE))
        val other = board.applyEvent(BoardFixtures.anEvent(7, "a", EventType.GATE_FINISHED))

        assertThat(replayed).isEqualTo(board)
        assertThat(older).isEqualTo(board)
        assertThat(unknown.cardsIn(Column.DONE)).isEmpty()
        assertThat(unknown.lastSeq).isEqualTo(6L)
        assertThat(other.cardsIn(Column.VALIDATION).map { it.id }).containsExactly("a")
        assertThat(other.lastSeq).isEqualTo(7L)
    }

    @Test
    fun `AC-4 an empty snapshot gives ten empty columns`() {
        val board = BoardFixtures.empty().applySnapshot(emptyList())

        assertThat(Column.entries.map { board.cardsIn(it) }).hasSize(10).allMatch { it.isEmpty() }
    }

    @Test
    fun `AC-4 reverting a refused optimistic move puts the card back in its origin column`() {
        val board = BoardFixtures.empty().applySnapshot(
            listOf(BoardFixtures.aCard("a", Column.STAGED), BoardFixtures.aCard("b", Column.READY_FOR_PROD)),
        )

        val moved = board.withOptimisticMove("a", Column.READY_FOR_PROD)
        val reverted = moved.revertMove("a", Column.STAGED, Column.READY_FOR_PROD)

        assertThat(moved.cardsIn(Column.READY_FOR_PROD).map { it.id }).containsExactly("b", "a")
        assertThat(moved.cardsIn(Column.STAGED)).isEmpty()
        assertThat(reverted.cardsIn(Column.STAGED).map { it.id }).containsExactly("a")
        assertThat(reverted.cardsIn(Column.READY_FOR_PROD).map { it.id }).containsExactly("b")
    }

    @Test
    fun `AC-4 reverting leaves the card where a daemon event already took it`() {
        val board = BoardFixtures.empty()
            .applySnapshot(listOf(BoardFixtures.aCard("a", Column.STAGED)))
            .withOptimisticMove("a", Column.READY_FOR_PROD)
            .applyEvent(BoardFixtures.aMove(1, "a", Column.DONE))

        val reverted = board.revertMove("a", Column.STAGED, Column.READY_FOR_PROD)

        assertThat(reverted).isEqualTo(board)
        assertThat(reverted.cardsIn(Column.DONE).map { it.id }).containsExactly("a")
    }

    @Test
    fun `AC-5 only Spec Review and Staged wait for the owner`() {
        assertThat(OWNER_DECISION_COLUMNS).containsExactlyInAnyOrder(Column.SPEC_REVIEW, Column.STAGED)
        assertThat(Column.entries.filter { isWaitingForOwner(it) })
            .containsExactlyInAnyOrder(Column.SPEC_REVIEW, Column.STAGED)
    }

    @Test
    fun `AC-6 a card moved out of a decision column is no longer in a waiting column`() {
        val board = BoardFixtures.empty()
            .applySnapshot(listOf(BoardFixtures.aCard("a", Column.STAGED)))

        val after = board.applyEvent(BoardFixtures.aMove(1, "a", Column.READY_FOR_PROD))

        assertThat(board.cardsIn(Column.STAGED)).hasSize(1)
        assertThat(isWaitingForOwner(Column.STAGED)).isTrue()
        assertThat(after.cardsIn(Column.STAGED)).isEmpty()
        assertThat(isWaitingForOwner(Column.READY_FOR_PROD)).isFalse()
        assertThat(after.cardsIn(Column.READY_FOR_PROD).map { it.id }).containsExactly("a")
    }

    @Test
    fun `AC-3 pulse strip shows the state in words with the daemon's phrase`() {
        val view = PulseView.of(PulseFixtures.aPulse(), PulseFixtures.dayWithLimit("3.20"))

        assertThat(view.freshness).isEqualTo(PulseFreshness.Live)
        assertThat(view.stateText).isEqualTo("▸ trabalhando")
        assertThat(view.phrase).isEqualTo("Em andamento: #235, #240.")
        assertThat(view.notice).isNull()
    }

    @Test
    fun `AC-3 pulse strip shows the pool as used and total squares with the active cards`() {
        val view = PulseView.of(PulseFixtures.aPulse(capacity = 4, active = listOf("#235", "#240")), PulseFixtures.dayWithLimit("3.20"))

        assertThat(view.sessionsText).isEqualTo("2 de 4 sessões")
        assertThat(view.squares).containsExactly(true, true, false, false)
        assertThat(view.activeCards).containsExactly("#235", "#240")
    }

    @Test
    fun `AC-3 pulse strip shows the day ceiling as a meter with the spend marked as estimate`() {
        val view = PulseView.of(PulseFixtures.aPulse(), PulseFixtures.dayWithLimit("3.20"))

        val meter = view.ceiling as? PulseCeiling.Meter
        assertThat(meter).isNotNull()
        assertThat(meter?.spent).contains("est.").contains("US$ 3,20")
        assertThat(meter?.percent).isEqualTo(32)
        assertThat(meter?.limitState).isEqualTo(LimitState.Within)
    }

    @Test
    fun `AC-3 pulse strip meter follows the limit state the daemon reports`() {
        val view = PulseView.of(PulseFixtures.aPulse(), PulseFixtures.dayWithLimit("10.00", dayReached = true))

        val meter = view.ceiling as? PulseCeiling.Meter
        assertThat(meter?.limitState).isEqualTo(LimitState.Reached)
        assertThat(meter?.percent).isEqualTo(100)
    }

    @Test
    fun `AC-3 pulse strip without a day limit says no ceiling and has no meter`() {
        val view = PulseView.of(PulseFixtures.aPulse(), FeedFixtures.aSummary("3.20"))

        assertThat(view.ceiling).isEqualTo(PulseCeiling.Unlimited("sem teto"))
    }

    @Test
    fun `AC-4 a stale pulse strip keeps the last reading dimmed with the out of date notice`() {
        val live = PulseView.of(PulseFixtures.aPulse(), PulseFixtures.dayWithLimit("3.20"))

        val stale = live.markStale()

        assertThat(stale.freshness).isEqualTo(PulseFreshness.Stale)
        assertThat(stale.notice).containsIgnoringCase("desatualizad")
        assertThat(stale.copy(freshness = PulseFreshness.Live, notice = null)).isEqualTo(live)
    }

    @Test
    fun `AC-4 a pulse strip that never read the daemon invents no state, phrase or number`() {
        val loading = PulseView.loading()
        val staleBeforeAnyReading = loading.markStale()

        assertThat(loading.freshness).isEqualTo(PulseFreshness.Loading)
        for (view in listOf(loading, staleBeforeAnyReading)) {
            assertThat(view.stateText).isNull()
            assertThat(view.phrase).isNull()
            assertThat(view.sessionsText).isNull()
            assertThat(view.squares).isEmpty()
            assertThat(view.activeCards).isEmpty()
            assertThat(view.ceiling).isNull()
        }
        assertThat(staleBeforeAnyReading.freshness).isEqualTo(PulseFreshness.Stale)
    }
}

object BoardFixtures {
    fun empty(): Board = Board(emptyList(), null)

    fun aCard(id: String, column: Column, title: String = "Title of $id") = BoardCard(id, title, column)

    fun anEvent(
        seq: Long,
        cardId: String,
        type: EventType,
        payload: Map<String, String> = emptyMap(),
    ) = BoardEvent(seq, cardId, type, payload)

    fun aMove(seq: Long, cardId: String, to: Column, type: EventType = EventType.CARD_MOVED) =
        anEvent(seq, cardId, type, mapOf("to" to to.name))
}

object PulseFixtures {
    fun aPulse(
        state: AssistantState = AssistantState.WORKING,
        phrase: String = "Em andamento: #235, #240.",
        capacity: Int = 4,
        active: List<String> = listOf("#235", "#240"),
    ) = Pulse(state, phrase, capacity, active)

    /** A day with a US$ 10.00 limit and [spent] already spent. */
    fun dayWithLimit(spent: String, dayReached: Boolean = false) =
        FeedFixtures.aSummary(spent, limits = CostLimitsInfo(null, BigDecimal("10.00")), dayReached = dayReached)
}
