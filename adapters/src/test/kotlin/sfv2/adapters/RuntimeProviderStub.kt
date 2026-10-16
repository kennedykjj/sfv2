package sfv2.adapters

import java.nio.file.Path
import java.time.Clock
import java.time.Duration

// shipfloor:qa-stub - compile-only stub of the #87 contract so the red gate fails by assertion.
// The implementer deletes this file when RuntimeProvider.kt and ClaudeRuntime(provider=...) exist in main.

sealed interface RuntimeProvider {
    fun environment(): Map<String, String> = emptyMap()

    data object Anthropic : RuntimeProvider
    data class Bedrock(val region: String, val profile: String? = null) : RuntimeProvider
    data class Vertex(val region: String, val projectId: String) : RuntimeProvider
    data class Proxy(val baseUrl: String) : RuntimeProvider

    companion object {
        fun fromEnv(env: Map<String, String>): RuntimeProvider = Anthropic
    }
}

class InvalidRuntimeProviderException(field: String) : RuntimeException("invalid runtime provider: $field")

/** Overload used only while the real constructor has no `provider` parameter; ignores it. */
fun ClaudeRuntime(
    binary: String,
    transcriptDir: Path,
    timeout: Duration,
    clock: Clock,
    provider: RuntimeProvider,
): ClaudeRuntime = ClaudeRuntime(binary, transcriptDir, timeout, clock)
