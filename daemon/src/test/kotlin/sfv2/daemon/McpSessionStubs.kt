package sfv2.daemon

import java.time.Clock
import java.time.Duration

// shipfloor:qa-stub - lets the acceptance tests compile before the session binding exists; the implementer deletes this file.
// Members of the real classes win over these extensions, so they stop mattering once the contract is implemented.

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun McpSessions(clock: Clock, ttl: Duration): McpSessions = McpSessions()

@Suppress("UNUSED_PARAMETER")
fun McpSessions.close(token: String) = Unit

@Suppress("UNUSED_PARAMETER")
fun McpSessions.owns(httpSessionId: String, token: String): Boolean = false

@Suppress("UNUSED_PARAMETER")
fun McpRequestGuard.authorize(host: String?, origin: String?, authorization: String?, sessionId: String?): McpSession? =
    authorize(host, origin, authorization)
