package sfv2.daemon

import java.io.IOException
import java.net.StandardProtocolFamily
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.MoveDecision
import sfv2.core.MoveRules
import sfv2.store.CardEventStore
import sfv2.store.Database
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SocketServerTest {

    @TempDir
    lateinit var runtimeDir: Path

    private val servers = mutableListOf<SocketServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { runCatching { it.close() } }
    }

    private fun socketPath(): Path = resolveSocketPath(mapOf("XDG_RUNTIME_DIR" to runtimeDir.toString()))

    private fun startServer(): SocketServer = SocketServer.start(socketPath()).also { servers.add(it) }

    private fun exchange(line: String): String {
        SocketChannel.open(UnixDomainSocketAddress.of(socketPath())).use { channel ->
            val writer = Channels.newOutputStream(channel)
            writer.write((line + "\n").toByteArray(Charsets.UTF_8))
            writer.flush()
            return Channels.newInputStream(channel).bufferedReader(Charsets.UTF_8).readLine()
        }
    }

    @Test
    fun `AC-1 ping returns pong`() {
        startServer()

        val reply = exchange("""{"cmd":"ping"}""")

        assertThat(reply).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `AC-2 second daemon fails and the first keeps serving`() {
        startServer()

        val failure = assertThrows(DaemonAlreadyRunningException::class.java) { SocketServer.start(socketPath()) }

        assertThat(failure.message).contains(socketPath().toString())
        assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `AC-3 invalid json and unknown command give an error and the daemon keeps serving`() {
        startServer()

        val notJson = exchange("this is not json")
        val unknown = exchange("""{"cmd":"explode"}""")
        val missing = exchange("""{"foo":1}""")

        for (reply in listOf(notJson, unknown, missing)) {
            assertThat(reply).startsWith("""{"ok":false,"error":"""")
        }
        assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `AC-3 line above 64 KiB is an error and closes the connection`() {
        startServer()

        val reply = exchange("x".repeat(70 * 1024))

        assertThat(reply).startsWith("""{"ok":false,"error":"""")
        assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `AC-4 several messages on one connection each get a single json line`() {
        startServer()

        SocketChannel.open(UnixDomainSocketAddress.of(socketPath())).use { channel ->
            val out = Channels.newOutputStream(channel)
            val reader = Channels.newInputStream(channel).bufferedReader(Charsets.UTF_8)
            out.write("{\"cmd\":\"ping\"}\n{\"cmd\":\"nope\"}\n".toByteArray(Charsets.UTF_8))
            out.flush()

            val first = reader.readLine()
            val second = reader.readLine()

            assertThat(first).isEqualTo("""{"ok":true,"result":"pong"}""")
            assertThat(second).startsWith("{").endsWith("}").contains(""""ok":false""")
        }
    }

    @Test
    fun `AC-5 socket lives under XDG_RUNTIME_DIR and is owner only`() {
        startServer()

        val path = socketPath()
        assertThat(path).startsWith(runtimeDir)
        assertThat(Files.exists(path)).isTrue()
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path))).isEqualTo("rw-------")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path.parent))).isEqualTo("rwx------")
    }

    @Test
    fun `AC-5 without XDG_RUNTIME_DIR the daemon refuses to start naming the variable`() {
        val failure = assertThrows(DaemonStartException::class.java) { resolveSocketPath(emptyMap()) }

        assertThat(failure.message).contains("XDG_RUNTIME_DIR")
    }

    @Test
    fun `AC-6 new daemon takes over an orphan socket`() {
        val path = socketPath()
        Files.createDirectories(path.parent)
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { it.bind(UnixDomainSocketAddress.of(path)) }
        assertThat(Files.exists(path)).isTrue()

        startServer()

        assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    private class RecordingLog : DaemonLog {
        val entries = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        private val latch = CountDownLatch(1)

        override fun log(event: String, fields: Map<String, String>) {
            entries.add(event to fields)
            latch.countDown()
        }

        fun awaitFirst() = latch.await(5, TimeUnit.SECONDS)
    }

    @Test
    fun `AC-1 hardening lock excludes a second start even when the socket file looks orphaned`() {
        val first = startServer()
        Files.delete(socketPath())

        val failure = assertThrows(DaemonAlreadyRunningException::class.java) { SocketServer.start(socketPath()) }

        assertThat(failure.message).isNotBlank()
        val lockFile = socketPath().resolveSibling("daemon.lock")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(lockFile))).isEqualTo("rw-------")
        first.close()
        assertThat(Files.exists(lockFile)).isTrue()
        startServer()
        assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `AC-2 hardening client error is logged with the error type only and the server keeps serving`() {
        val log = RecordingLog()
        servers.add(SocketServer.start(socketPath(), log = log))

        SocketChannel.open(UnixDomainSocketAddress.of(socketPath())).use { channel ->
            val out = Channels.newOutputStream(channel)
            out.write("{\"cmd\":\"ping\",\"note\":\"TOPSECRET\"}\n".toByteArray(Charsets.UTF_8))
            out.flush()
        }

        assertThat(log.awaitFirst()).isTrue()
        val (event, fields) = log.entries.first()
        assertThat(event).isEqualTo("client_error")
        assertThat(fields.keys).containsExactly("error")
        assertThat(fields.getValue("error")).isNotBlank().doesNotContain("TOPSECRET").doesNotContain(" ")
        assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    private fun connectedChannel(): SocketChannel {
        val pairPath = runtimeDir.resolve("pair.sock")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { listener ->
            listener.bind(UnixDomainSocketAddress.of(pairPath))
            val client = SocketChannel.open(UnixDomainSocketAddress.of(pairPath))
            listener.accept().close()
            return client
        }
    }

    @Test
    fun `AC-3 hardening failing accept backs off with a growing capped pause and logs each failure`() {
        val pauses = mutableListOf<Long>()
        val log = RecordingLog()
        var calls = 0
        val loop = AcceptLoop(
            accept = {
                calls++
                if (calls <= 9) throw IOException("too many open files") else throw ClosedChannelException()
            },
            log = log,
            pause = { pauses.add(it) },
            onClient = { },
        )

        loop.run()

        assertThat(pauses).hasSize(9)
        assertThat(pauses.first()).isEqualTo(10L)
        assertThat(pauses).isSorted()
        assertThat(pauses.max()).isEqualTo(1000L)
        assertThat(acceptBackoffMillis(500)).isEqualTo(1000L)
        assertThat(log.entries.map { it.first }).containsOnly("accept_failed")
        assertThat(log.entries).hasSize(9)
        assertThat(log.entries.map { it.second.getValue("error") }).containsOnly("IOException")
        val attempts = log.entries.map { it.second.getValue("attempt").toInt() }
        assertThat(attempts.zipWithNext { a, b -> b - a }).containsOnly(1)
    }

    @Test
    fun `AC-4 hardening accept that recovers serves the client and restarts the backoff`() {
        val pauses = mutableListOf<Long>()
        val served = mutableListOf<SocketChannel>()
        val client = connectedChannel()
        var calls = 0
        val loop = AcceptLoop(
            accept = {
                calls++
                when (calls) {
                    1, 2, 3 -> throw IOException("fail")
                    4 -> client
                    5, 6 -> throw IOException("fail again")
                    else -> throw ClosedChannelException()
                }
            },
            log = RecordingLog(),
            pause = { pauses.add(it) },
            onClient = { served.add(it) },
        )

        loop.run()

        assertThat(served).containsExactly(client)
        assertThat(pauses).containsExactly(10L, 20L, 40L, 10L, 20L)
        client.close()
    }

    private val fixedNow = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private fun withCardServer(block: (CardEventStore) -> Unit) {
        Database.open(runtimeDir.resolve("cards.db")).use { db ->
            val store = CardEventStore(db)
            startCardServer(store)
            block(store)
        }
    }

    private val tokenPath: Path get() = runtimeDir.resolve("owner.token")

    /** The owner proof of the running card server, read the way only the owner can. */
    private lateinit var proof: String

    private val cardLog = RecordingLog()

    private fun startCardServer(store: CardEventStore) {
        val ownerProof = OwnerProof.create(tokenPath, SecureRandom())
        proof = Files.readString(tokenPath).trim()
        servers.add(SocketServer.start(socketPath(), commands = CardCommands(store, clock, ownerProof, cardLog)))
    }

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    private fun reply(line: String): JsonObject = Json.parseToJsonElement(exchange(line)).jsonObject

    private fun createCard(title: String): String {
        val created = reply("""{"cmd":"card.create","actor":"owner","proof":"$proof","title":"$title"}""")
        return created.getValue("result").jsonObject.str("id")
    }

    private fun seed(store: CardEventStore, id: String, column: Column) {
        val event = Event(EventId("seed-$id"), fixedNow, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Seeded"))
        store.recordChange(id, Card.create("Seeded", column), event)
    }

    private fun ok(reply: JsonObject): Boolean = reply.getValue("ok").jsonPrimitive.boolean

    private fun badProofs(): List<String> = listOf(
        "",
        "deadbeef",
        proof.dropLast(1),
        proof + "0",
        proof.uppercase().let { if (it == proof) proof.replaceFirst(proof[0], if (proof[0] == 'a') 'b' else 'a') else it },
    )

    private fun withProof(json: String, value: String?): String =
        if (value == null) json else json.removeSuffix("}") + ""","proof":"$value"}"""

    @Test
    fun `AC-1 owner create without proof or with a bad proof is refused and stores nothing`() = withCardServer { store ->
        val attempts = listOf<String?>(null) + badProofs()

        for (attempt in attempts) {
            val refused = reply(withProof("""{"cmd":"card.create","actor":"owner","title":"Forged"}""", attempt))
            assertThat(ok(refused)).isFalse()
            assertThat(refused.str("error")).isEqualTo("owner proof missing or invalid")
        }

        assertThat(store.cards()).isEmpty()
    }

    @Test
    fun `AC-1 owner move without proof or with a bad proof is refused and writes nothing`() = withCardServer { store ->
        val inbox = createCard("Inbox card")
        seed(store, "spec", Column.SPEC_REVIEW)
        seed(store, "staged", Column.STAGED)
        val moves = listOf(
            """{"cmd":"card.move","actor":"owner","id":"$inbox","to":"READY_FOR_REFINEMENT"}""",
            """{"cmd":"card.move","actor":"owner","id":"spec","to":"READY_FOR_DEV"}""",
            """{"cmd":"card.move","actor":"owner","id":"staged","to":"READY_FOR_PROD"}""",
        )
        val before = listOf(inbox, "spec", "staged").associateWith { store.card(it) to store.events(it) }

        for (move in moves) {
            for (attempt in listOf<String?>(null) + badProofs()) {
                val refused = reply(withProof(move, attempt))
                assertThat(ok(refused)).isFalse()
                assertThat(refused.str("error")).isEqualTo("owner proof missing or invalid")
            }
        }

        assertThat(before.keys.associateWith { store.card(it) to store.events(it) }).isEqualTo(before)
    }

    @Test
    fun `AC-1 refusal is the same for an existing and a missing card and the log has only cmd and id`() =
        withCardServer { _ ->
            val id = createCard("Exists")
            val existing = reply("""{"cmd":"card.move","actor":"owner","id":"$id","to":"READY_FOR_REFINEMENT","proof":"nope"}""")
            val missing = reply("""{"cmd":"card.move","actor":"owner","id":"ghost","to":"READY_FOR_REFINEMENT","proof":"nope"}""")

            assertThat(existing).isEqualTo(missing)
            val refusals = cardLog.entries.filter { it.first == "card_command_refused" }
            assertThat(refusals).isNotEmpty()
            for ((_, fields) in refusals) {
                assertThat(fields.keys).containsOnly("cmd", "card")
            }
        }

    @Test
    fun `AC-2 owner request with the valid proof is accepted and the event records the owner`() = withCardServer { store ->
        val id = createCard("Mine")
        seed(store, "spec", Column.SPEC_REVIEW)

        val moved = reply("""{"cmd":"card.move","actor":"owner","id":"$id","to":"READY_FOR_REFINEMENT","proof":"$proof"}""")
        val approved = reply("""{"cmd":"card.move","actor":"owner","id":"spec","to":"READY_FOR_DEV","proof":"$proof"}""")

        assertThat(ok(moved)).isTrue()
        assertThat(ok(approved)).isTrue()
        assertThat(store.events(id).map { it.actor }).containsOnly(Actor.Owner)
        assertThat(store.card("spec")?.column).isEqualTo(Column.READY_FOR_DEV)
        assertThat(store.events("spec").last().actor).isEqualTo(Actor.Owner)
    }

    @Test
    fun `AC-2 agent with the valid proof does not become the owner`() = withCardServer { store ->
        val id = createCard("Mine")

        val attempt = reply("""{"cmd":"card.move","actor":"agent:dev","id":"$id","to":"READY_FOR_REFINEMENT","proof":"$proof"}""")

        assertThat(ok(attempt)).isFalse()
        assertThat(store.card(id)?.column).isEqualTo(Column.INBOX)
        assertThat(store.events(id).map { it.actor }).containsOnly(Actor.Owner)
    }

    @Test
    fun `AC-3 token file is owner only and no reply or log carries the token`() = withCardServer { _ ->
        val id = createCard("Mine")
        val replies = listOf(
            exchange("""{"cmd":"card.create","actor":"owner","title":"x","proof":"$proof"}"""),
            exchange("""{"cmd":"card.move","actor":"owner","id":"$id","to":"CODING","proof":"$proof"}"""),
            exchange("""{"cmd":"card.move","actor":"owner","id":"$id","to":"CODING","proof":"wrong"}"""),
            exchange("""{"cmd":"card.show","id":"$id"}"""),
            exchange("""{"cmd":"card.list"}"""),
            exchange("""{"cmd":"proof"}"""),
            exchange("""{"cmd":"owner.token"}"""),
            exchange("""{"cmd":"ping"}"""),
        )

        assertThat(proof).matches("[0-9a-f]{64}")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(tokenPath))).isEqualTo("rw-------")
        for (text in replies) assertThat(text).doesNotContain(proof)
        for ((event, fields) in cardLog.entries) {
            assertThat(event).doesNotContain(proof)
            assertThat(fields.values.joinToString(" ")).doesNotContain(proof)
        }
    }

    @Test
    fun `AC-3 OwnerProof writes a private file in a private directory and a new token each start`() {
        val nested = runtimeDir.resolve("fresh").resolve("owner.token")
        val first = OwnerProof.create(nested, SecureRandom())
        val firstToken = Files.readString(nested).trim()
        val second = OwnerProof.create(nested, SecureRandom())
        val secondToken = Files.readString(nested).trim()

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(nested.parent))).isEqualTo("rwx------")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(nested))).isEqualTo("rw-------")
        assertThat(firstToken).matches("[0-9a-f]{64}").isNotEqualTo(secondToken)
        assertThat(first.matches(firstToken)).isTrue()
        assertThat(first.matches(secondToken)).isFalse()
        assertThat(second.matches(secondToken)).isTrue()
        assertThat(second.matches(null)).isFalse()
        assertThat(second.matches("")).isFalse()
        assertThat(second.toString()).doesNotContain(secondToken)
    }

    @Test
    fun `AC-1 card create stores the card and a creation event by the owner`() = withCardServer { store ->
        val created = reply("""{"cmd":"card.create","actor":"owner","proof":"$proof","title":"Write docs"}""")

        assertThat(created.getValue("ok").jsonPrimitive.boolean).isTrue()
        val result = created.getValue("result").jsonObject
        assertThat(result.str("title")).isEqualTo("Write docs")
        assertThat(result.str("column")).isEqualTo("INBOX")
        val id = result.str("id")
        assertThat(store.card(id)).isEqualTo(Card.create("Write docs", Column.INBOX))
        val events = store.events(id)
        assertThat(events).hasSize(1)
        assertThat(events.single().type).isEqualTo(EventType.CARD_CREATED)
        assertThat(events.single().actor).isEqualTo(Actor.Owner)
        assertThat(events.single().at).isEqualTo(fixedNow)
        assertThat(events.single().payload).containsEntry("title", "Write docs")
    }

    @Test
    fun `AC-2 refused move writes neither card nor event`() = withCardServer { store ->
        val id = createCard("Write docs")
        val cardBefore = store.card(id)
        val eventsBefore = store.events(id)

        reply("""{"cmd":"card.move","actor":"owner","proof":"$proof","id":"$id","to":"CODING"}""")
        reply("""{"cmd":"card.move","actor":"agent:dev","id":"$id","to":"READY_FOR_REFINEMENT"}""")

        assertThat(store.card(id)).isEqualTo(cardBefore)
        assertThat(store.events(id)).isEqualTo(eventsBefore)
    }

    @Test
    fun `AC-2 allowed move updates the card and records who moved it`() = withCardServer { store ->
        val id = createCard("Write docs")

        val moved = reply("""{"cmd":"card.move","actor":"owner","proof":"$proof","id":"$id","to":"READY_FOR_REFINEMENT"}""")

        assertThat(moved.getValue("ok").jsonPrimitive.boolean).isTrue()
        assertThat(moved.getValue("result").jsonObject.str("column")).isEqualTo("READY_FOR_REFINEMENT")
        assertThat(store.card(id)?.column).isEqualTo(Column.READY_FOR_REFINEMENT)
        val last = store.events(id).last()
        assertThat(last.type).isEqualTo(EventType.CARD_MOVED)
        assertThat(last.actor).isEqualTo(Actor.Owner)
        assertThat(last.payload).containsEntry("from", "INBOX").containsEntry("to", "READY_FOR_REFINEMENT")
    }

    @Test
    fun `AC-3 refused move answers with the reason from the rules`() = withCardServer { _ ->
        val id = createCard("Write docs")
        val expected = (MoveRules.check(Column.INBOX, Column.CODING, Actor.Owner) as MoveDecision.Refused).reason
        val ownerOnly =
            (MoveRules.check(Column.INBOX, Column.READY_FOR_REFINEMENT, Actor.Agent("dev")) as MoveDecision.Refused).reason

        val notAllowed = reply("""{"cmd":"card.move","actor":"owner","proof":"$proof","id":"$id","to":"CODING"}""")
        val wrongActor = reply("""{"cmd":"card.move","actor":"agent:dev","id":"$id","to":"READY_FOR_REFINEMENT"}""")

        assertThat(notAllowed.getValue("ok").jsonPrimitive.boolean).isFalse()
        assertThat(notAllowed.str("error")).isEqualTo(expected)
        assertThat(wrongActor.getValue("ok").jsonPrimitive.boolean).isFalse()
        assertThat(wrongActor.str("error")).isEqualTo(ownerOnly)
    }

    @Test
    fun `AC-5 card show and list return the current stored state`() = withCardServer { _ ->
        val first = createCard("First")
        val second = createCard("Second")
        reply("""{"cmd":"card.move","actor":"owner","proof":"$proof","id":"$first","to":"READY_FOR_REFINEMENT"}""")

        val shown = reply("""{"cmd":"card.show","id":"$first"}""")
        val listed = reply("""{"cmd":"card.list"}""")
        val missing = reply("""{"cmd":"card.show","id":"nope"}""")

        assertThat(shown.getValue("ok").jsonPrimitive.boolean).isTrue()
        assertThat(shown.getValue("result").jsonObject.str("column")).isEqualTo("READY_FOR_REFINEMENT")
        assertThat(shown.getValue("result").jsonObject.str("title")).isEqualTo("First")
        val rows = listed.getValue("result").jsonArray.map { it.jsonObject }
        assertThat(rows.map { it.str("id") }).containsExactly(first, second)
        assertThat(rows.map { it.str("column") }).containsExactly("READY_FOR_REFINEMENT", "INBOX")
        assertThat(missing.getValue("ok").jsonPrimitive.boolean).isFalse()
        assertThat(missing.str("error")).isEqualTo("card not found: nope")
    }

    @Test
    fun `AC-7 closing the server removes the socket`() {
        val server = startServer()
        assertThat(Files.exists(socketPath())).isTrue()

        server.close()

        assertThat(Files.exists(socketPath())).isFalse()
    }
}
