package sfv2.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import sfv2.core.Column

// shipfloor:qa-stub — only so the red gate compiles; the implementer deletes this file and writes the real
// OwnerMoves.kt, OwnerCommands.kt and Board members. Every function here does nothing useful on purpose.

fun Board.withOptimisticMove(cardId: String, to: Column): Board = this

fun Board.revertMove(cardId: String, from: Column, optimistic: Column): Board = this

fun ownerTargets(from: Column): List<Column> = emptyList()

fun needsNote(from: Column, to: Column): Boolean = false

sealed interface MoveOutcome {
    data object Accepted : MoveOutcome
    data class Refused(val reason: String) : MoveOutcome
    data class NotSent(val cause: NotSentCause) : MoveOutcome
}

enum class NotSentCause { DAEMON_UNAVAILABLE, NO_OWNER_PROOF }

interface BoardEdits {
    fun board(): Board?
    fun edit(transform: (Board) -> Board)
}

enum class NoticeKind { REFUSED, NOT_SENT }

data class MoveNotice(val kind: NoticeKind, val text: String)

data class OwnerMoveState(
    val held: BoardCard? = null,
    val targets: List<Column> = emptyList(),
    val selected: Column? = null,
    val notePending: Boolean = false,
    val status: String? = null,
    val notice: MoveNotice? = null,
)

interface OwnerCommands {
    fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome
}

fun ownerMoveRequest(cardId: String, from: Column, to: Column, note: String?, proof: String): String = "{}"

class SocketOwnerCommands(private val socket: Path, private val token: Path) : OwnerCommands {
    override fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome = MoveOutcome.Accepted
}

class OwnerMoveController(commands: OwnerCommands, edits: BoardEdits, io: CoroutineDispatcher) {
    val state: StateFlow<OwnerMoveState> = MutableStateFlow(OwnerMoveState())

    fun hold(cardId: String) {}

    fun chooseTarget(to: Column) {}

    suspend fun submit(note: String? = null) {}

    fun cancel() {}

    fun dismissNotice() {}
}
