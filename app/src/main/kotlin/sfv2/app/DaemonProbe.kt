package sfv2.app

fun interface DaemonProbe {
    fun ping(): Boolean
}

enum class StartOutcome { Started, NotInstalled, Failed }

interface DaemonStarter {
    fun start(): StartOutcome
}
