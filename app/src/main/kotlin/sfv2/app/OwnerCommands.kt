package sfv2.app

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import sfv2.core.Column

/** What the board needs to act as the owner. Never throws: failures are a [MoveOutcome]. */
interface OwnerCommands {
    fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome
}

/** The daemon command line for an owner move; approve and request-change are the daemon's commands for Spec Review. */
fun ownerMoveRequest(cardId: String, from: Column, to: Column, note: String?, proof: String): String =
    buildJsonObject {
        when {
            from == Column.SPEC_REVIEW && to == Column.READY_FOR_DEV -> put("cmd", "card.approve")
            from == Column.SPEC_REVIEW && to == Column.READY_FOR_REFINEMENT -> {
                put("cmd", "card.request-change")
                put("note", note.orEmpty())
            }
            else -> {
                put("cmd", "card.move")
                put("to", to.name)
            }
        }
        put("id", cardId)
        put("actor", "owner")
        put("proof", proof)
    }.toString()

/** Talks to the daemon's Unix socket; reads [token] at every send and never keeps it. */
class SocketOwnerCommands(private val socket: Path, private val token: Path) : OwnerCommands {
    override fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome {
        val proof = readProof(token) ?: return MoveOutcome.NotSent(NotSentCause.NO_OWNER_PROOF)
        return try {
            parse(sendLine(socket, ownerMoveRequest(cardId, from, to, note, proof)))
        } catch (e: IOException) {
            MoveOutcome.NotSent(NotSentCause.DAEMON_UNAVAILABLE)
        }
    }

    private fun parse(line: String?): MoveOutcome {
        val reply = parseReply(line)
        if (reply.accepted()) return MoveOutcome.Accepted
        return MoveOutcome.Refused(reply.error() ?: "o daemon recusou o movimento")
    }
}

/** What the closure screen needs from the daemon. Never throws: failures are a [DraftOutcome] or a [ConfirmOutcome]. */
interface ClosureCommands {
    fun draft(cardId: String): DraftOutcome

    fun confirm(cardId: String, draftHash: String): ConfirmOutcome
}

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

fun closureDraftRequest(cardId: String): String =
    buildJsonObject {
        put("cmd", "card.close.draft")
        put("id", cardId)
    }.toString()

fun closureConfirmRequest(cardId: String, draftHash: String, proof: String): String =
    buildJsonObject {
        put("cmd", "card.close.confirm")
        put("id", cardId)
        put("actor", "owner")
        put("draftHash", draftHash)
        put("proof", proof)
    }.toString()

private const val DRAFT_CHANGED_PREFIX = "draft changed"

/** Talks to the daemon's Unix socket; reads [token] at every confirm and never keeps it. */
class SocketClosureCommands(private val socket: Path, private val token: Path) : ClosureCommands {
    override fun draft(cardId: String): DraftOutcome =
        try {
            parseDraft(sendLine(socket, closureDraftRequest(cardId)))
        } catch (e: IOException) {
            DraftOutcome.NotSent(NotSentCause.DAEMON_UNAVAILABLE)
        }

    override fun confirm(cardId: String, draftHash: String): ConfirmOutcome {
        val proof = readProof(token) ?: return ConfirmOutcome.NotSent(NotSentCause.NO_OWNER_PROOF)
        return try {
            parseConfirm(sendLine(socket, closureConfirmRequest(cardId, draftHash, proof)))
        } catch (e: IOException) {
            ConfirmOutcome.NotSent(NotSentCause.DAEMON_UNAVAILABLE)
        }
    }

    private fun parseDraft(line: String?): DraftOutcome {
        val reply = parseReply(line)
        if (!reply.accepted()) return DraftOutcome.Refused(reply.error() ?: "o daemon recusou o rascunho")
        val result = reply["result"] as? JsonObject ?: throw IOException("unreadable reply")
        fun field(key: String) = (result[key] as? JsonPrimitive)?.contentOrNull ?: throw IOException("unreadable reply")
        return DraftOutcome.Drafted(field("status"), field("summary"), field("prLink"), field("draftHash"))
    }

    private fun parseConfirm(line: String?): ConfirmOutcome {
        val reply = parseReply(line)
        if (reply.accepted()) return ConfirmOutcome.Closed
        val error = reply.error() ?: "o daemon recusou o fechamento"
        return if (error.startsWith(DRAFT_CHANGED_PREFIX)) ConfirmOutcome.DraftChanged else ConfirmOutcome.Failed(error)
    }
}

private fun readProof(token: Path): String? =
    try {
        Files.readString(token).trim().takeIf { it.isNotEmpty() }
    } catch (e: IOException) {
        null
    }

private fun sendLine(socket: Path, command: String): String? {
    val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
    try {
        channel.connect(UnixDomainSocketAddress.of(socket))
        Channels.newOutputStream(channel).apply {
            write((command + "\n").toByteArray())
            flush()
        }
        return Channels.newInputStream(channel).bufferedReader().readLine()
    } finally {
        channel.close()
    }
}

private fun parseReply(line: String?): JsonObject {
    if (line == null) throw IOException("connection closed")
    return try {
        Json.parseToJsonElement(line).jsonObject
    } catch (e: IllegalArgumentException) {
        throw IOException("unreadable reply")
    }
}

private fun JsonObject.accepted(): Boolean = (this["ok"] as? JsonPrimitive)?.contentOrNull == "true"

private fun JsonObject.error(): String? = (this["error"] as? JsonPrimitive)?.contentOrNull
