package sfv2.daemon

import java.io.PrintStream
import java.nio.channels.SocketChannel
import java.nio.file.Path

// TEMPORARY test-only stubs so the acceptance tests of #101 compile and fail by assertion
// before the implementation exists. The implementer DELETES THIS FILE when adding the real
// declarations in src/main (DaemonLog, SocketServer.start(path, log, pause), AcceptLoop,
// acceptBackoffMillis, DaemonLaunch, launchDaemon). Assumed AcceptLoop shape:
// AcceptLoop(accept, log, pause, onClient).run(), ending when accept throws ClosedChannelException.

fun interface DaemonLog {
    fun log(event: String, fields: Map<String, String>)
}

@Suppress("UNUSED_PARAMETER")
fun SocketServer.Companion.start(path: Path, log: DaemonLog, pause: (Long) -> Unit = {}): SocketServer = start(path)

internal fun acceptBackoffMillis(attempt: Int): Long = 0L

internal class AcceptLoop(
    private val accept: () -> SocketChannel,
    private val log: DaemonLog,
    private val pause: (Long) -> Unit,
    private val onClient: (SocketChannel) -> Unit,
) {
    fun run() = Unit
}

sealed interface DaemonLaunch {
    data class Started(val server: SocketServer) : DaemonLaunch
    data class Failed(val exitCode: Int) : DaemonLaunch
}

@Suppress("UNUSED_PARAMETER")
fun launchDaemon(env: Map<String, String>, err: PrintStream): DaemonLaunch = try {
    DaemonLaunch.Started(SocketServer.start(resolveSocketPath(env)))
} catch (e: RuntimeException) {
    DaemonLaunch.Failed(exitCode = 1)
}
