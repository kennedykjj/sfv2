package sfv2.app

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sfv2.core.Column
import sfv2.core.EventType

/**
 * Shape the implementation must follow (in `BoardMonitor.kt`, no UI, no sleeps):
 *
 * - `BoardMonitor(source: BoardSource, connection: StateFlow<ConnectionState>, io: CoroutineDispatcher)`;
 *   `val view: StateFlow<BoardView?>` is `null` until the first snapshot; `BoardView(board: Board, live: Boolean)`.
 * - `suspend fun run()` loops until cancelled: on `Connected` it subscribes (`after = board.lastSeq`), reads the
 *   snapshot once, applies events (`CARD_CREATED` through `source.fetchCard`); when `subscribe` throws `IOException`
 *   it sets `live = false`, waits for the next `Connected` and subscribes again with `after = lastSeq`, without
 *   reloading the snapshot. The blocking source calls run on `io`.
 */
class BoardMonitorTest {
    @Test
    fun `AC-7 resubscribes after the drop from the last seen seq without reloading or duplicating`() = withMonitor { fx ->
        fx.source.snapshot = listOf(BoardFixtures.aCard("a", Column.VALIDATION))
        fx.source.sessions += listOf(BoardFixtures.aMove(3, "a", Column.VALIDATION))
        fx.source.sessions += listOf(
            BoardFixtures.aMove(3, "a", Column.DONE),
            BoardFixtures.aMove(4, "a", Column.STAGED),
        )

        fx.connect()
        fx.drop()
        fx.connect()

        val board = requireNotNull(fx.monitor.view.value).board
        assertThat(fx.source.afters).containsExactly(null, 3L)
        assertThat(fx.source.snapshotCalls).isEqualTo(1)
        assertThat(board.cardsIn(Column.STAGED).map { it.id }).containsExactly("a")
        assertThat(board.cardsIn(Column.VALIDATION)).isEmpty()
        assertThat(board.cards).hasSize(1)
    }

    @Test
    fun `AC-8 shows no board before the first snapshot and marks the kept board as not live after a drop`() = withMonitor { fx ->
        fx.source.snapshot = listOf(BoardFixtures.aCard("a", Column.SPEC_REVIEW))
        assertThat(fx.monitor.view.value).isNull()

        fx.connect()
        fx.drop()

        val view = requireNotNull(fx.monitor.view.value)
        assertThat(view.live).isFalse()
        assertThat(view.board.cardsIn(Column.SPEC_REVIEW).map { it.id }).containsExactly("a")
    }

    @Test
    fun `AC-9 the owner approval moves the card out of Spec Review and drops its highlight`() = withMonitor { fx ->
        fx.source.snapshot = listOf(BoardFixtures.aCard("a", Column.SPEC_REVIEW))
        fx.source.sessions += emptyList<BoardEvent>()
        fx.source.sessions += listOf(BoardFixtures.aMove(2, "a", Column.READY_FOR_DEV, EventType.OWNER_APPROVED))

        fx.connect()
        fx.drop()
        fx.connect()

        val board = requireNotNull(fx.monitor.view.value).board
        assertThat(board.cardsIn(Column.SPEC_REVIEW)).isEmpty()
        assertThat(board.cardsIn(Column.READY_FOR_DEV).map { it.id }).containsExactly("a")
        assertThat(isWaitingForOwner(Column.READY_FOR_DEV)).isFalse()
    }

    @Test
    fun `AC-10 a created card is placed in the column fetched with card show, not in Inbox`() = withMonitor { fx ->
        fx.source.snapshot = emptyList()
        fx.source.known["n"] = BoardFixtures.aCard("n", Column.CODING, "Imported")
        fx.source.sessions += emptyList<BoardEvent>()
        fx.source.sessions += listOf(BoardFixtures.anEvent(2, "n", EventType.CARD_CREATED))

        fx.connect()
        fx.drop()
        fx.connect()

        val board = requireNotNull(fx.monitor.view.value).board
        assertThat(board.cardsIn(Column.CODING).map { it.id }).containsExactly("n")
        assertThat(board.cardsIn(Column.INBOX)).isEmpty()
        assertThat(fx.source.fetched).containsExactly("n")
    }

    private fun withMonitor(body: suspend (MonitorFixtures) -> Unit) = runBlocking {
        val fx = MonitorFixtures()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { fx.monitor.run() }
        try {
            body(fx)
        } finally {
            job.cancelAndJoin()
        }
    }
}

class FakeBoardSource : BoardSource {
    var snapshot: List<BoardCard> = emptyList()
    val known = mutableMapOf<String, BoardCard>()
    val sessions = ArrayDeque<List<BoardEvent>>()
    val afters = mutableListOf<Long?>()
    val fetched = mutableListOf<String>()
    var snapshotCalls = 0
        private set

    override fun snapshot(): List<BoardCard> {
        snapshotCalls += 1
        return snapshot
    }

    override fun fetchCard(id: String): BoardCard? {
        fetched += id
        return known[id]
    }

    override fun subscribe(after: Long?, onEvent: (BoardEvent) -> Unit) {
        afters += after
        sessions.removeFirstOrNull().orEmpty().forEach(onEvent)
        throw IOException("connection closed")
    }
}

class MonitorFixtures {
    val source = FakeBoardSource()
    val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val monitor = BoardMonitor(source, connection, Dispatchers.Unconfined)

    suspend fun connect() {
        connection.value = ConnectionState.Connected
        settle()
    }

    suspend fun drop() {
        connection.value = ConnectionState.Disconnected(DisconnectReason.NOT_RESPONDING)
        settle()
    }

    private suspend fun settle() = repeat(10) { yield() }
}
