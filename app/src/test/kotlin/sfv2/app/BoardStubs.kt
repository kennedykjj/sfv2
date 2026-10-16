package sfv2.app

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sfv2.core.Column
import sfv2.core.EventType

// shipfloor:qa-stub - compile-only stand-ins so the acceptance tests fail by assertion; the implementer deletes this file.

data class BoardCard(val id: String, val title: String, val column: Column)

data class BoardEvent(val seq: Long, val cardId: String, val type: EventType, val payload: Map<String, String>)

data class Board(val cards: List<BoardCard>, val lastSeq: Long?) {
    fun applySnapshot(cards: List<BoardCard>): Board = this

    fun applyEvent(event: BoardEvent, fetched: BoardCard? = null): Board = this

    fun cardsIn(column: Column): List<BoardCard> = emptyList()
}

val OWNER_DECISION_COLUMNS: Set<Column> = emptySet()

fun isWaitingForOwner(column: Column): Boolean = false

data class BoardView(val board: Board, val live: Boolean)

interface BoardSource {
    fun snapshot(): List<BoardCard>

    fun fetchCard(id: String): BoardCard?

    @Throws(IOException::class)
    fun subscribe(after: Long?, onEvent: (BoardEvent) -> Unit)
}

class BoardMonitor(
    source: BoardSource,
    connection: StateFlow<ConnectionState>,
    io: CoroutineDispatcher,
) {
    val view: StateFlow<BoardView?> = MutableStateFlow(null)

    suspend fun run() {}
}
