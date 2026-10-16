package sfv2.daemon

import java.nio.file.Path

// shipfloor:qa-stub - only so WorktreeManagerTest compiles for the red gate; the implementer deletes this file.

interface WorktreeGit {
    fun addWorktree(repo: Path, dir: Path, branch: String)
    fun hasWorktree(repo: Path, dir: Path): Boolean
    fun isDirty(dir: Path): Boolean
    fun removeWorktree(repo: Path, dir: Path)
    fun deleteBranch(repo: Path, branch: String)
}

data class CardWorktree(val cardId: String, val path: Path, val branch: String)

sealed interface CleanupResult {
    data object Removed : CleanupResult
    data class KeptDirty(val path: Path) : CleanupResult
    data object NothingToClean : CleanupResult
}

class WorktreeException(message: String) : RuntimeException(message)

class WorktreeManager(root: Path, git: WorktreeGit, log: DaemonLog = StderrDaemonLog) {
    fun ensure(project: Project, cardId: String, title: String): CardWorktree =
        CardWorktree(cardId, Path.of("/unimplemented"), "unimplemented")

    fun cleanup(project: Project, cardId: String, title: String): CleanupResult = CleanupResult.NothingToClean
}
