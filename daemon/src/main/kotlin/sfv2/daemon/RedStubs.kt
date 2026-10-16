package sfv2.daemon

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.SecureRandom

// Red-gate stubs: signatures only, so the acceptance tests compile and fail by assertion.

class TcpTransport private constructor() : AutoCloseable {

    val localAddress: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)

    override fun close() = Unit

    companion object {
        fun open(directory: Path, port: Int, random: SecureRandom): TcpTransport = TcpTransport()
    }
}

fun SocketServer.Companion.start(
    path: Path,
    tcp: TcpTransport?,
    log: DaemonLog = StderrDaemonLog,
    commands: CardCommands? = null,
): SocketServer = start(path, log = log, commands = commands)
