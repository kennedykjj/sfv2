# SDK de MCP em Kotlin 0.15.0: o que o #28 precisa saber

Nota do dono interino para destravar o #28 (o engenheiro não consegue ler o jar do cache do Gradle). Lida do `kotlin-sdk-server-0.15.0-sources.jar` e do `kotlin-sdk-core-0.15.0-sources.jar` do Maven Central. A versão 0.8.1 do catálogo era um palpite e foi trocada.

## Dependências (versões fixas, já no catálogo)

- `libs.mcp.kotlin.sdk.server` = `io.modelcontextprotocol:kotlin-sdk-server:0.15.0` (traz `kotlin-sdk-core` e Ktor server em runtime: `ktor-server-core`, `-sse`, `-content-negotiation`, `-websockets`, `ktor-serialization-kotlinx-json` 3.5.1).
- `libs.ktor.server.cio` = `io.ktor:ktor-server-cio:3.5.1` (o motor HTTP; o SDK não escolhe um).
- Em `daemon/build.gradle.kts`: `implementation(libs.mcp.kotlin.sdk.server)` e `implementation(libs.ktor.server.cio)`. As classes do SDK (`Server`, `ServerOptions`, tipos de `types.*`) são API do módulo `kotlin-sdk-core`, que chega por transitividade; se o compilador não as achar, declarar também `io.modelcontextprotocol:kotlin-sdk-core:0.15.0`.

## Servidor e ferramentas

```kotlin
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

val server = Server(
    Implementation(name = "sfv2-daemon", version = "0"),
    ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
) {
    addTool(
        name = "card_get",
        description = "...",
        inputSchema = ToolSchema(properties = buildJsonObject { /* JSON Schema das propriedades */ }, required = listOf("cardId")),
    ) { request ->                       // this: ClientConnection; request: CallToolRequest
        val cardId = request.arguments?.get("cardId")        // JsonElement? (JsonObject? em request.params.arguments)
        CallToolResult(content = listOf(TextContent(text = "...")), isError = false)
    }
}
```

- `addTool(name, description, inputSchema = ToolSchema(), title, outputSchema, ..., handler)` com `handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult`.
- `CallToolRequest.params.arguments: JsonObject?` e `.name`.
- `CallToolResult(content: List<ContentBlock>, isError: Boolean? = null, structuredContent: JsonObject? = null)`; `TextContent(text)` é um `ContentBlock`.
- Erro de ferramenta = `CallToolResult(content = listOf(TextContent(msg)), isError = true)`; não lançar exceção no handler.

## Transporte HTTP (Streamable HTTP) com Ktor

`Application.mcpStreamableHttp(path = "/mcp", enableDnsRebindingProtection = true, allowedHosts = null, allowedOrigins = null, eventStore = null, sseHeartbeatConfig = null, block: RoutingContext.() -> Server)`:

- instala `ContentNegotiation` (McpJson) e `SSE` sozinha;
- **`DnsRebindingProtection` já vem ligada**: `Host` precisa ser `localhost`, `127.0.0.1` ou `[::1]`; com `allowedOrigins = null` e hosts padrão, um `Origin` presente também só passa se for desses hosts. É o AC-4 do `Host`/`Origin`; o teste deve provar a recusa mesmo assim;
- o `block` roda a cada sessão nova e recebe o `RoutingContext` (cabeçalhos em `call.request.headers`): é onde se resolve o **projeto da sessão** a partir do token e se devolve um `Server` só com as ferramentas daquele projeto.

Autenticação (AC-4) antes do SDK, no `Application`:

```kotlin
embeddedServer(CIO, host = "127.0.0.1", port = 0) {
    intercept(ApplicationCallPipeline.Plugins) {
        val bearer = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        if (!sessions.matches(bearer)) { call.respond(HttpStatusCode.Unauthorized); finish() }   // sem eco, sem dizer o motivo
    }
    mcpStreamableHttp { serverFor(sessions.projectOf(call.request.headers[HttpHeaders.Authorization])) }
}.start(wait = false)
```

- `port = 0` escolhe porta livre; a porta real sai de `engine.resolvedConnectors().first().port` (função suspensa).
- Esse `intercept` roda também nos pedidos de GET/DELETE da sessão: todo pedido precisa do token, não só o primeiro.
- Limite de corpo: `mcpStreamableHttp` não tem `maxRequestBodySize` (só o `Route.mcp` SSE tem). O AC-5 (256 KiB) é sobre o tamanho do que a ferramenta devolve; limitar na ferramenta.
- Parar: `engine.stop(gracePeriodMillis = 0, timeoutMillis = 1000)`.

## O que NÃO fazer

- Não escolher outro SDK nem escrever o protocolo à mão. Não usar WebSocket nem SSE legado (`Route.mcp`).
- Não pôr o token na URL. Não logar o token nem o cabeçalho `Authorization`.
- Se algo desta nota não compilar, parar e dizer qual símbolo falhou, em vez de adivinhar.
