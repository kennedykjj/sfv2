package sfv2.app

import sfv2.core.Column
import sfv2.core.EventType
import sfv2.core.OWNER_WAIT_COLUMNS

data class BoardCard(val id: String, val title: String, val column: Column)

/** Columns whose cards wait for a decision of the owner: the owner-only moves in `MoveRules` start here (PRODUCT.md). */
val OWNER_DECISION_COLUMNS: Set<Column> = OWNER_WAIT_COLUMNS

fun isWaitingForOwner(column: Column): Boolean = column in OWNER_DECISION_COLUMNS

/**
 * The cards of the board in insertion order. Events are absolute (`to`), so applying one twice is harmless;
 * an event whose `seq` is not above [lastSeq] was already seen and is dropped.
 */
data class Board(val cards: List<BoardCard>, val lastSeq: Long?) {
    fun applySnapshot(cards: List<BoardCard>): Board = copy(cards = cards)

    /** [fetched] is the card as the daemon reports it, used by `CARD_CREATED` only (never assumed to be in Inbox). */
    fun applyEvent(event: BoardEvent, fetched: BoardCard? = null): Board {
        if (lastSeq != null && event.seq <= lastSeq) return this
        val advanced = copy(lastSeq = event.seq)
        return when (event.type) {
            EventType.CARD_MOVED, EventType.OWNER_APPROVED -> advanced.moveTo(event)
            EventType.CARD_CREATED -> fetched?.let { advanced.upsert(it) } ?: advanced
            else -> advanced
        }
    }

    fun cardsIn(column: Column): List<BoardCard> = cards.filter { it.column == column }

    /** Shows [cardId] in [to] before the daemon confirms; the daemon's own event that follows is absolute. */
    fun withOptimisticMove(cardId: String, to: Column): Board = relocate(cardId, to)

    /** Undoes an optimistic move, unless the card is no longer in [optimistic] (a daemon event moved it on). */
    fun revertMove(cardId: String, from: Column, optimistic: Column): Board =
        if (cards.any { it.id == cardId && it.column == optimistic }) relocate(cardId, from) else this

    private fun relocate(cardId: String, to: Column): Board {
        val card = cards.firstOrNull { it.id == cardId } ?: return this
        return copy(cards = cards.filter { it.id != cardId } + card.copy(column = to))
    }

    private fun moveTo(event: BoardEvent): Board {
        val target = event.payload["to"]?.let { name -> Column.entries.firstOrNull { it.name == name } } ?: return this
        val card = cards.firstOrNull { it.id == event.cardId } ?: return this
        return copy(cards = cards.filter { it.id != card.id } + card.copy(column = target))
    }

    private fun upsert(card: BoardCard): Board =
        if (cards.any { it.id == card.id }) copy(cards = cards.map { if (it.id == card.id) card else it })
        else copy(cards = cards + card)
}
