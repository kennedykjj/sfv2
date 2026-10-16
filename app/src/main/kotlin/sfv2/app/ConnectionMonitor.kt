package sfv2.app

import java.time.Clock

/** Skeleton of the contract: the implementation (task 1) replaces the body and exposes a StateFlow. */
class ConnectionStateHolder(val value: ConnectionState)

class ConnectionMonitor(probe: DaemonProbe, starter: DaemonStarter, clock: Clock) {
    val state = ConnectionStateHolder(ConnectionState.Connecting)

    fun tick() {}
}
