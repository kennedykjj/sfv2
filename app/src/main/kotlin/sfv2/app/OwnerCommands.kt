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
        val proof = try {
            Files.readString(token).trim().takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            null
        } ?: return MoveOutcome.NotSent(NotSentCause.NO_OWNER_PROOF)
        return try {
            parse(send(ownerMoveRequest(cardId, from, to, note, proof)))
        } catch (e: IOException) {
            MoveOutcome.NotSent(NotSentCause.DAEMON_UNAVAILABLE)
        }
    }

    private fun send(command: String): String? {
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

    private fun parse(line: String?): MoveOutcome {
        if (line == null) throw IOException("connection closed")
        val reply: JsonObject = try {
            Json.parseToJsonElement(line).jsonObject
        } catch (e: IllegalArgumentException) {
            throw IOException("unreadable reply")
        }
        if ((reply["ok"] as? JsonPrimitive)?.contentOrNull == "true") return MoveOutcome.Accepted
        val reason = (reply["error"] as? JsonPrimitive)?.contentOrNull ?: "o daemon recusou o movimento"
        return MoveOutcome.Refused(reason)
    }
}
