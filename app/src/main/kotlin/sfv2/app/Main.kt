package sfv2.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.nio.file.Path
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val SOCKET_RELATIVE_PATH = "sfv2/daemon.sock"
private const val TOKEN_RELATIVE_PATH = "sfv2/owner.token"

fun main() {
    val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
    val monitor = runtimeDir?.let {
        ConnectionMonitor(SocketDaemonProbe(Path.of(it, SOCKET_RELATIVE_PATH)), SystemctlDaemonStarter(), Clock.systemUTC())
    }
    val noRuntimeDir: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(DisconnectReason.NO_RUNTIME_DIR))

    val settingsClient = runtimeDir?.let {
        SocketSettingsClient(Path.of(it, SOCKET_RELATIVE_PATH), Path.of(it, TOKEN_RELATIVE_PATH))
    }

    application {
        var showSettings by remember { mutableStateOf(false) }
        val state by (monitor?.state ?: noRuntimeDir).collectAsState()
        LaunchedEffect(Unit) { monitor?.run() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "sfv2",
            state = rememberWindowState(size = DpSize(960.dp, 640.dp), position = WindowPosition.PlatformDefault),
        ) {
            window.minimumSize = Dimension(360, 240)
            if (showSettings && settingsClient != null) {
                SettingsScreen(settingsClient, onBack = { showSettings = false })
            } else {
                ConnectionScreen(state, onOpenSettings = settingsClient?.let { { showSettings = true } })
            }
        }
    }
}
