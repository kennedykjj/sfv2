package sfv2.daemon

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    private lateinit var registry: ProjectRegistry
    private lateinit var sessions: McpSessions
    private lateinit var endpoint: McpEndpoint
    private val http = HttpClient.newHttpClient()
    private val clock = MutableClock(Instant.parse("2026-10-16T10:00:00Z"))

    @BeforeEach
    fun setUp() {
        database = Database.open(dir.resolve("factory.db"))
        store = CardEventStore(database)
        val specs = SpecStore(database, dir.resolve("specs"))
        val config = McpFixtures.FakeConfigSource()
        registry = ProjectRegistry(config)
        alpha = register(registry, config, "alpha")
        beta = register(registry, config, "beta")
        sessions = McpSessions(clock, TTL)
        endpoint = McpServerFactory(sessions, store, specs, registry, McpFixtures.FakeFileSource()).start()
        aCard("mine", alpha, "Add login")
        aCard("theirs", beta, "Secret work")
    }

    @AfterEach
    fun tearDown() {
        // HttpClient.close() waits for its selector thread and connections, so their fds are released before the next test class.
        http.close()
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

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private class RecordingLog : DaemonLog {
        val lines = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        override fun log(event: String, fields: Map<String, String>) {
            lines.add(event to fields)
        }
    }

    private class ThrowingFiles : ProjectFileSource {
        override fun read(repo: Path, path: String): String? = throw IllegalStateException("cannot open /home/secret/agent-path/file")
    }

    private fun post(
        body: String,
        token: String?,
        origin: String? = null,
        sessionId: String? = null,
        port: Int = endpoint.port,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (origin != null) builder.header("Origin", origin)
        if (sessionId != null) builder.header("Mcp-Session-Id", sessionId)
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun initialize(token: String, port: Int = endpoint.port): String {
        val init = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",""" +
            """"capabilities":{},"clientInfo":{"name":"t","version":"0"}}}"""
        val response = post(init, token, port = port)
        assertThat(response.statusCode()).isEqualTo(200)
        val sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null)
        assertThat(sessionId).isNotNull()
        post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", token, sessionId = sessionId, port = port)
        return sessionId
    }

    private fun callCard(token: String, sessionId: String, cardId: String): HttpResponse<String> = post(
        """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"card_get","arguments":{"cardId":"$cardId"}}}""",
        token,
        sessionId = sessionId,
    )

    private fun listTools(token: String, sessionId: String): HttpResponse<String> =
        post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", token, sessionId = sessionId)

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

    @Test
    fun `AC-1 a session id opened with the token of one project is refused for another token and stays intact`() {
        val tokenA = sessions.open(alpha.repo)
        val tokenB = sessions.open(beta.repo)
        val sessionA = initialize(tokenA)

        val foreign = listTools(tokenB, sessionA)
        val unknown = listTools(tokenB, "no-such-session")
        val guard = McpRequestGuard(sessions, endpoint.port)

        assertThat(foreign.statusCode()).isEqualTo(401)
        assertThat(foreign.body()).isEmpty()
        assertThat(unknown.statusCode()).isEqualTo(foreign.statusCode())
        assertThat(unknown.body()).isEqualTo(foreign.body())
        assertThat(guard.authorize("127.0.0.1:${endpoint.port}", null, "Bearer $tokenB", sessionA)).isNull()
        assertThat(guard.authorize("127.0.0.1:${endpoint.port}", null, "Bearer $tokenA", sessionA)?.repo).isEqualTo(alpha.repo)
        assertThat(listTools(tokenA, sessionA).body()).contains("card_get")
        assertThat(callCard(tokenA, sessionA, "mine").body()).contains("Add login")
    }

    @Test
    fun `AC-2 a token is valid one tick before its ttl and refused at the ttl with its sessions`() {
        val token = sessions.open(alpha.repo)
        val sessionId = initialize(token)

        clock.now = clock.now.plus(TTL).minusMillis(1)
        assertThat(sessions.find(token)?.repo).isEqualTo(alpha.repo)
        assertThat(listTools(token, sessionId).statusCode()).isEqualTo(200)

        clock.now = clock.now.plusMillis(1)
        assertThat(sessions.find(token)).isNull()
        assertThat(listTools(token, sessionId).statusCode()).isEqualTo(401)
        assertThat(post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""", token).statusCode()).isEqualTo(401)
    }

    @Test
    fun `AC-2 closing a token refuses it and its sessions without touching another project's token`() {
        val tokenA = sessions.open(alpha.repo)
        val tokenB = sessions.open(beta.repo)
        val sessionA = initialize(tokenA)
        val sessionB = initialize(tokenB)

        sessions.close(tokenA)

        assertThat(sessions.find(tokenA)).isNull()
        assertThat(listTools(tokenA, sessionA).statusCode()).isEqualTo(401)
        assertThat(sessions.owns(sessionA, tokenA)).isFalse()
        assertThat(sessions.find(tokenB)?.repo).isEqualTo(beta.repo)
        assertThat(listTools(tokenB, sessionB).body()).contains("card_get")
    }

    @Test
    fun `AC-2 a ttl that is zero or negative fails at construction`() {
        assertThatThrownBy { McpSessions(clock, Duration.ZERO) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { McpSessions(clock, Duration.ofSeconds(-1)) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `AC-4 an infrastructure error reaches the agent as a fixed message and the detail only the daemon log`() {
        val log = RecordingLog()
        val failing = McpServerFactory(
            sessions,
            store,
            SpecStore(database, dir.resolve("specs")),
            registry,
            ThrowingFiles(),
            log,
        ).start()
        try {
            val token = sessions.open(alpha.repo)
            val sessionId = initialize(token, failing.port)

            val body = post(
                """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"review_criteria_get","arguments":{}}}""",
                token,
                sessionId = sessionId,
                port = failing.port,
            ).body()

            assertThat(body).contains("\"isError\":true").contains("tool failed (code=internal)")
            assertThat(body).doesNotContain("/home/secret").doesNotContain("IllegalStateException")
            val failure = log.lines.single { it.first == "mcp_tool_failed" }.second
            assertThat(failure).containsEntry("tool", "review_criteria_get").containsEntry("code", "internal")
                .containsEntry("error", "IllegalStateException")
            assertThat(log.lines.flatMap { it.second.values }).noneMatch { it.contains("/home/secret") }
        } finally {
            failing.close()
        }
    }

    private companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}
