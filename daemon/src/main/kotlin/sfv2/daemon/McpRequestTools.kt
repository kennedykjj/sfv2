package sfv2.daemon

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.util.UUID
import sfv2.core.AgentRequestRefusedException
import sfv2.core.AgentRequests
import sfv2.core.Card
import sfv2.core.EventId
import sfv2.store.CardEventStore

data class ReportedView(val eventId: String)

data class WriteView(val path: String, val bytes: Int)

/** Tools through which an agent reports and writes; each one acts only on the session's own card. */
class McpRequestTools(
    private val session: McpSession,
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val worktrees: WorktreeManager,
    private val registry: ProjectRegistry,
    private val clock: Clock,
    private val log: DaemonLog = StderrDaemonLog,
) {

    fun completionReport(cardId: String, summary: String): ReportedView {
        val card = requireOwnCard("completion_report", cardId)
        val event = refusing {
            AgentRequests.reportCompletion(cardId, card, session.role, summary, newId(), clock.instant())
        }
        recorder.record(cardId, card, event)
        log.log("mcp_tool", mapOf("tool" to "completion_report", "card" to cardId))
        return ReportedView(event.id.value)
    }

    fun fileWriteRequest(cardId: String, path: String, content: String): WriteView {
        val card = requireOwnCard("file_write_request", cardId)
        val project = project()
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_CONTENT_BYTES) {
            throw refused("file_write_request", cardId, "content above $MAX_CONTENT_BYTES bytes refused for card $cardId")
        }
        if (path.length > MAX_PATH_CHARS) {
            throw refused("file_write_request", cardId, "path above $MAX_PATH_CHARS characters refused for card $cardId")
        }
        val relative = normalize(project, cardId, path)
        val event = refusing {
            AgentRequests.requestWrite(cardId, card, session.role, relative, bytes.size, newId(), clock.instant())
        }
        val root = worktrees.existing(project, cardId)
            ?: throw refused("file_write_request", cardId, "card $cardId has no worktree")
        writeInside(root, relative, bytes, cardId)
        recorder.record(cardId, card, event)
        log.log("mcp_tool", mapOf("tool" to "file_write_request", "card" to cardId, "path" to relative))
        return WriteView(relative, bytes.size)
    }

    private fun normalize(project: Project, cardId: String, path: String): String {
        val parts = path.split("/").filter { it.isNotEmpty() && it != "." }
        val unsafe = path.startsWith("/") || parts.isEmpty() || ".." in parts || path.any { it.isISOControl() } ||
            parts.any { it == GIT_DIR }
        val relative = parts.joinToString("/")
        if (unsafe || ScopeGate.isProtected(project, relative)) {
            throw refused("file_write_request", cardId, "path is not writable for card $cardId")
        }
        return relative
    }

    private fun writeInside(root: Path, relative: String, bytes: ByteArray, cardId: String) {
        val realRoot = root.toRealPath()
        var current = realRoot
        val parts = relative.split("/")
        for (part in parts.dropLast(1)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) throw outside(cardId)
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current)
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw outside(cardId)
        }
        if (!current.toRealPath().startsWith(realRoot)) throw outside(cardId)
        val target = current.resolve(parts.last())
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            (Files.isSymbolicLink(target) || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
        ) {
            throw outside(cardId)
        }
        val temp = Files.createTempFile(current, ".write-", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            Files.deleteIfExists(temp)
            throw refused("file_write_request", cardId, "write failed for card $cardId")
        }
    }

    private fun outside(cardId: String): McpToolException =
        refused("file_write_request", cardId, "path leaves the worktree of card $cardId")

    private fun requireOwnCard(tool: String, cardId: String): Card {
        if (!CARD_ID.matches(cardId)) {
            log.log("mcp_refused", mapOf("tool" to tool, "reason" to "invalid_card_id"))
            throw McpToolException("argument cardId is invalid")
        }
        if (store.projectOf(cardId) != session.repo) {
            throw refused(tool, cardId, "card $cardId does not belong to project ${project().name}")
        }
        if (cardId != session.cardId) {
            throw refused(tool, cardId, "card $cardId is not the card of this session")
        }
        return store.card(cardId) ?: throw McpToolException("card $cardId not found")
    }

    private fun refused(tool: String, cardId: String, message: String): McpToolException {
        log.log("mcp_refused", mapOf("tool" to tool, "card" to cardId))
        return McpToolException(message)
    }

    private fun <T> refusing(block: () -> T): T =
        try {
            block()
        } catch (e: AgentRequestRefusedException) {
            throw McpToolException(e.message ?: "request refused")
        }

    private fun project(): Project =
        registry.list().firstOrNull { it.repo == session.repo }
            ?: throw McpToolException("project ${session.repo} is not registered")

    private fun newId(): EventId = EventId(UUID.randomUUID().toString())

    private companion object {
        val CARD_ID = Regex("[A-Za-z0-9-]{1,64}")
        const val MAX_CONTENT_BYTES = 256 * 1024
        const val MAX_PATH_CHARS = 512
        const val GIT_DIR = ".git"
    }
}
