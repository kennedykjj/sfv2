// shipfloor:qa-stub - compile-only stubs so the red gate fails by assertion; the implementer deletes this file
// and adds Runtime.kt and FakeRuntime.kt in src/main.
package sfv2.ports

import java.nio.file.Path

interface Runtime {
    fun launch(request: SessionRequest): SessionResult
}

data class SessionRequest(val stage: String, val worktree: Path, val model: String, val mcpConfig: Path)

sealed interface SessionResult {
    data class Exited(val exitCode: Int, val transcriptPath: Path) : SessionResult
    data class Hung(val transcriptPath: Path) : SessionResult
}

sealed interface SessionScript {
    data object Succeed : SessionScript
    data class Fail(val exitCode: Int) : SessionScript
    data object Hang : SessionScript
}

class FakeRuntime(script: List<SessionScript>) : Runtime {
    val launched: List<SessionRequest> = emptyList()
    override fun launch(request: SessionRequest): SessionResult = SessionResult.Exited(-1, Path.of(""))
}
