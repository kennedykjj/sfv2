package sfv2.daemon

import java.nio.file.Path

// shipfloor:qa-stub - compile-only stand-ins so the acceptance tests of #30 fail by assertion, not by compilation.
// The implementer deletes this file when ScopeGate.kt, GitChangedPaths.kt and Project.forbidden exist in main.

val Project.forbidden: List<String> get() = emptyList()

sealed interface ScopeVerdict {
    object Pass : ScopeVerdict
    data class Violations(val forbidden: List<String>, val outsideLayers: List<String>) : ScopeVerdict
}

object ScopeGate {
    fun check(project: Project, changed: List<String>, stageLayers: Set<String>): ScopeVerdict = ScopeVerdict.Pass
}

interface ChangedPaths {
    fun between(worktree: Path, base: String): List<String>
}

class GitChangedPaths : ChangedPaths {
    override fun between(worktree: Path, base: String): List<String> = emptyList()
}
