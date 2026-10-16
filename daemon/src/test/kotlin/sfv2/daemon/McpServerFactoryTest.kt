package sfv2.daemon

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.Card
import sfv2.core.Column
import sfv2.store.CardEventStore
import sfv2.store.Database
import sfv2.store.SpecStore

class McpServerFactoryTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var alpha: Project
    private lateinit var beta: Project
    private lateinit var sessions: McpSessions
    private lateinit var endpoint: McpEndpoint
    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        database = Database.open(dir.resolve("factory.db"))
        store = CardEventStore(database)
        val specs = SpecStore(database, dir.resolve("specs"))
        val config = McpFixtures.FakeConfigSource()
        val registry = ProjectRegistry(config)
        alpha = register(registry, config, "alpha")
        beta = register(registry, config, "beta")
        sessions = McpSessions()
        endpoint = McpServerFactory(sessions, store, specs, registry, McpFixtures.FakeFileSource()).start()
        aCard("mine", alpha, "Add login")
        aCard("theirs", beta, "Secret work")
    }

    @AfterEach
    fun tearDown() {
        endpoint.close()
        database.close()
    }

    private fun register(registry: ProjectRegistry, config: McpFixtures.FakeConfigSource, name: String): Project {
        val repo = Files.createDirectories(dir.resolve(name))
        Files.createDirectories(repo.resolve(".git"))
        config.files[repo.toRealPath()] = McpFixtures.yaml(name)
        return registry.register(repo)
    }

    private fun aCard(id: String, project: Project, title: String) {
        store.recordChange(id, Card.create(title, Column.CODING), McpFixtures.anEvent("e-$id"))
        store.assignProject(id, project.repo)
    }

    private fun post(body: String, token: String?, origin: String? = null, sessionId: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${endpoint.port}/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (origin != null) builder.header("Origin", origin)
        if (sessionId != null) builder.header("Mcp-Session-Id", sessionId)
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun initialize(token: String): String {
        val init = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",""" +
            """"capabilities":{},"clientInfo":{"name":"t","version":"0"}}}"""
        val response = post(init, token)
        assertThat(response.statusCode()).isEqualTo(200)
        val sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null)
        assertThat(sessionId).isNotNull()
        post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", token, sessionId = sessionId)
        return sessionId
    }

    private fun callCard(token: String, sessionId: String, cardId: String): HttpResponse<String> = post(
        """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"card_get","arguments":{"cardId":"$cardId"}}}""",
        token,
        sessionId = sessionId,
    )

    @Test
    fun `a valid token lists the five tools and reads a card of its project marked untrusted`() {
        val token = sessions.open(alpha.repo)
        val sessionId = initialize(token)

        val list = post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", token, sessionId = sessionId)
        assertThat(list.body()).contains("card_get", "spec_get", "review_criteria_get", "policies_get", "layers_get")

        val card = callCard(token, sessionId, "mine")
        assertThat(card.body()).contains("Add login").contains("untrusted").doesNotContain("\"isError\":true")
    }

    @Test
    fun `a card of another project is refused through the server as a tool error without its content`() {
        val token = sessions.open(alpha.repo)
        val sessionId = initialize(token)

        val body = callCard(token, sessionId, "theirs").body()

        assertThat(body).contains("\"isError\":true").contains("theirs").doesNotContain("Secret work")
    }

    @Test
    fun `missing or unknown token is refused with 401 and no body`() {
        val init = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""

        val missing = post(init, null)
        val unknown = post(init, "not-a-token")

        assertThat(missing.statusCode()).isEqualTo(401)
        assertThat(unknown.statusCode()).isEqualTo(401)
        assertThat(missing.body()).isEmpty()
        assertThat(unknown.body()).doesNotContain("not-a-token")
    }

    @Test
    fun `a request with a foreign origin is refused even with a valid token`() {
        val token = sessions.open(alpha.repo)

        val response = post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""", token, origin = "http://evil.example")

        assertThat(response.statusCode()).isEqualTo(401)
    }
}
