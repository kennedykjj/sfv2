package sfv2.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import sfv2.core.Column
import sfv2.core.MoveAuthority
import sfv2.core.MoveRules

/** Where the owner may take a card from [from]: the owner-only moves of [MoveRules.table]. */
fun ownerTargets(from: Column): List<Column> =
    MoveRules.table.filter { (move, authority) -> move.first == from && authority == MoveAuthority.OWNER_ONLY }.keys.map { it.second }

/** Sending a spec back to refinement asks for a note; the daemon requires it. */
fun needsNote(from: Column, to: Column): Boolean = from == Column.SPEC_REVIEW && to == Column.READY_FOR_REFINEMENT

sealed interface MoveOutcome {
    data object Accepted : MoveOutcome

    data class Refused(val reason: String) : MoveOutcome

    data class NotSent(val cause: NotSentCause) : MoveOutcome
}

enum class NotSentCause { DAEMON_UNAVAILABLE, NO_OWNER_PROOF }

/** The board the controller edits optimistically; the monitor implements it. */
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

/** The owner's move gesture (drag or `M`): hold a card, choose a destination, submit. The daemon stays the judge. */
class OwnerMoveController(
    private val commands: OwnerCommands,
    private val edits: BoardEdits,
    private val io: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow(OwnerMoveState())
    val state: StateFlow<OwnerMoveState> = mutableState

    fun hold(cardId: String) {
        val card = edits.board()?.cards?.firstOrNull { it.id == cardId }
        val targets = card?.let { ownerTargets(it.column) }.orEmpty()
        mutableState.value = if (card == null || targets.isEmpty()) {
            OwnerMoveState(status = Texts.NO_OWNER_MOVE, notice = mutableState.value.notice)
        } else {
            OwnerMoveState(held = card, targets = targets, status = Texts.holding(card.id), notice = null)
        }
    }

    fun chooseTarget(to: Column) {
        val current = mutableState.value
        if (to in current.targets) mutableState.value = current.copy(selected = to, notePending = false)
    }

    suspend fun submit(note: String? = null) {
        val current = mutableState.value
        val card = current.held ?: return
        val to = current.selected ?: return
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        if (needsNote(card.column, to) && trimmed == null) {
            mutableState.value = current.copy(notePending = true)
            return
        }
        mutableState.value = OwnerMoveState()
        edits.edit { it.withOptimisticMove(card.id, to) }
        val outcome = withContext(io) { commands.move(card.id, card.column, to, trimmed) }
        if (outcome == MoveOutcome.Accepted) return
        edits.edit { it.revertMove(card.id, card.column, to) }
        mutableState.value = OwnerMoveState(notice = noticeFor(outcome))
    }

    fun cancel() {
        mutableState.value = OwnerMoveState(status = Texts.NOTHING_SENT, notice = mutableState.value.notice)
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    private fun noticeFor(outcome: MoveOutcome): MoveNotice? =
        when (outcome) {
            MoveOutcome.Accepted -> null
            is MoveOutcome.Refused -> MoveNotice(NoticeKind.REFUSED, Texts.refused(outcome.reason))
            is MoveOutcome.NotSent -> MoveNotice(NoticeKind.NOT_SENT, Texts.notSent(outcome.cause))
        }
}
