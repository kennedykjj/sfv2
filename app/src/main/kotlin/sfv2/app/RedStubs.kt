package sfv2.app

enum class ResyncResult { Reloaded, Failed, Busy }

suspend fun BoardMonitor.resync(): ResyncResult = ResyncResult.Busy
