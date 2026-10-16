package sfv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder

private const val SOCKET_RELATIVE_PATH = "sfv2/daemon.sock"
private const val REQUEST_BUFFER = 8
private const val TICK_EVERY_MILLIS = 60_000L

/** What a click on the tray or on a notification asks of the window. */
private sealed interface WindowRequest {
    /** Brings the window to the front on [cardId], or on the board when it is null. */
    data class Open(val cardId: String?) : WindowRequest

    data object Quit : WindowRequest
}

private class NoticePorts(val tray: TrayPort, val notifications: NotificationPort)

private object NoTray : TrayPort {
    override fun show(waiting: Int) = Unit

    override fun clear() = Unit

    override fun onActivated(listener: () -> Unit) = Unit
}

private object NoNotifications : NotificationPort {
    override fun notify(title: String, body: String, replacesId: Int): Int = 0

    override fun onActivated(listener: (notificationId: Int) -> Unit) = Unit
}

private fun logEvent(event: String, fields: Map<String, String>) {
    System.err.println((listOf("event=$event") + fields.map { "${it.key}=${it.value}" }).joinToString(" "))
}

/** Opens the D-Bus adapters; whatever fails is logged and replaced by a no-op port, so the app still starts. */
private fun openNoticePorts(requests: MutableSharedFlow<WindowRequest>): NoticePorts {
    val connection = try {
        DBusConnectionBuilder.forSessionBus().build()
    } catch (e: Exception) {
        logEvent("owner_notice_unavailable", mapOf("port" to "dbus", "reason" to e.javaClass.simpleName))
        return NoticePorts(NoTray, NoNotifications)
    }
    val tray = try {
        DbusTray(connection, NoticeLanguage.PT_BR) { requests.tryEmit(WindowRequest.Quit) }
    } catch (e: Exception) {
        logEvent("owner_notice_unavailable", mapOf("port" to "tray", "reason" to e.javaClass.simpleName))
        NoTray
    }
    val notifier = try {
        DbusNotifier(connection)
    } catch (e: Exception) {
        logEvent("owner_notice_unavailable", mapOf("port" to "notification", "reason" to e.javaClass.simpleName))
        NoNotifications
    }
    return NoticePorts(tray, notifier)
}

@Composable
private fun OpenCard(cardId: String, source: CardDetailSource?, onBack: () -> Unit) {
    val model = remember(cardId) {
        CardDetailModel(cardId, source ?: CardDetailSource { throw IOException("no runtime dir") })
    }
    val state by model.state.collectAsState()
    LaunchedEffect(model) { withContext(Dispatchers.IO) { model.refresh() } }
    CardDetailScreen(cardId, state, onBack)
}

fun main() {
    val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
    val monitor = runtimeDir?.let {
        ConnectionMonitor(SocketDaemonProbe(Path.of(it, SOCKET_RELATIVE_PATH)), SystemctlDaemonStarter(), Clock.systemUTC())
    }
    val feed = runtimeDir?.let { FeedRuntime(Path.of(it, SOCKET_RELATIVE_PATH), Clock.systemUTC(), ZoneId.systemDefault()) }
    val noRuntimeDir: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(DisconnectReason.NO_RUNTIME_DIR))

    val requests = MutableSharedFlow<WindowRequest>(extraBufferCapacity = REQUEST_BUFFER)
    val ports = openNoticePorts(requests)
    val shell = OwnerNoticeShell(
        ports.tray,
        ports.notifications,
        Clock.systemUTC(),
        ZoneId.systemDefault(),
        NoticeLanguage.PT_BR,
        ::logEvent,
    ) { cardId -> requests.tryEmit(WindowRequest.Open(cardId)) }

    val board = runtimeDir?.let {
        BoardMonitor(
            SocketBoardSource(Path.of(it, SOCKET_RELATIVE_PATH)),
            monitor?.state ?: noRuntimeDir,
            Dispatchers.IO,
            shell::onEvent,
            shell::onSnapshot,
        )
    }
    val cardSource = runtimeDir?.let { SocketCardDetailSource(Path.of(it, SOCKET_RELATIVE_PATH)) }

    val noBoard: StateFlow<BoardView?> = MutableStateFlow(null)

    application {
        val state by (monitor?.state ?: noRuntimeDir).collectAsState()
        val view by (board?.view ?: noBoard).collectAsState()
        val connected = state == ConnectionState.Connected
        var showFeed by remember { mutableStateOf(false) }
        var openCardId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { monitor?.run() }
        LaunchedEffect(Unit) { board?.run() }
        LaunchedEffect(feed, connected) { if (feed != null && connected) feed.run() }
        LaunchedEffect(Unit) {
            while (true) {
                delay(TICK_EVERY_MILLIS)
                withContext(Dispatchers.IO) { shell.tick() }
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "sfv2",
            state = rememberWindowState(size = DpSize(640.dp, 400.dp), position = WindowPosition.PlatformDefault),
        ) {
            window.minimumSize = Dimension(360, 240)
            LaunchedEffect(Unit) {
                requests.collect { request ->
                    when (request) {
                        is WindowRequest.Open -> {
                            window.isMinimized = false
                            window.toFront()
                            openCardId = request.cardId
                        }
                        WindowRequest.Quit -> exitApplication()
                    }
                }
            }
            val feedAvailable = feed != null && connected
            when {
                openCardId != null -> OpenCard(requireNotNull(openCardId), cardSource) { openCardId = null }
                feed != null && feedAvailable && showFeed -> ScreenWithTabs(showFeed = true, onSelect = { showFeed = it }) {
                    val feedState by feed.client.state.collectAsState()
                    val scope = rememberCoroutineScope()
                    FeedScreen(feedState, LocalDate.now(feed.clock.withZone(feed.zone))) { day ->
                        scope.launch(Dispatchers.IO) { feed.client.selectDay(day) }
                    }
                }
                // The belt stays on screen (dimmed) once it has been drawn; before that, the connection screen.
                view != null -> ScreenWithTabs(showFeed = false, onSelect = { showFeed = it }, tabs = feedAvailable) {
                    BoardScreen(view, connected = connected)
                }
                else -> ConnectionScreen(state)
            }
        }
    }
}

/** A minimal switch between the belt and the day feed, above the active screen. */
@Composable
private fun ScreenWithTabs(
    showFeed: Boolean,
    onSelect: (Boolean) -> Unit,
    tabs: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Tokens.ground)) {
        if (tabs) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Tab(Texts.BOARD, selected = !showFeed) { onSelect(false) }
                Tab(Texts.FEED_TITLE, selected = showFeed) { onSelect(true) }
            }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    BasicText(
        label,
        modifier = Modifier.clickable(onClick = onClick).padding(end = 16.dp),
        style = TextStyle(
            color = if (selected) Tokens.text else Tokens.muted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
    )
}
