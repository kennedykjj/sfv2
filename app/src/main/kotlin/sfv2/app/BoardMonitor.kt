package sfv2.app

import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import sfv2.core.EventType

/** The board as the screen shows it; [live] is false while the event stream is not open. */
data class BoardView(val board: Board, val live: Boolean)

/**
 * Keeps the board in step with the daemon while [connection] says Connected. It subscribes first, then reads the
 * snapshot; events that arrive before the snapshot is applied wait and are applied right after it (events are
 * absolute, so the last one per card wins). After a drop it subscribes again from the last seen `seq`, without
 * reloading the snapshot.
 */
class BoardMonitor(
    private val source: BoardSource,
    private val connection: StateFlow<ConnectionState>,
    private val io: CoroutineDispatcher,
) : BoardEdits {
    private val mutableView = MutableStateFlow<BoardView?>(null)
    val view: StateFlow<BoardView?> = mutableView

    private val lock = Any()
    private var board = Board(emptyList(), null)
    private var live = false
    private var hasSnapshot = false
    private var waiting: MutableList<BoardEvent>? = null

    override fun board(): Board? = synchronized(lock) { board.takeIf { hasSnapshot } }

    /** Applies an owner edit (optimistic move or its reversal) to the board and publishes it. */
    override fun edit(transform: (Board) -> Board) {
        synchronized(lock) {
            board = transform(board)
            publish()
        }
    }

    suspend fun run() {
        while (true) {
            connection.first { it == ConnectionState.Connected }
            session()
            // Resubscribe on the next Connected; if the connection never flips, retry after a pause.
            withTimeoutOrNull(RETRY_AFTER.toMillis()) { connection.first { it != ConnectionState.Connected } }
        }
    }

    private suspend fun session() = coroutineScope {
        synchronized(lock) { if (!hasSnapshot) waiting = mutableListOf() }
        val after = synchronized(lock) { board.lastSeq }
        val subscription = launch {
            try {
                runInterruptible(io) { source.subscribe(after, ::receive) }
            } catch (e: IOException) {
                // The stream ended; the session wraps up below.
            }
        }
        val watcher = launch {
            connection.first { it != ConnectionState.Connected }
            subscription.cancelAndJoin()
        }
        try {
            if (!hasSnapshot && !loadSnapshot()) subscription.cancelAndJoin()
            if (subscription.isActive) setLive(true)
            subscription.join()
        } finally {
            watcher.cancel()
            setLive(false)
        }
    }

    /** Reads the snapshot and applies it, then the events that waited. False when the daemon could not be read. */
    private suspend fun loadSnapshot(): Boolean {
        val cards = try {
            runInterruptible(io) { source.snapshot() }
        } catch (e: IOException) {
            return false
        }
        synchronized(lock) {
            board = board.applySnapshot(cards)
            hasSnapshot = true
        }
        drainWaiting()
        return true
    }

    private fun drainWaiting() {
        while (true) {
            val batch = synchronized(lock) {
                val pending = waiting.orEmpty().toList()
                if (pending.isEmpty()) {
                    waiting = null
                    publish()
                }
                pending.also { waiting?.clear() }
            }
            if (batch.isEmpty()) return
            batch.forEach(::apply)
        }
    }

    /** Called on the io thread for each event of the stream. */
    private fun receive(event: BoardEvent) {
        val held = synchronized(lock) { waiting?.add(event) ?: false }
        if (!held) apply(event)
    }

    private fun apply(event: BoardEvent) {
        val fetched = if (event.type == EventType.CARD_CREATED) fetchOrNull(event.cardId) else null
        synchronized(lock) {
            board = board.applyEvent(event, fetched)
            publish()
        }
    }

    private fun fetchOrNull(id: String): BoardCard? =
        try {
            source.fetchCard(id)
        } catch (e: IOException) {
            null
        }

    private fun setLive(value: Boolean) {
        synchronized(lock) {
            live = value
            publish()
        }
    }

    private fun publish() {
        if (hasSnapshot && waiting == null) mutableView.value = BoardView(board, live)
    }

    private companion object {
        val RETRY_AFTER: Duration = Duration.ofSeconds(5)
    }
}
