package sfv2.daemon

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import sfv2.core.ProjectPolicies
import sfv2.store.CardEventStore
import sfv2.store.SpecStore

/** A running MCP endpoint on loopback; [close] stops it. */
class McpEndpoint(val port: Int, private val engine: EmbeddedServer<*, *>) : AutoCloseable {
    override fun close() {
        engine.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MS)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 1000L
    }
}

/** Serves the read-only tools over Streamable HTTP on 127.0.0.1; every request is authorized by [McpRequestGuard]. */
class McpServerFactory(
    private val sessions: McpSessions,
    private val store: CardEventStore,
    private val specs: SpecStore,
    private val registry: ProjectRegistry,
    private val files: ProjectFileSource,
    private val log: DaemonLog = StderrDaemonLog,
) {

    fun start(port: Int = 0): McpEndpoint {
        val guard = AtomicReference<McpRequestGuard?>(null)
        val engine = embeddedServer(CIO, host = LOOPBACK, port = port) {
            intercept(ApplicationCallPipeline.Plugins) {
                val headers = call.request.headers
                if (authorize(guard.get(), headers) == null) {
                    log.log("mcp_refused", mapOf("reason" to "unauthorized"))
                    call.respond(HttpStatusCode.Unauthorized)
                    finish()
                } else if (headers[MCP_SESSION_ID] == null) {
                    val token = headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    if (token != null) {
                        call.response.pipeline.intercept(ApplicationSendPipeline.Before) {
                            call.response.headers[MCP_SESSION_ID]?.let { sessions.bind(it, token) }
                        }
                    }
                }
            }
            mcpStreamableHttp {
                val session = requireNotNull(authorize(guard.get(), call.request.headers)) { "unauthorized MCP request" }
                serverFor(session)
            }
        }
        engine.start(wait = false)
        val bound = runBlocking { engine.engine.resolvedConnectors().first().port }
        guard.set(McpRequestGuard(sessions, bound))
        return McpEndpoint(bound, engine)
    }

    private fun authorize(guard: McpRequestGuard?, headers: Headers): McpSession? =
        guard?.authorize(
            headers[HttpHeaders.Host],
            headers[HttpHeaders.Origin],
            headers[HttpHeaders.Authorization],
            headers[MCP_SESSION_ID],
        )

    internal fun serverFor(session: McpSession): Server {
        val tools = McpReadTools(session, store, specs, registry, files, log)
        val server = Server(
            Implementation(name = "sfv2-daemon", version = "0"),
            ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        )
        val cardId = idSchema()
        val none = ToolSchema()
        server.addTool("card_get", "Reads a card of this project: title, column, references.", cardId) { request ->
            toolResult("card_get") { tools.cardGet(request.cardId()).let(::cardJson) }
        }
        server.addTool("spec_get", "Reads the registered spec of a card of this project.", cardId) { request ->
            toolResult("spec_get") { tools.specGet(request.cardId()).let { buildJsonObject { put("cardId", it.cardId); put("spec", untrusted(it.spec)) } } }
        }
        server.addTool("review_criteria_get", "Reads the project's review criteria from main.", none) {
            toolResult("review_criteria_get") { tools.reviewCriteriaGet().let { buildJsonObject { put("criteria", untrusted(it.criteria)) } } }
        }
        server.addTool("policies_get", "Reads the project's delivery policies.", none) {
            toolResult("policies_get") { policiesJson(tools.policiesGet()) }
        }
        server.addTool("layers_get", "Reads the project's layers.", none) {
            toolResult("layers_get") {
                buildJsonObject {
                    putJsonArray("layers") {
                        tools.layersGet().forEach { add(buildJsonObject { put("name", it.name); put("path", it.path) }) }
                    }
                }
            }
        }
        return server
    }

    private fun toolResult(tool: String, block: () -> JsonObject): CallToolResult =
        try {
            CallToolResult(content = listOf(TextContent(text = block().toString())), isError = false)
        } catch (e: McpToolException) {
            CallToolResult(content = listOf(TextContent(text = e.message ?: "tool refused")), isError = true)
        } catch (e: RuntimeException) {
            log.log("mcp_tool_failed", mapOf("tool" to tool, "code" to INTERNAL, "error" to e.javaClass.simpleName))
            CallToolResult(content = listOf(TextContent(text = "tool failed (code=$INTERNAL)")), isError = true)
        }

    private fun CallToolRequest.cardId(): String =
        (params.arguments?.get("cardId") as? JsonPrimitive)?.contentOrNull
            ?: throw McpToolException("argument cardId is required")

    private fun idSchema() = ToolSchema(
        properties = buildJsonObject { putJsonObject("cardId") { put("type", "string") } },
        required = listOf("cardId"),
    )

    private fun untrusted(text: UntrustedText): JsonElement = buildJsonObject {
        put("source", text.source)
        put("trust", text.trust)
        put("text", text.text)
    }

    private fun cardJson(card: McpCardView): JsonObject = buildJsonObject {
        put("id", card.id)
        put("title", untrusted(card.title))
        put("column", card.column.name)
        put(
            "references",
            buildJsonArray {
                card.references.forEach {
                    add(buildJsonObject { put("provider", it.provider); put("externalId", it.externalId); put("url", it.url) })
                }
            },
        )
    }

    private fun policiesJson(policies: ProjectPolicies): JsonObject = buildJsonObject {
        put("deliveryMode", policies.deliveryMode.key)
        putJsonObject("signers") { policies.signers.forEach { (effect, signer) -> put(effect.key, signer.key) } }
        putJsonObject("conventions") {
            put("commitFormat", policies.conventions.commitFormat)
            put("branchFormat", policies.conventions.branchFormat)
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val MCP_SESSION_ID = "Mcp-Session-Id"
        const val INTERNAL = "internal"
    }
}
