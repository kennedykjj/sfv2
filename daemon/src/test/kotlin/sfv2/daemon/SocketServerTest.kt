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

    @Test
    fun `AC-7 closing the server removes the socket`() {
        val server = startServer()
        assertThat(Files.exists(socketPath())).isTrue()

        server.close()

        assertThat(Files.exists(socketPath())).isFalse()
    }
}
