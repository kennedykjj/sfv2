package sfv2.daemon

import java.nio.file.Path
import java.time.Duration
import sfv2.core.GateInputs

/** Fails when the implementation changed assertions of the acceptance tests committed by QA. */
class AcceptanceEditGate(private val executor: GateExecutor, @Suppress("unused") private val runner: GateRunner) {

    fun check(
        cardId: String,
        worktree: Path,
        qaCommit: String,
        files: List<String>,
        keep: Regex,
        runDir: Path,
    ): GateVerdict {
        val commit = GateInputs.qaCommit(qaCommit)
        val validFiles = files.map(GateInputs::file)
        val command = listOf("git", "-C", worktree.toString(), "diff", commit, "HEAD", "--") + validFiles
        val spec = GateSpec(
            name = GATE_NAME,
            command = command,
            workingDir = worktree,
            timeout = TIMEOUT,
            check = GateCheck.AssertionEdit(keep),
        )
        return executor.execute(cardId, spec, runDir)
    }

    private companion object {
        const val GATE_NAME = "acceptance-edit"
        val TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
