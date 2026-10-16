package sfv2.daemon

import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/** Outcome of starting the daemon; only [main] turns a failure into a process exit. */
sealed interface DaemonLaunch {
    data class Started(val server: SocketServer) : DaemonLaunch
    data class Failed(val exitCode: Int) : DaemonLaunch
}

/** Starts the daemon from [env]; failures are reported on [err] and returned, never thrown. */
fun launchDaemon(env: Map<String, String>, err: PrintStream): DaemonLaunch = try {
    DaemonLaunch.Started(SocketServer.start(resolveSocketPath(env)))
} catch (e: DaemonStartException) {
    err.println(e.message)
    DaemonLaunch.Failed(exitCode = 1)
} catch (e: DaemonAlreadyRunningException) {
    err.println(e.message)
    DaemonLaunch.Failed(exitCode = 1)
}

fun main() {
    when (val launch = launchDaemon(System.getenv(), System.err)) {
        is DaemonLaunch.Failed -> exitProcess(launch.exitCode)
        is DaemonLaunch.Started -> {
            Runtime.getRuntime().addShutdownHook(Thread { launch.server.close() })
            CountDownLatch(1).await()
        }
    }
}
