package sfv2.app

import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Temporary red-step stubs (QA): minimal signatures with neutral bodies so the acceptance tests compile and fail by assertion.

sealed interface DraftOutcome {
    data class Drafted(val status: String, val summary: String, val prLink: String, val draftHash: String) : DraftOutcome

    data class Refused(val reason: String) : DraftOutcome

    data class NotSent(val cause: NotSentCause) : DraftOutcome
}

sealed interface ConfirmOutcome {
    data object Closed : ConfirmOutcome

    data object DraftChanged : ConfirmOutcome

    data class Failed(val reason: String) : ConfirmOutcome

    data class NotSent(val cause: NotSentCause) : ConfirmOutcome
}

interface ClosureCommands {
    fun draft(cardId: String): DraftOutcome

    fun confirm(cardId: String, draftHash: String): ConfirmOutcome
}

fun closureDraftRequest(cardId: String): String = "{}"

fun closureConfirmRequest(cardId: String, draftHash: String, proof: String): String = "{}"

class SocketClosureCommands(private val socket: Path, private val token: Path) : ClosureCommands {
    override fun draft(cardId: String): DraftOutcome = DraftOutcome.Refused("")

    override fun confirm(cardId: String, draftHash: String): ConfirmOutcome = ConfirmOutcome.Failed("")
}

sealed interface ClosureState {
    data object Loading : ClosureState

    data class Draft(
        val status: String,
        val summary: String,
        val prLink: String,
        val draftHash: String,
        val notSent: NotSentCause? = null,
    ) : ClosureState

    data class Refused(val reason: String) : ClosureState

    data class Confirming(val draftHash: String) : ClosureState

    data object Closed : ClosureState

    data class Failed(val reason: String, val draftHash: String) : ClosureState
}

class ClosureController(private val commands: ClosureCommands, private val io: CoroutineDispatcher) {
    private val mutableState = MutableStateFlow<ClosureState>(ClosureState.Loading)
    val state: StateFlow<ClosureState> = mutableState

    suspend fun open(cardId: String, events: List<TimelineEvent>) {}

    suspend fun confirm() {}
}
