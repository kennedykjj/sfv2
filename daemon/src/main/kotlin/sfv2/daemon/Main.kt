package sfv2.daemon

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Clock
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess
import sfv2.store.CardEventStore
import sfv2.store.Database
import sfv2.store.ProjectPathStore
import sfv2.store.SpecStore

/** Outcome of starting the daemon; only [main] turns a failure into a process exit. */
sealed interface DaemonLaunch {
    data class Started(val server: SocketServer) : DaemonLaunch
    data class Failed(val exitCode: Int) : DaemonLaunch
}

/** Starts the daemon from [env]; failures are reported on [err] and returned, never thrown. */
fun launchDaemon(env: Map<String, String>, err: PrintStream): DaemonLaunch = try {
    val socketPath = resolveSocketPath(env)
    val database = openDatabase(resolveDatabasePath(env, socketPath))
    try {
        val ownerProof = OwnerProof.create(socketPath.resolveSibling("owner.token"), SecureRandom())
        val clock = Clock.systemUTC()
        val store = CardEventStore(database)
        val specs = SpecStore(database, resolveDatabasePath(env, socketPath).parent.resolve("specs"))
        val commands = CardCommands(store, clock, ownerProof, specs = specs)
        val source = GitProjectConfigSource()
        val registry = ProjectRegistry(source, ProjectPathStore(database), StderrDaemonLog, clock)
        registry.restore()
        val projects = ProjectCommands(registry, ownerProof)
        val dataDir = resolveDatabasePath(env, socketPath).parent
        val settingsStore = SettingsStore(dataDir.resolve("settings.json"), clock)
        val settings = SettingsCommands(settingsStore, commands.recorder, store, ownerProof, env, clock)
        val refine = RefineRuntime.start(
            settings = settingsStore,
            dataDir = dataDir,
            store = store,
            commands = commands,
            registry = registry,
            files = source,
            database = database,
            clock = clock,
            source = source,
            env = env,
        )
        if (refine.dispatcherAllowed) {
            commands.events.addListener(refine.dispatcher::onEvent)
            refine.dispatcher.start()
        }
        DaemonLaunch.Started(
            SocketServer.start(
                socketPath,
                commands = commands,
                projects = projects,
                rebuild = CardRebuild.forProjects(
                    store, commands.recorder, registry,
                    { project -> ProjectDelivery.forgeOf(project, dataDir.resolve("forge")) },
                    ownerProof, clock, StderrDaemonLog,
                ),
                importer = CardImport.forProjects(
                    store, commands.recorder, registry,
                    { project -> ProjectDelivery.trackerOf(project, dataDir.resolve("tracker")) },
                    ownerProof, clock, StderrDaemonLog,
                ),
                closure = CardClosure.forProjects(
                    store, commands.recorder, registry,
                    { project -> ProjectDelivery.trackerOf(project, dataDir.resolve("tracker")) },
                    { project, pr -> ProjectDelivery.pullRequestUrl(project, pr) },
                    ownerProof, clock, StderrDaemonLog,
                ),
                milestones = refine.milestones,
                settings = settings,
                costSummary = refine.costSummary,
                onClose = {
                    runCatching { refine.close() }
                    database.close()
                },
            ),
        )
    } catch (e: Throwable) {
        runCatching { database.close() }
        throw e
    }
} catch (e: DaemonStartException) {
    err.println(e.message)
    DaemonLaunch.Failed(exitCode = 1)
} catch (e: DaemonAlreadyRunningException) {
    err.println(e.message)
    DaemonLaunch.Failed(exitCode = 1)
}

private fun openDatabase(path: Path): Database = try {
    Files.createDirectories(path.parent)
    Database.open(path)
} catch (e: IOException) {
    throw DaemonStartException("cannot open database at $path: ${e.javaClass.simpleName}")
}

/**
 * Database under XDG_DATA_HOME, else HOME/.local/share. Without either (no home to persist to)
 * it falls back to the socket's runtime directory so the daemon never guesses a path outside [env].
 */
internal fun resolveDatabasePath(env: Map<String, String>, socketPath: Path): Path {
    val dataHome = env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        ?: env["HOME"]?.takeIf { it.isNotBlank() }?.let { Path.of(it).resolve(".local/share") }
        ?: return requireNotNull(socketPath.parent) { "socket path has no parent directory" }.resolve(DATABASE_FILE_NAME)
    return dataHome.resolve("sfv2").resolve(DATABASE_FILE_NAME)
}

private const val DATABASE_FILE_NAME = "sfv2.db"

fun main(args: Array<String>) {
    if (args.firstOrNull() == "service") {
        exitProcess(runServiceCommand(args.toList(), System.getenv(), System.out, System.err))
    }
    when (val launch = launchDaemon(System.getenv(), System.err)) {
        is DaemonLaunch.Failed -> exitProcess(launch.exitCode)
        is DaemonLaunch.Started -> {
            Runtime.getRuntime().addShutdownHook(Thread { launch.server.close() })
            CountDownLatch(1).await()
        }
    }
}
