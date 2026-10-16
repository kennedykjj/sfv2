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
 *
 * Command palette (pure, in `PaletteModel.kt`):
 *
 * - `PaletteView(label)` with `BOARD("Quadro")`, `FEED("Feed")`, `SETTINGS("Configurações")`; `Direction { UP, DOWN, FIRST, LAST }`.
 * - `PaletteEntry.Card(card, waiting)` and `PaletteEntry.View(view)`.
 * - `paletteEntries(cards, views, query)`: waiting cards, then views, then the other cards; filtered by label, title,
 *   `id` prefix or column display name (case-insensitive); at most `PALETTE_ROWS` (9) rows, cut after filtering.
 * - `PaletteState(open, query, active)`: `toggle()`, `close()` (both drop query and active), `withQuery(query)`,
 *   `move(direction, size)` (UP/DOWN wrap, FIRST/LAST jump, result always in `0 until size`, 0 when `size` is 0),
 *   `select(entries): PaletteChoice(state, entry)` where `state` is closed and `entry` is the active row or null.
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
    fun `AC-1 palette with an empty query lists waiting cards, then views, then the other cards`() {
        val entries = paletteEntries(PaletteFixtures.mixedCards(), PaletteView.entries, "")

        assertThat(entries.keys()).containsExactly(
            "card:b", "card:d", "view:BOARD", "view:FEED", "view:SETTINGS", "card:abc123", "card:c",
        )
        assertThat(entries.filterIsInstance<PaletteEntry.Card>().associate { it.card.id to it.waiting })
            .containsEntry("b", true)
            .containsEntry("d", true)
            .containsEntry("abc123", false)
            .containsEntry("c", false)
    }

    @Test
    fun `AC-1 palette shows at most nine rows and keeps the waiting cards first`() {
        val cards = (1..12).map { BoardFixtures.aCard("c$it", Column.CODING, "Plain $it") } +
            BoardFixtures.aCard("w", Column.STAGED, "Waiting")

        val entries = paletteEntries(cards, PaletteView.entries, "")

        assertThat(PALETTE_ROWS).isEqualTo(9)
        assertThat(entries).hasSize(9)
        assertThat(entries.keys().take(4)).containsExactly("card:w", "view:BOARD", "view:FEED", "view:SETTINGS")
    }

    @Test
    fun `AC-2 palette filters by title, id prefix and column name without caring about case`() {
        val cards = PaletteFixtures.mixedCards()

        assertThat(paletteEntries(cards, PaletteView.entries, "LOGIN").keys()).containsExactly("card:c")
        assertThat(paletteEntries(cards, PaletteView.entries, "abc").keys()).containsExactly("card:abc123")
        assertThat(paletteEntries(cards, PaletteView.entries, "spec rev").keys()).containsExactly("card:b")
        assertThat(paletteEntries(cards, PaletteView.entries, "staged").keys()).containsExactly("card:d")
    }

    @Test
    fun `AC-2 palette filters views by label and keeps the order of the empty query`() {
        val cards = PaletteFixtures.mixedCards()

        assertThat(paletteEntries(cards, PaletteView.entries, "quad").keys()).containsExactly("view:BOARD")
        assertThat(paletteEntries(cards, PaletteView.entries, "FEED").keys()).containsExactly("view:FEED")
        assertThat(paletteEntries(cards, PaletteView.entries, "configura").keys()).containsExactly("view:SETTINGS")
        assertThat(paletteEntries(cards, PaletteView.entries, "e").keys())
            .containsSubsequence("card:b", "card:d", "view:BOARD", "view:FEED", "view:SETTINGS")
    }

    @Test
    fun `AC-2 palette without a match is empty and nothing can be chosen`() {
        val entries = paletteEntries(PaletteFixtures.mixedCards(), PaletteView.entries, "zzz-no-match")
        val choice = PaletteState(open = true, query = "zzz-no-match", active = 0).select(entries)

        assertThat(entries).isEmpty()
        assertThat(choice.entry).isNull()
    }

    @Test
    fun `AC-3 arrows move the active row and wrap at both ends`() {
        val state = PaletteState(open = true)

        assertThat(state.move(Direction.DOWN, 3).active).isEqualTo(1)
        assertThat(state.move(Direction.DOWN, 3).move(Direction.DOWN, 3).move(Direction.DOWN, 3).active).isEqualTo(0)
        assertThat(state.move(Direction.UP, 3).active).isEqualTo(2)
        assertThat(state.move(Direction.DOWN, 3).move(Direction.UP, 3).active).isEqualTo(0)
    }

    @Test
    fun `AC-3 Home and End jump to the first and last row`() {
        val middle = PaletteState(open = true, active = 2)

        assertThat(middle.move(Direction.FIRST, 5).active).isEqualTo(0)
        assertThat(middle.move(Direction.LAST, 5).active).isEqualTo(4)
    }

    @Test
    fun `AC-3 the active row stays inside the list after the filter shrinks it`() {
        val deep = PaletteState(open = true, active = 7)

        assertThat(deep.move(Direction.DOWN, 3).active).isBetween(0, 2)
        assertThat(deep.move(Direction.UP, 3).active).isBetween(0, 2)
        assertThat(deep.move(Direction.LAST, 3).active).isEqualTo(2)
        assertThat(deep.move(Direction.DOWN, 0).active).isEqualTo(0)
    }

    @Test
    fun `AC-4 choosing a card row returns that card and closes the palette`() {
        val entries = paletteEntries(PaletteFixtures.mixedCards(), PaletteView.entries, "")
        val state = PaletteState(open = true, query = "", active = 1)

        val choice = state.select(entries)

        assertThat(choice.entry)
            .isEqualTo(PaletteEntry.Card(BoardFixtures.aCard("d", Column.STAGED, "Approve release"), true))
        assertThat(choice.state.open).isFalse()
        assertThat(choice.state.query).isEmpty()
    }

    @Test
    fun `AC-4 choosing a view row returns that view and closes the palette`() {
        val entries = paletteEntries(PaletteFixtures.mixedCards(), PaletteView.entries, "feed")
        val state = PaletteState(open = true, query = "feed", active = 0)

        val choice = state.select(entries)

        assertThat(choice.entry).isEqualTo(PaletteEntry.View(PaletteView.FEED))
        assertThat(choice.state.open).isFalse()
    }

    @Test
    fun `AC-5 the shortcut opens the palette and pressing it again closes it dropping the text`() {
        val closed = PaletteState()

        val opened = closed.toggle()
        val typed = opened.withQuery("abc")
        val toggledOff = typed.toggle()

        assertThat(opened.open).isTrue()
        assertThat(typed.query).isEqualTo("abc")
        assertThat(toggledOff.open).isFalse()
        assertThat(toggledOff.query).isEmpty()
    }

    @Test
    fun `AC-5 Esc or a click on the backdrop closes the palette and discards the text`() {
        val typed = PaletteState(open = true, query = "abc", active = 2)

        val closed = typed.close()

        assertThat(closed.open).isFalse()
        assertThat(closed.query).isEmpty()
        assertThat(closed.active).isEqualTo(0)
        assertThat(closed.toggle().query).isEmpty()
    }
}

private fun List<PaletteEntry>.keys(): List<String> = map {
    when (it) {
        is PaletteEntry.Card -> "card:${it.card.id}"
        is PaletteEntry.View -> "view:${it.view.name}"
    }
}

object PaletteFixtures {
    fun mixedCards(): List<BoardCard> = listOf(
        BoardFixtures.aCard("abc123", Column.CODING, "Refactor parser"),
        BoardFixtures.aCard("b", Column.SPEC_REVIEW, "Write the spec"),
        BoardFixtures.aCard("c", Column.DONE, "Fix login"),
        BoardFixtures.aCard("d", Column.STAGED, "Approve release"),
    )
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
