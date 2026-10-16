package sfv2.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SOCKET_RELATIVE_PATH = "sfv2/daemon.sock"

fun main() {
    val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
    val monitor = runtimeDir?.let {
        ConnectionMonitor(SocketDaemonProbe(Path.of(it, SOCKET_RELATIVE_PATH)), SystemctlDaemonStarter(), Clock.systemUTC())
    }
    val feed = runtimeDir?.let { FeedRuntime(Path.of(it, SOCKET_RELATIVE_PATH), Clock.systemUTC(), ZoneId.systemDefault()) }
    val noRuntimeDir: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(DisconnectReason.NO_RUNTIME_DIR))

    application {
        val state by (monitor?.state ?: noRuntimeDir).collectAsState()
        LaunchedEffect(Unit) { monitor?.run() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "sfv2",
            state = rememberWindowState(size = DpSize(640.dp, 400.dp), position = WindowPosition.PlatformDefault),
        ) {
            window.minimumSize = Dimension(360, 240)
            if (state == ConnectionState.Connected && feed != null) {
                val feedState by feed.client.state.collectAsState()
                LaunchedEffect(feed) { feed.run() }
                val scope = rememberCoroutineScope()
                FeedScreen(feedState, LocalDate.now(feed.clock.withZone(feed.zone))) { day ->
                    scope.launch(Dispatchers.IO) { feed.client.selectDay(day) }
                }
            } else {
                ConnectionScreen(state)
            }
        }
    }
}
