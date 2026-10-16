package sfv2.daemon

import java.io.PrintStream
import java.nio.file.Path

// shipfloor:qa-stub - lets ServiceInstallerTest compile; the implementer deletes this file.

data class SystemctlResult(val exitCode: Int, val output: String)

interface SystemctlRunner {
    fun run(vararg args: String): SystemctlResult
}

sealed interface ServiceOutcome {
    data object Installed : ServiceOutcome
    data object AlreadyInstalled : ServiceOutcome
    data object Removed : ServiceOutcome
    data object NotInstalled : ServiceOutcome
    data class Unsupported(val os: String) : ServiceOutcome
    data class Failed(val reason: String) : ServiceOutcome
}

object ServiceUnit {
    fun render(launcher: Path): String = ""
}

class ServiceInstaller(
    private val unitDir: Path,
    private val launcher: Path,
    private val systemctl: SystemctlRunner,
    private val osName: String,
) {
    fun install(): ServiceOutcome = ServiceOutcome.Failed("stub")
    fun uninstall(): ServiceOutcome = ServiceOutcome.Failed("stub")
}

fun runServiceCommand(args: List<String>, env: Map<String, String>, out: PrintStream, err: PrintStream): Int = 99
