package sfv2.daemon

import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.locks.LockSupport
import java.net.StandardProtocolFamily
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CompletableFuture
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

    private class EventStreamClient(private val channel: SocketChannel) : AutoCloseable {
        private val reader = Channels.newInputStream(channel).bufferedReader(Charsets.UTF_8)

        fun send(line: String) {
            val out = Channels.newOutputStream(channel)
            out.write((line + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }

        fun nextLine(): String? =
            CompletableFuture.supplyAsync { reader.readLine() }.get(10, TimeUnit.SECONDS)

        fun nextEvent(): JsonObject {
            val line = requireNotNull(nextLine()) { "stream closed before an event arrived" }
            return Json.parseToJsonElement(line).jsonObject.getValue("event").jsonObject
        }

        fun events(count: Int): List<JsonObject> = List(count) { nextEvent() }

        override fun close() = channel.close()
    }

    private fun subscribe(after: Long? = null): EventStreamClient {
        val client = EventStreamClient(SocketChannel.open(UnixDomainSocketAddress.of(socketPath())))
        val extra = if (after == null) "" else ""","after":$after"""
        client.send("""{"cmd":"events.subscribe"$extra}""")
        assertThat(client.nextLine()).isEqualTo("""{"ok":true,"result":"subscribed"}""")
        return client
    }

    private fun JsonObject.seq(): Long = getValue("seq").jsonPrimitive.long

    // Above the default per-subscriber queue of 1000, with titles big enough to fill the socket buffer.
    private val flood = 1100

    private fun bigTitle(n: Int): String = "t$n-" + "x".repeat(5_000)

    @Test
    fun `AC-1 stream two subscribers receive the same events in the same order`() = withCardServer { _ ->
        subscribe().use { first ->
            subscribe().use { second ->
                val id = createCard("Write docs")
                reply("""{"cmd":"card.move","actor":"owner","proof":"$proof","id":"$id","to":"READY_FOR_REFINEMENT"}""")

                val seenByFirst = first.events(2)
                val seenBySecond = second.events(2)

                assertThat(seenBySecond).isEqualTo(seenByFirst)
                assertThat(seenByFirst.map { it.str("card") }).containsOnly(id)
                assertThat(seenByFirst.map { it.seq() }).isSorted().doesNotHaveDuplicates()
                assertThat(seenByFirst.last().getValue("payload").jsonObject.str("to")).isEqualTo("READY_FOR_REFINEMENT")
            }
        }
    }

    @Test
    fun `AC-2 stream subscribing after a seq replays the missed events then the new ones without gap or duplicate`() =
        withCardServer { _ ->
            val firstId = createCard("First")
            val seen = subscribe(after = 0).use { it.nextEvent() }.seq()
            val moved = reply("""{"cmd":"card.move","actor":"owner","proof":"$proof","id":"$firstId","to":"READY_FOR_REFINEMENT"}""")
            val secondId = createCard("Second")
            assertThat(moved.getValue("ok").jsonPrimitive.boolean).isTrue()

            subscribe(after = seen).use { client ->
                val thirdId = createCard("Third")
                val received = client.events(3)

                assertThat(received.map { it.str("card") }).containsExactly(firstId, secondId, thirdId)
                assertThat(received.map { it.seq() }).isSorted().doesNotHaveDuplicates().allMatch { it > seen }
            }
        }

    @Test
    fun `AC-2 stream invalid after is refused`() = withCardServer { _ ->
        SocketChannel.open(UnixDomainSocketAddress.of(socketPath())).use { channel ->
            val client = EventStreamClient(channel)
            client.send("""{"cmd":"events.subscribe","after":"abc"}""")

            assertThat(client.nextLine()).isEqualTo("""{"ok":false,"error":"invalid after"}""")
        }
    }

    @Test
    fun `AC-3 stream a subscriber that never reads does not delay the others or the writes`() =
        withCardServer { store ->
            subscribe().use { _ ->
                subscribe().use { fast ->
                    val ids = (1..flood).map { n ->
                        val id = createCard(bigTitle(n))
                        assertThat(fast.nextEvent().str("card")).isEqualTo(id)
                        id
                    }

                    assertThat(ids.flatMap { store.events(it) }).hasSize(flood)
                }
            }
        }

    @Test
    fun `AC-4 stream a subscriber that fell behind is dropped and resumes by its last seq without loss`() =
        withCardServer { _ ->
            subscribe().use { slow ->
                subscribe().use { fast ->
                    val all = (1..flood).map { n ->
                        createCard(bigTitle(n))
                        fast.nextEvent().seq()
                    }

                    val before = mutableListOf<Long>()
                    var closing: String? = null
                    while (closing == null) {
                        val line = slow.nextLine() ?: break
                        val parsed = Json.parseToJsonElement(line).jsonObject
                        if (parsed.containsKey("error")) closing = parsed.str("error") else before.add(parsed.getValue("event").jsonObject.seq())
                    }

                    assertThat(closing).isEqualTo("subscriber too slow")
                    assertThat(before).isNotEmpty().isSubsetOf(all)
                    val resumed = subscribe(after = before.last()).use { client ->
                        client.events(all.size - before.size).map { it.seq() }
                    }
                    assertThat(before + resumed).isEqualTo(all)
                }
            }
        }

    @Test
    fun `AC-5 stream subscribing without a last event receives only events recorded afterwards`() = withCardServer { _ ->
        createCard("Old")

        subscribe().use { client ->
            val newId = createCard("New")

            val first = client.nextEvent()

            assertThat(first.str("card")).isEqualTo(newId)
        }
    }

    @Test
    fun `AC-6 stream a subscriber that disconnects does not affect the others or the daemon`() =
        withCardServer { store ->
            val gone = subscribe()
            subscribe().use { stayer ->
                gone.close()

                val ids = (1..10).map { n ->
                    val id = createCard(bigTitle(n))
                    assertThat(stayer.nextEvent().str("card")).isEqualTo(id)
                    id
                }

                assertThat(ids.flatMap { store.events(it) }).hasSize(10)
                assertThat(exchange("""{"cmd":"ping"}""")).isEqualTo("""{"ok":true,"result":"pong"}""")
            }
        }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(by: Duration) {
            now = now.plus(by)
        }
    }

    private val grace = Duration.ofSeconds(5)

    private class StreamHarness(val commands: CardCommands, val clock: MutableClock)

    /** A card server whose subscribers are dropped after 5 queued events and closed after [grace] on a manual clock. */
    private fun withGraceServer(block: (StreamHarness) -> Unit) {
        Database.open(runtimeDir.resolve("cards.db")).use { db ->
            val store = CardEventStore(db)
            val ownerProof = OwnerProof.create(tokenPath, SecureRandom())
            proof = Files.readString(tokenPath).trim()
            val manual = MutableClock(fixedNow)
            val commands = CardCommands(
                store, manual, ownerProof, cardLog, subscriberQueueLimit = 5, closeGrace = grace,
            )
            servers.add(SocketServer.start(socketPath(), commands = commands))
            block(StreamHarness(commands, manual))
        }
    }

    private fun openFds(): Int = File("/proc/self/fd").list().orEmpty().size

    /** Descriptor count once closes running on other threads have settled. */
    private fun settledFds(): Int {
        var last = openFds()
        repeat(100) {
            LockSupport.parkNanos(20_000_000L)
            val now = openFds()
            if (now == last) return now
            last = now
        }
        return last
    }

    /** Publishes enough big events, each read by [fast], for every subscriber that never reads to be dropped. */
    private fun floodUntilDropped(fast: EventStreamClient) {
        repeat(150) { n ->
            val id = createCard(bigTitle(n))
            assertThat(fast.nextEvent().str("card")).isEqualTo(id)
        }
    }

    private fun EventStreamClient.drain(): List<String> {
        val lines = mutableListOf<String>()
        while (true) lines.add(nextLine() ?: return lines)
    }

    @Test
    fun `AC-1 grace a dropped subscriber that never reads has its connection closed after the grace without reading`() =
        withGraceServer { h ->
            subscribe().use { fast ->
                createCard("warm")
                fast.nextEvent()
                val baseline = settledFds()
                subscribe().use { slow ->
                    floodUntilDropped(fast)
                    h.clock.advance(grace.plusMillis(1))
                    h.commands.events.sweep()

                    val lines = slow.drain()

                    assertThat(lines).isNotEmpty().noneMatch { it.contains("error") }
                    slow.close()
                    assertThat(settledFds()).isLessThanOrEqualTo(baseline)
                }
            }
        }

    @Test
    fun `AC-2 grace a dropped subscriber that reads within the grace receives subscriber too slow before the close`() =
        withGraceServer { h ->
            subscribe().use { fast ->
                subscribe().use { slow ->
                    floodUntilDropped(fast)
                    h.clock.advance(grace.minusMillis(1))
                    h.commands.events.sweep()

                    val lines = slow.drain()

                    assertThat(lines.last()).isEqualTo("""{"error":"subscriber too slow"}""")
                }
            }
        }

    @Test
    fun `AC-3 grace a dropped subscriber that consumed everything before the grace is closed too`() =
        withGraceServer { h ->
            subscribe().use { fast ->
                createCard("warm")
                fast.nextEvent()
                val baseline = settledFds()
                subscribe().use { slow ->
                    floodUntilDropped(fast)
                    var line: String?
                    do {
                        line = slow.nextLine()
                    } while (line != null && !line.contains("error"))
                    assertThat(line).isEqualTo("""{"error":"subscriber too slow"}""")

                    h.clock.advance(grace.plusMillis(1))
                    h.commands.events.sweep()

                    assertThat(slow.nextLine()).isNull()
                    slow.close()
                    assertThat(settledFds()).isLessThanOrEqualTo(baseline)
                }
            }
        }

    @Test
    fun `AC-4 grace several dropped subscribers release their descriptors once all graces pass`() =
        withGraceServer { h ->
            subscribe().use { fast ->
                createCard("warm")
                fast.nextEvent()
                val baseline = settledFds()
                val slows = List(3) { subscribe() }
                floodUntilDropped(fast)
                assertThat(settledFds()).isGreaterThan(baseline)

                h.clock.advance(grace.plusMillis(1))
                h.commands.events.sweep()
                slows.forEach { it.close() }

                assertThat(settledFds()).isLessThanOrEqualTo(baseline)
            }
        }

    @Test
    fun `AC-5 grace a healthy subscriber keeps receiving while slow ones are closed`() =
        withGraceServer { h ->
            subscribe().use { fast ->
                val slows = List(2) { subscribe() }
                floodUntilDropped(fast)

                h.clock.advance(grace.plusMillis(1))
                h.commands.events.sweep()
                val id = createCard("after the sweep")

                assertThat(fast.nextEvent().str("card")).isEqualTo(id)
                slows.forEach { it.close() }
            }
        }

    @Test
    fun `AC-6 grace the close happens exactly when the injected clock reaches the grace with no real wait`() =
        withGraceServer { h ->
            subscribe().use { fast ->
                subscribe().use { slow ->
                    floodUntilDropped(fast)
                    val open = settledFds()

                    h.clock.advance(grace.minusMillis(1))
                    h.commands.events.sweep()
                    assertThat(settledFds()).isEqualTo(open)

                    h.clock.advance(Duration.ofMillis(1))
                    h.commands.events.sweep()
                    assertThat(settledFds()).isLessThan(open)
                    slow.close()
                }
            }
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

    private fun approve(id: String, proofValue: String? = proof, actor: String = "owner"): JsonObject =
        reply(withProof("""{"cmd":"card.approve","actor":"$actor","id":"$id"}""", proofValue))

    private fun requestChange(id: String, note: String?, proofValue: String? = proof): JsonObject {
        val noteField = if (note == null) "" else ""","note":${JsonPrimitive(note)}"""
        return reply(withProof("""{"cmd":"card.request-change","actor":"owner","id":"$id"$noteField}""", proofValue))
    }

    @Test
    fun `AC-1 owner approve moves a Spec Review card to Ready for Dev and records OWNER_APPROVED by the owner`() =
        withCardServer { store ->
            seed(store, "spec", Column.SPEC_REVIEW)
            seed(store, "other", Column.SPEC_REVIEW)

            assertThat(ok(approve("other", proofValue = null))).isFalse()
            assertThat(ok(approve("other", proofValue = "wrong"))).isFalse()
            assertThat(ok(approve("other", actor = "agent:dev"))).isFalse()
            assertThat(store.card("other")?.column).isEqualTo(Column.SPEC_REVIEW)
            assertThat(store.events("other")).hasSize(1)

            val approved = approve("spec")

            assertThat(ok(approved)).isTrue()
            assertThat(approved.getValue("result").jsonObject.str("column")).isEqualTo("READY_FOR_DEV")
            assertThat(store.card("spec")?.column).isEqualTo(Column.READY_FOR_DEV)
            val last = store.events("spec").last()
            assertThat(last.type).isEqualTo(EventType.OWNER_APPROVED)
            assertThat(last.actor).isEqualTo(Actor.Owner)
            assertThat(last.at).isEqualTo(fixedNow)
            assertThat(last.payload).containsEntry("from", "SPEC_REVIEW").containsEntry("to", "READY_FOR_DEV")
        }

    @Test
    fun `AC-2 request change with a note sends the card back to Ready for Refinement and records the note`() =
        withCardServer { store ->
            seed(store, "spec", Column.SPEC_REVIEW)

            val changed = requestChange("spec", "Cover the offline case")

            assertThat(ok(changed)).isTrue()
            assertThat(changed.getValue("result").jsonObject.str("column")).isEqualTo("READY_FOR_REFINEMENT")
            assertThat(store.card("spec")?.column).isEqualTo(Column.READY_FOR_REFINEMENT)
            val last = store.events("spec").last()
            assertThat(last.type).isEqualTo(EventType.CARD_MOVED)
            assertThat(last.actor).isEqualTo(Actor.Owner)
            assertThat(last.payload)
                .containsEntry("from", "SPEC_REVIEW")
                .containsEntry("to", "READY_FOR_REFINEMENT")
                .containsEntry("note", "Cover the offline case")
        }

    @Test
    fun `AC-3 request change without a usable note is refused and neither the column nor the events change`() =
        withCardServer { store ->
            seed(store, "spec", Column.SPEC_REVIEW)
            val before = store.card("spec") to store.events("spec")
            val tooLong = "é".repeat(1025)

            for (note in listOf(null, "", "   ", "\n\t", tooLong)) {
                val refused = requestChange("spec", note)

                assertThat(ok(refused)).isFalse()
                assertThat(refused.str("error")).isNotBlank().isNotEqualTo("owner proof missing or invalid")
            }

            assertThat(store.card("spec") to store.events("spec")).isEqualTo(before)
            assertThat(ok(requestChange("spec", "é".repeat(1024)))).isTrue()
        }

    @Test
    fun `AC-4 approve and request change outside Spec Review or on a missing card fail with the id and change nothing`() =
        withCardServer { store ->
            seed(store, "fresh", Column.INBOX)
            seed(store, "busy", Column.CODING)
            val before = listOf("fresh", "busy").associateWith { store.card(it) to store.events(it) }

            for ((id, column) in listOf("fresh" to Column.INBOX, "busy" to Column.CODING)) {
                for (reply in listOf(approve(id), requestChange(id, "Please redo"))) {
                    assertThat(ok(reply)).isFalse()
                    assertThat(reply.str("error")).contains(id)
                    assertThat(reply.str("error")).satisfiesAnyOf(
                        { assertThat(it).contains(column.name) },
                        { assertThat(it).contains(column.displayName) },
                    )
                }
            }
            for (reply in listOf(approve("ghost"), requestChange("ghost", "Please redo"))) {
                assertThat(ok(reply)).isFalse()
                assertThat(reply.str("error")).contains("ghost")
            }

            assertThat(before.keys.associateWith { store.card(it) to store.events(it) }).isEqualTo(before)
            assertThat(store.card("ghost")).isNull()
        }

    @Test
    fun `AC-7 closing the server removes the socket`() {
        val server = startServer()
        assertThat(Files.exists(socketPath())).isTrue()

        server.close()

        assertThat(Files.exists(socketPath())).isFalse()
    }
}
