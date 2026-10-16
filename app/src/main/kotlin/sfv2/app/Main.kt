package sfv2.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.nio.file.Path
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val SOCKET_RELATIVE_PATH = "sfv2/daemon.sock"

fun main() {
    val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
    val monitor = runtimeDir?.let {
        ConnectionMonitor(SocketDaemonProbe(Path.of(it, SOCKET_RELATIVE_PATH)), SystemctlDaemonStarter(), Clock.systemUTC())
    }
    val noRuntimeDir: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(DisconnectReason.NO_RUNTIME_DIR))

    val board = runtimeDir?.let {
        BoardMonitor(SocketBoardSource(Path.of(it, SOCKET_RELATIVE_PATH)), monitor?.state ?: noRuntimeDir, Dispatchers.IO)
    }

    val noBoard: StateFlow<BoardView?> = MutableStateFlow(null)

    application {
        val state by (monitor?.state ?: noRuntimeDir).collectAsState()
        val view by (board?.view ?: noBoard).collectAsState()
        LaunchedEffect(Unit) { monitor?.run() }
        LaunchedEffect(Unit) { board?.run() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "sfv2",
            state = rememberWindowState(size = DpSize(640.dp, 400.dp), position = WindowPosition.PlatformDefault),
        ) {
            window.minimumSize = Dimension(360, 240)
            // The belt stays on screen (dimmed) once it has been drawn; before that, the connection screen.
            if (view != null) BoardScreen(view, connected = state == ConnectionState.Connected) else ConnectionScreen(state)
        }
    }
}
