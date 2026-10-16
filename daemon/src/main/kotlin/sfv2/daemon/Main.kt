package sfv2.daemon

import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

fun main() {
    val server = try {
        SocketServer.start(resolveSocketPath(System.getenv()))
    } catch (e: DaemonStartException) {
        System.err.println(e.message)
        exitProcess(1)
    } catch (e: DaemonAlreadyRunningException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    CountDownLatch(1).await()
}
