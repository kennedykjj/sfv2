package sfv2.app

import java.io.IOException
import java.math.BigDecimal
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sfv2.core.Column

/**
 * Shape the implementation must follow (see the plan's "Contrato"): `OwnerMoves.kt` (`ownerTargets`, `needsNote`,
 * `MoveOutcome`, `NotSentCause`, `BoardEdits`, `OwnerMoveState`, `MoveNotice`, `OwnerMoveController`) and
 * `OwnerCommands.kt` (`OwnerCommands`, `ownerMoveRequest`, `SocketOwnerCommands`). No UI, no sleeps.
 *
 * Card view actions (#67), also in `OwnerMoves.kt`: `SpecReviewController(commands: OwnerCommands, io: CoroutineDispatcher)`
 * with `state: StateFlow<SpecReviewState(notePending, sending, sent, notice)>`; `suspend approve(cardId)`;
 * `suspend requestChange(cardId, note: String?)` (a blank note opens the note field and sends nothing); `cancel()`;
 * `dismissNotice()`; `onDetail(detail: CardDetail)` clears `sent` once the card left `SPEC_REVIEW`. There is no
 * optimistic edit: the column only changes when the daemon event reaches the detail. The pure
 * `specReviewActionsVisible(state: CardDetailState)` is true only for a loaded card in `SPEC_REVIEW`.
 */
class OwnerMovesTest {
    @Test
    fun `AC-1 the owner moves are exactly the four owner-only ones`() {
        assertThat(ownerTargets(Column.INBOX)).containsExactly(Column.READY_FOR_REFINEMENT)
        assertThat(ownerTargets(Column.SPEC_REVIEW)).containsExactlyInAnyOrder(Column.READY_FOR_DEV, Column.READY_FOR_REFINEMENT)
        assertThat(ownerTargets(Column.STAGED)).containsExactly(Column.READY_FOR_PROD)
    }

    @Test
    fun `AC-1 a drop sends the move to the daemon and the card is in the new column right away`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.STAGED), BoardFixtures.aCard("b", Column.READY_FOR_PROD))
        var columnWhileInFlight: List<String> = emptyList()
        fx.commands.reply = {
            columnWhileInFlight = fx.edits.current.cardsIn(Column.READY_FOR_PROD).map { it.id }
            MoveOutcome.Accepted
        }

        fx.controller.hold("a")
        fx.controller.chooseTarget(Column.READY_FOR_PROD)
        fx.controller.submit()

        assertThat(fx.commands.calls).containsExactly(SentMove("a", Column.STAGED, Column.READY_FOR_PROD, null))
        assertThat(columnWhileInFlight).containsExactly("b", "a")
        assertThat(fx.edits.current.cardsIn(Column.READY_FOR_PROD).map { it.id }).containsExactly("b", "a")
        assertThat(fx.edits.current.cardsIn(Column.STAGED)).isEmpty()
        assertThat(fx.controller.state.value.held).isNull()
        assertThat(fx.controller.state.value.notice).isNull()
    }

    @Test
    fun `AC-1 the request carries the owner actor and proof and picks the daemon command for the move`() {
        fun parse(from: Column, to: Column, note: String? = null): JsonObject =
            Json.parseToJsonElement(ownerMoveRequest("c1", from, to, note, "tok-123")).jsonObject

        val move = parse(Column.STAGED, Column.READY_FOR_PROD)
        assertThat(move["cmd"]?.jsonPrimitive?.content).isEqualTo("card.move")
        assertThat(move["to"]?.jsonPrimitive?.content).isEqualTo("READY_FOR_PROD")
        assertThat(parse(Column.INBOX, Column.READY_FOR_REFINEMENT)["cmd"]?.jsonPrimitive?.content).isEqualTo("card.move")

        val approve = parse(Column.SPEC_REVIEW, Column.READY_FOR_DEV)
        assertThat(approve["cmd"]?.jsonPrimitive?.content).isEqualTo("card.approve")

        val change = parse(Column.SPEC_REVIEW, Column.READY_FOR_REFINEMENT, "cover offline")
        assertThat(change["cmd"]?.jsonPrimitive?.content).isEqualTo("card.request-change")
        assertThat(change["note"]?.jsonPrimitive?.content).isEqualTo("cover offline")

        listOf(move, approve, change).forEach {
            assertThat(it["actor"]?.jsonPrimitive?.content).isEqualTo("owner")
            assertThat(it["proof"]?.jsonPrimitive?.content).isEqualTo("tok-123")
            assertThat(it["id"]?.jsonPrimitive?.content).isEqualTo("c1")
        }
    }

    @Test
    fun `AC-1 the socket commands send the request with the token read from disk and read the daemon answer`() {
        val dir = Files.createTempDirectory("sfv2m")
        Files.writeString(dir.resolve("owner.token"), "secret-token\n")
        val daemon = FakeDaemon(dir.resolve("daemon.sock")) { """{"ok":true,"result":{"id":"a","title":"t","column":"READY_FOR_PROD"}}""" }
        try {
            val commands = SocketOwnerCommands(dir.resolve("daemon.sock"), dir.resolve("owner.token"))

            val outcome = commands.move("a", Column.STAGED, Column.READY_FOR_PROD, null)

            assertThat(outcome).isEqualTo(MoveOutcome.Accepted)
            val sent = Json.parseToJsonElement(daemon.requests.single()).jsonObject
            assertThat(sent["cmd"]?.jsonPrimitive?.content).isEqualTo("card.move")
            assertThat(sent["proof"]?.jsonPrimitive?.content).isEqualTo("secret-token")
            assertThat(sent["actor"]?.jsonPrimitive?.contentOrNull).isEqualTo("owner")
        } finally {
            daemon.close()
        }
    }

    @Test
    fun `AC-4 a daemon error answer is a refusal carrying the daemon reason`() {
        val dir = Files.createTempDirectory("sfv2m")
        Files.writeString(dir.resolve("owner.token"), "secret-token\n")
        val daemon = FakeDaemon(dir.resolve("daemon.sock")) { """{"ok":false,"error":"move not allowed"}""" }
        try {
            val outcome = SocketOwnerCommands(dir.resolve("daemon.sock"), dir.resolve("owner.token"))
                .move("a", Column.STAGED, Column.READY_FOR_PROD, null)

            assertThat(outcome).isEqualTo(MoveOutcome.Refused("move not allowed"))
        } finally {
            daemon.close()
        }
    }

    @Test
    fun `AC-2 M offers only the allowed destinations, a choice is sent on confirm and Esc sends nothing`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.SPEC_REVIEW))

        fx.controller.hold("a")
        val held = fx.controller.state.value
        assertThat(held.held?.id).isEqualTo("a")
        assertThat(held.targets).containsExactlyInAnyOrder(Column.READY_FOR_DEV, Column.READY_FOR_REFINEMENT)

        fx.controller.chooseTarget(Column.CODING)
        assertThat(fx.controller.state.value.selected).isNotEqualTo(Column.CODING)

        fx.controller.chooseTarget(Column.READY_FOR_DEV)
        assertThat(fx.controller.state.value.selected).isEqualTo(Column.READY_FOR_DEV)
        fx.controller.submit()
        assertThat(fx.commands.calls).containsExactly(SentMove("a", Column.SPEC_REVIEW, Column.READY_FOR_DEV, null))
        assertThat(fx.edits.current.cardsIn(Column.READY_FOR_DEV).map { it.id }).containsExactly("a")
    }

    @Test
    fun `AC-2 cancelling drops the held card without sending or moving anything`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.SPEC_REVIEW))
        val before = fx.edits.current

        fx.controller.hold("a")
        fx.controller.chooseTarget(Column.READY_FOR_DEV)
        fx.controller.cancel()
        fx.controller.submit()

        assertThat(fx.commands.calls).isEmpty()
        assertThat(fx.edits.current).isEqualTo(before)
        assertThat(fx.controller.state.value.held).isNull()
        assertThat(fx.controller.state.value.status).isEqualTo("Nada enviado.")
    }

    @Test
    fun `AC-3 only Spec Review to Ready for Refinement needs a note`() {
        assertThat(needsNote(Column.SPEC_REVIEW, Column.READY_FOR_REFINEMENT)).isTrue()
        assertThat(needsNote(Column.SPEC_REVIEW, Column.READY_FOR_DEV)).isFalse()
        assertThat(needsNote(Column.INBOX, Column.READY_FOR_REFINEMENT)).isFalse()
        assertThat(needsNote(Column.STAGED, Column.READY_FOR_PROD)).isFalse()
    }

    @Test
    fun `AC-3 sending back without a note does not send, and with a note the card returns carrying it`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.SPEC_REVIEW))
        val before = fx.edits.current
        fx.controller.hold("a")
        fx.controller.chooseTarget(Column.READY_FOR_REFINEMENT)

        fx.controller.submit(null)
        fx.controller.submit("   ")

        assertThat(fx.commands.calls).isEmpty()
        assertThat(fx.edits.current).isEqualTo(before)
        assertThat(fx.controller.state.value.notePending).isTrue()

        fx.controller.submit("  cover offline  ")

        assertThat(fx.commands.calls)
            .containsExactly(SentMove("a", Column.SPEC_REVIEW, Column.READY_FOR_REFINEMENT, "cover offline"))
        assertThat(fx.edits.current.cardsIn(Column.READY_FOR_REFINEMENT).map { it.id }).containsExactly("a")
    }

    @Test
    fun `AC-4 a refused move puts the card back and shows the daemon reason`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.STAGED))
        fx.commands.reply = { MoveOutcome.Refused("card is not in Staged") }

        fx.controller.hold("a")
        fx.controller.chooseTarget(Column.READY_FOR_PROD)
        fx.controller.submit()

        assertThat(fx.edits.current.cardsIn(Column.STAGED).map { it.id }).containsExactly("a")
        assertThat(fx.edits.current.cardsIn(Column.READY_FOR_PROD)).isEmpty()
        assertThat(fx.controller.state.value.notice)
            .isEqualTo(MoveNotice(NoticeKind.REFUSED, "Não movido: card is not in Staged"))

        fx.controller.dismissNotice()
        assertThat(fx.controller.state.value.notice).isNull()
    }

    @Test
    fun `AC-4 a refusal does not overwrite the column a daemon event already took the card to`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.STAGED))
        fx.commands.reply = {
            fx.edits.edit { it.copy(cards = it.cards.map { c -> c.copy(column = Column.DONE) }) }
            MoveOutcome.Refused("too late")
        }

        fx.controller.hold("a")
        fx.controller.chooseTarget(Column.READY_FOR_PROD)
        fx.controller.submit()

        assertThat(fx.edits.current.cardsIn(Column.DONE).map { it.id }).containsExactly("a")
        assertThat(fx.controller.state.value.notice?.text).isEqualTo("Não movido: too late")
    }

    @Test
    fun `AC-5 a card without an owner move is not held, offers no destination and sends nothing`() = runBlocking {
        val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.CODING))
        val before = fx.edits.current

        fx.controller.hold("a")
        fx.controller.chooseTarget(Column.DONE)
        fx.controller.submit()

        val state = fx.controller.state.value
        assertThat(state.held).isNull()
        assertThat(state.targets).isEmpty()
        assertThat(state.status).isEqualTo("Este card não tem movimento seu.")
        assertThat(ownerTargets(Column.CODING)).isEmpty()
        assertThat(fx.commands.calls).isEmpty()
        assertThat(fx.edits.current).isEqualTo(before)
    }

    @Test
    fun `AC-6 an unavailable daemon or a missing proof puts the card back and says it was not sent`() = runBlocking {
        val cases = mapOf(
            NotSentCause.DAEMON_UNAVAILABLE to "Movimento não enviado: o daemon está indisponível.",
            NotSentCause.NO_OWNER_PROOF to "Movimento não enviado: prova do dono não encontrada.",
        )
        cases.forEach { (cause, text) ->
            val fx = ControllerFixtures(BoardFixtures.aCard("a", Column.INBOX))
            fx.commands.reply = { MoveOutcome.NotSent(cause) }

            fx.controller.hold("a")
            fx.controller.chooseTarget(Column.READY_FOR_REFINEMENT)
            fx.controller.submit()

            assertThat(fx.edits.current.cardsIn(Column.INBOX).map { it.id }).containsExactly("a")
            assertThat(fx.controller.state.value.notice).isEqualTo(MoveNotice(NoticeKind.NOT_SENT, text))
        }
    }

    @Test
    fun `AC-6 the socket commands report no daemon, and no token without touching the daemon`() {
        val dir = Files.createTempDirectory("sfv2m")
        val socket = dir.resolve("daemon.sock")
        val token = dir.resolve("owner.token")

        // Token present, nothing listening.
        Files.writeString(token, "tok\n")
        assertThat(SocketOwnerCommands(socket, token).move("a", Column.STAGED, Column.READY_FOR_PROD, null))
            .isEqualTo(MoveOutcome.NotSent(NotSentCause.DAEMON_UNAVAILABLE))

        // Token missing or empty, daemon up: nothing is sent.
        val daemon = FakeDaemon(socket) { """{"ok":true,"result":{}}""" }
        try {
            Files.delete(token)
            assertThat(SocketOwnerCommands(socket, token).move("a", Column.STAGED, Column.READY_FOR_PROD, null))
                .isEqualTo(MoveOutcome.NotSent(NotSentCause.NO_OWNER_PROOF))
            Files.writeString(token, "  \n")
            assertThat(SocketOwnerCommands(socket, token).move("a", Column.STAGED, Column.READY_FOR_PROD, null))
                .isEqualTo(MoveOutcome.NotSent(NotSentCause.NO_OWNER_PROOF))
            assertThat(daemon.requests).isEmpty()
        } finally {
            daemon.close()
        }
    }

    @Test
    fun `AC-1 the card view shows the actions only for a loaded card in Spec Review`() {
        assertThat(specReviewActionsVisible(CardDetailState.Loaded(SpecReviewFixtures.aDetail(Column.SPEC_REVIEW)))).isTrue()

        Column.entries.filter { it != Column.SPEC_REVIEW }.forEach {
            assertThat(specReviewActionsVisible(CardDetailState.Loaded(SpecReviewFixtures.aDetail(it)))).isFalse()
        }
        assertThat(specReviewActionsVisible(CardDetailState.Loading)).isFalse()
        assertThat(specReviewActionsVisible(CardDetailState.NotFound)).isFalse()
        assertThat(specReviewActionsVisible(CardDetailState.Disconnected)).isFalse()
    }

    @Test
    fun `AC-2 approving sends card approve as the owner and shows the approval as sent without moving the column`() = runBlocking {
        val fx = SpecReviewFixtures.controller()
        var stateWhileInFlight: SpecReviewState? = null
        fx.commands.reply = {
            stateWhileInFlight = fx.controller.state.value
            MoveOutcome.Accepted
        }

        fx.controller.approve("a")

        assertThat(fx.commands.calls).containsExactly(SentMove("a", Column.SPEC_REVIEW, Column.READY_FOR_DEV, null))
        assertThat(stateWhileInFlight?.sending).isTrue()
        val state = fx.controller.state.value
        assertThat(state.sent).isTrue()
        assertThat(state.sending).isFalse()
        assertThat(state.notice).isNull()
    }

    @Test
    fun `AC-3 asking for a change without a note sends nothing and keeps asking for the note`() = runBlocking {
        val fx = SpecReviewFixtures.controller()

        fx.controller.requestChange("a", null)
        assertThat(fx.controller.state.value.notePending).isTrue()
        fx.controller.requestChange("a", "   ")
        fx.controller.requestChange("a", "\n\t ")

        assertThat(fx.commands.calls).isEmpty()
        assertThat(fx.controller.state.value.notePending).isTrue()
        assertThat(fx.controller.state.value.sending).isFalse()
        assertThat(fx.controller.state.value.sent).isFalse()
    }

    @Test
    fun `AC-3 asking for a change with a note sends card request-change with the trimmed note`() = runBlocking {
        val fx = SpecReviewFixtures.controller()

        fx.controller.requestChange("a", "  cover offline  ")

        assertThat(fx.commands.calls)
            .containsExactly(SentMove("a", Column.SPEC_REVIEW, Column.READY_FOR_REFINEMENT, "cover offline"))
        assertThat(fx.controller.state.value.notePending).isFalse()
        assertThat(fx.controller.state.value.sent).isTrue()
    }

    @Test
    fun `AC-3 cancelling the note closes the field and sends nothing`() = runBlocking {
        val fx = SpecReviewFixtures.controller()
        fx.controller.requestChange("a", null)

        fx.controller.cancel()

        assertThat(fx.controller.state.value).isEqualTo(SpecReviewState())
        assertThat(fx.commands.calls).isEmpty()
    }

    @Test
    fun `AC-4 a refused approval shows the daemon reason, frees the actions and is dismissable`() = runBlocking {
        val fx = SpecReviewFixtures.controller()
        fx.commands.reply = { MoveOutcome.Refused("card is not in Spec Review") }

        fx.controller.approve("a")

        val state = fx.controller.state.value
        assertThat(state.notice).isEqualTo(MoveNotice(NoticeKind.REFUSED, "Não movido: card is not in Spec Review"))
        assertThat(state.sending).isFalse()
        assertThat(state.sent).isFalse()

        fx.controller.dismissNotice()
        assertThat(fx.controller.state.value.notice).isNull()
    }

    @Test
    fun `AC-4 an unavailable daemon or a missing proof says not sent with the cause and frees the actions`() = runBlocking {
        val cases = mapOf(
            NotSentCause.DAEMON_UNAVAILABLE to "Movimento não enviado: o daemon está indisponível.",
            NotSentCause.NO_OWNER_PROOF to "Movimento não enviado: prova do dono não encontrada.",
        )
        cases.forEach { (cause, text) ->
            val fx = SpecReviewFixtures.controller()
            fx.commands.reply = { MoveOutcome.NotSent(cause) }

            fx.controller.requestChange("a", "cover offline")

            val state = fx.controller.state.value
            assertThat(state.notice).isEqualTo(MoveNotice(NoticeKind.NOT_SENT, text))
            assertThat(state.sending).isFalse()
            assertThat(state.sent).isFalse()
        }
    }

    @Test
    fun `AC-4 after a refusal the owner can act again and the next accepted command clears the notice`() = runBlocking {
        val fx = SpecReviewFixtures.controller()
        fx.commands.reply = { MoveOutcome.Refused("busy") }
        fx.controller.approve("a")
        fx.commands.reply = { MoveOutcome.Accepted }

        fx.controller.approve("a")

        assertThat(fx.commands.calls).hasSize(2)
        assertThat(fx.controller.state.value.notice).isNull()
        assertThat(fx.controller.state.value.sent).isTrue()
    }

    @Test
    fun `AC-5 a second click while a command is in flight sends nothing new`() = runBlocking {
        val fx = SpecReviewFixtures.controller()
        fx.commands.reply = {
            runBlocking {
                fx.controller.approve("a")
                fx.controller.requestChange("a", "cover offline")
            }
            MoveOutcome.Accepted
        }

        fx.controller.approve("a")

        assertThat(fx.commands.calls).containsExactly(SentMove("a", Column.SPEC_REVIEW, Column.READY_FOR_DEV, null))
    }
}

object SpecReviewFixtures {
    class Controller(val commands: FakeOwnerCommands, val controller: SpecReviewController)

    fun controller(): Controller {
        val commands = FakeOwnerCommands()
        return Controller(commands, SpecReviewController(commands, Dispatchers.Unconfined))
    }

    fun aDetail(column: Column, events: List<TimelineEvent> = emptyList()) = CardDetail(
        id = "a",
        title = "A card",
        column = column.name,
        spec = SpecView("valid", "Do the thing"),
        events = events,
        eventsTruncated = false,
        runs = emptyList(),
        total = CostTotal(CostStatus.KNOWN, BigDecimal.ZERO),
    )
}

data class SentMove(val cardId: String, val from: Column, val to: Column, val note: String?)

class FakeOwnerCommands : OwnerCommands {
    val calls = mutableListOf<SentMove>()
    var reply: () -> MoveOutcome = { MoveOutcome.Accepted }

    override fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome {
        calls += SentMove(cardId, from, to, note)
        return reply()
    }
}

class FakeBoardEdits(var current: Board) : BoardEdits {
    override fun board(): Board? = current

    override fun edit(transform: (Board) -> Board) {
        current = transform(current)
    }
}

class ControllerFixtures(vararg cards: BoardCard) {
    val commands = FakeOwnerCommands()
    val edits = FakeBoardEdits(BoardFixtures.empty().applySnapshot(cards.toList()))
    val controller = OwnerMoveController(commands, edits, Dispatchers.Unconfined)
}

/** One request per connection, like the real daemon; [reply] gives the one answer line. */
class FakeDaemon(socket: Path, private val reply: (String) -> String) {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).also { it.bind(UnixDomainSocketAddress.of(socket)) }

    init {
        Thread.ofPlatform().daemon().start {
            try {
                while (true) {
                    val client = server.accept()
                    val line = Channels.newInputStream(client).bufferedReader().readLine()
                    if (line != null) {
                        requests.add(line)
                        Channels.newOutputStream(client).apply {
                            write((reply(line) + "\n").toByteArray())
                            flush()
                        }
                    }
                    client.close()
                }
            } catch (e: IOException) {
                // closed by the test
            }
        }
    }

    fun close() = server.close()
}
