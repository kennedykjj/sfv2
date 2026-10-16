package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import sfv2.store.CardEventStore
import sfv2.store.SpecStore

// shipfloor:qa-stub - lets the acceptance tests compile before the implementation exists; the implementer deletes this file
// and adds the real signatures (McpSession(repo, role, cardId), McpSessions.open(repo, role, cardId), WorktreeManager.existing,
// the McpServerFactory parameters recorder/worktrees/clock and McpRequestTools).

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun McpSession(repo: String, role: String, cardId: String): McpSession = McpSession(repo)

@Suppress("UNUSED_PARAMETER")
fun McpSessions.open(repo: String, role: String, cardId: String): String = open(repo)

@Suppress("UNUSED_PARAMETER")
fun WorktreeManager.existing(project: Project, cardId: String): Path? = null

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun McpServerFactory(
    sessions: McpSessions,
    store: CardEventStore,
    specs: SpecStore,
    registry: ProjectRegistry,
    files: ProjectFileSource,
    recorder: EventRecorder,
    worktrees: WorktreeManager,
    clock: Clock,
    log: DaemonLog = StderrDaemonLog,
): McpServerFactory = McpServerFactory(sessions, store, specs, registry, files, log)

data class ReportedView(val eventId: String)

data class WriteView(val path: String, val bytes: Int)

@Suppress("UNUSED_PARAMETER")
class McpRequestTools(
    session: McpSession,
    store: CardEventStore,
    recorder: EventRecorder,
    worktrees: WorktreeManager,
    registry: ProjectRegistry,
    clock: Clock,
    log: DaemonLog,
) {
    fun completionReport(cardId: String, summary: String): ReportedView = ReportedView("stub")

    fun fileWriteRequest(cardId: String, path: String, content: String): WriteView = WriteView(path, content.length)
}
