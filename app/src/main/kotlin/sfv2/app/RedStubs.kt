package sfv2.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Temporary red-gate stubs: signatures only, neutral bodies. The engineer replaces them with the real
// implementation in OwnerMoves.kt and deletes this file.

data class SpecReviewState(
    val notePending: Boolean = false,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val notice: MoveNotice? = null,
)

class SpecReviewController(
    private val commands: OwnerCommands,
    private val io: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow(SpecReviewState())
    val state: StateFlow<SpecReviewState> = mutableState

    suspend fun approve(cardId: String) {}

    suspend fun requestChange(cardId: String, note: String?) {}

    fun cancel() {}

    fun dismissNotice() {}

    fun onDetail(detail: CardDetail) {}
}

fun specReviewActionsVisible(state: CardDetailState): Boolean = false
