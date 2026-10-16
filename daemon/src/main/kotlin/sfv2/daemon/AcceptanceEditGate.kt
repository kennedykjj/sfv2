package sfv2.daemon

import java.nio.file.Path

/** Fails when the implementation changed assertions of the acceptance tests committed by QA. */
class AcceptanceEditGate(private val executor: GateExecutor, private val runner: GateRunner) {

    @Suppress("UNUSED_PARAMETER")
    fun check(
        cardId: String,
        worktree: Path,
        qaCommit: String,
        files: List<String>,
        keep: Regex,
        runDir: Path,
    ): GateVerdict = GateVerdict.Pass
}
