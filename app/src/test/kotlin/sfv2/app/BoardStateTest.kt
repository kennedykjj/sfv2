package sfv2.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
