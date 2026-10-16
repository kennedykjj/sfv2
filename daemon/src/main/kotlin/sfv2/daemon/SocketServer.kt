package sfv2.daemon

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** The daemon cannot start, for instance because the runtime directory is not configured. */
class DaemonStartException(message: String) : RuntimeException(message)

/** Another daemon is already serving on the socket. */
class DaemonAlreadyRunningException(message: String) : RuntimeException(message)

private const val RUNTIME_DIR_VARIABLE = "XDG_RUNTIME_DIR"
private const val SOCKET_DIR_NAME = "sfv2"
private const val SOCKET_FILE_NAME = "daemon.sock"

/** Resolves the daemon socket path under the user's runtime directory. */
fun resolveSocketPath(env: Map<String, String>): Path {
    val runtimeDir = env[RUNTIME_DIR_VARIABLE]?.takeIf { it.isNotBlank() }
        ?: throw DaemonStartException("$RUNTIME_DIR_VARIABLE is not set; the daemon needs it to place its socket")
    return Path.of(runtimeDir).resolve(SOCKET_DIR_NAME).resolve(SOCKET_FILE_NAME)
}

/** Serves the line-delimited JSON protocol on a Unix domain socket. */
class SocketServer private constructor(
    private val path: Path,
    private val server: ServerSocketChannel,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<SocketChannel>()

    private fun startAccepting() {
        Thread.ofPlatform().daemon().name("sfv2-daemon-accept").start { acceptLoop() }
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            val client = try {
                server.accept()
            } catch (e: ClosedChannelException) {
                return
            } catch (e: IOException) {
                if (closed.get()) return
                continue
            }
            clients.add(client)
            Thread.ofVirtual().start { serve(client) }
        }
    }

    private fun serve(client: SocketChannel) {
        try {
            client.use {
                val input = Channels.newInputStream(it).buffered()
                val output = Channels.newOutputStream(it)
                while (true) {
                    val line = when (val read = readLine(input)) {
                        LineRead.EndOfStream -> return
                        LineRead.TooLong -> {
                            reply(output, errorReply("line exceeds the $MAX_LINE_BYTES byte limit"))
                            drain(input)
                            return
                        }
                        is LineRead.Line -> read.text
                    }
                    reply(output, handle(line))
                }
            }
        } catch (e: IOException) {
            // A failing client only ends its own connection.
        } catch (e: RuntimeException) {
            // Same: never let one client affect the accept loop.
        } finally {
            clients.remove(client)
        }
    }

    private fun reply(output: OutputStream, body: String) {
        output.write((body + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun handle(line: String): String {
        val node: JsonNode = try {
            MAPPER.readTree(line)
        } catch (e: JsonProcessingException) {
            return errorReply("invalid JSON")
        } ?: return errorReply("invalid JSON")
        if (!node.isObject) return errorReply("message must be a JSON object")
        val cmd = node.get("cmd")
        if (cmd == null || !cmd.isTextual) return errorReply("missing cmd")
        return when (cmd.asText()) {
            "ping" -> MAPPER.writeValueAsString(linkedMapOf("ok" to true, "result" to "pong"))
            else -> errorReply("unknown cmd")
        }
    }

    private fun errorReply(message: String): String =
        MAPPER.writeValueAsString(linkedMapOf("ok" to false, "error" to message))

    /** Stops accepting, closes the channel and removes the socket file. Idempotent. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        clients.forEach { runCatching { it.close() } }
        runCatching { Files.deleteIfExists(path) }
    }

    private sealed interface LineRead {
        data object EndOfStream : LineRead
        data object TooLong : LineRead
        data class Line(val text: String) : LineRead
    }

    private fun readLine(input: InputStream): LineRead {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return LineRead.EndOfStream
            if (b == '\n'.code) return LineRead.Line(buffer.toString(Charsets.UTF_8))
            if (buffer.size() >= MAX_LINE_BYTES) return LineRead.TooLong
            buffer.write(b)
        }
    }

    /** Discards what the client still sends, up to a cap, so closing does not reset the reply. */
    private fun drain(input: InputStream) {
        var discarded = 0
        val chunk = ByteArray(8192)
        while (discarded < MAX_DRAIN_BYTES) {
            val n = input.read(chunk)
            if (n < 0) return
            discarded += n
        }
    }

    companion object {
        private const val MAX_LINE_BYTES = 64 * 1024
        private const val MAX_DRAIN_BYTES = 1024 * 1024
        private const val DIR_PERMISSIONS = "rwx------"
        private const val FILE_PERMISSIONS = "rw-------"
        private val MAPPER = ObjectMapper()

        /** Binds the socket at [path] and starts serving. */
        fun start(path: Path): SocketServer {
            prepareDirectory(path)
            clearOrphan(path)
            val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            try {
                channel.bind(UnixDomainSocketAddress.of(path))
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(FILE_PERMISSIONS))
            } catch (e: IOException) {
                runCatching { channel.close() }
                throw DaemonStartException("cannot bind socket at $path: ${e.javaClass.simpleName}")
            }
            return SocketServer(path, channel).also { it.startAccepting() }
        }

        private fun prepareDirectory(path: Path) {
            val dir = requireNotNull(path.parent) { "socket path has no parent directory: $path" }
            try {
                val permissions = PosixFilePermissions.fromString(DIR_PERMISSIONS)
                if (!Files.isDirectory(dir)) {
                    Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(permissions))
                }
                Files.setPosixFilePermissions(dir, permissions)
            } catch (e: IOException) {
                throw DaemonStartException("cannot prepare socket directory $dir: ${e.javaClass.simpleName}")
            }
        }

        private fun clearOrphan(path: Path) {
            if (!Files.exists(path)) return
            val alive = try {
                SocketChannel.open(UnixDomainSocketAddress.of(path)).use { true }
            } catch (e: IOException) {
                false
            }
            if (alive) throw DaemonAlreadyRunningException("a daemon is already running on $path")
            try {
                Files.deleteIfExists(path)
            } catch (e: IOException) {
                throw DaemonStartException("cannot remove orphan socket $path: ${e.javaClass.simpleName}")
            }
        }
    }
}
