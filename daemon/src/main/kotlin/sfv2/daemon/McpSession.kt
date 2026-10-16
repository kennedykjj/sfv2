package sfv2.daemon

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Free text from outside the factory, wrapped so a consumer never mistakes it for an instruction. */
data class UntrustedText(val source: String, val text: String) {
    val trust: String = "untrusted"
}

/** An MCP session is bound to one project, identified by [Project.repo]. */
data class McpSession(val repo: String)

/** Opens sessions and hands out one random bearer token per session. */
class McpSessions {
    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, McpSession>()

    fun open(repo: String): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        sessions[digest(token)] = McpSession(repo)
        return token
    }

    /** Looks the token up by its digest, so the comparison never depends on where a guess first differs. */
    fun find(token: String): McpSession? {
        val wanted = digest(token)
        var found: McpSession? = null
        sessions.forEach { (key, session) ->
            if (MessageDigest.isEqual(key.toByteArray(Charsets.UTF_8), wanted.toByteArray(Charsets.UTF_8))) {
                found = session
            }
        }
        return found
    }

    private fun digest(token: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

    private companion object {
        const val TOKEN_BYTES = 32
    }
}

/** Accepts a request only on the exact loopback host, with no `Origin` and a valid bearer token. */
class McpRequestGuard(private val sessions: McpSessions, private val port: Int) {

    fun authorize(host: String?, origin: String?, authorization: String?): McpSession? {
        if (origin != null) return null
        if (host != "127.0.0.1:$port") return null
        val header = authorization ?: return null
        if (!header.startsWith(BEARER)) return null
        val token = header.substring(BEARER.length)
        if (token.isEmpty()) return null
        return sessions.find(token)
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}
