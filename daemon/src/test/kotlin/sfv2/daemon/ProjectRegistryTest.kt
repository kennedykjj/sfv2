package sfv2.daemon

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.DeliveryMode
import sfv2.core.ExternalEffect
import sfv2.core.Signer

class ProjectRegistryTest {

    @TempDir
    lateinit var dir: Path

    private val servers = mutableListOf<SocketServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { runCatching { it.close() } }
    }

    /** Serves factory.yml text per repository; a missing entry means the file is absent on main. */
    private class FakeSource(private val files: MutableMap<Path, String> = mutableMapOf()) : ProjectConfigSource {
        override fun read(repo: Path): String? = files[repo.toRealPath()]

        fun put(repo: Path, yaml: String) {
            files[repo.toRealPath()] = yaml
        }
    }

    private fun aRepo(name: String): Path {
        val repo = Files.createDirectories(dir.resolve(name))
        Files.createDirectories(repo.resolve(".git"))
        return repo
    }

    private fun validYaml(name: String = "alpha") = """
        project:
          name: $name
        delivery_mode: pr
        signers:
          close: policy
        layers:
          - name: core
            path: core/
          - name: daemon
            path: daemon/
    """.trimIndent()

    @Test
    fun `AC-1 a valid factory yml registers the project with name layers and policies`() {
        val repo = aRepo("alpha")
        val source = FakeSource().also { it.put(repo, validYaml()) }
        val registry = ProjectRegistry(source)

        val registered = registry.register(repo)

        assertThat(registered.name).isEqualTo("alpha")
        assertThat(registered.layers).containsExactly(ProjectLayer("core", "core/"), ProjectLayer("daemon", "daemon/"))
        assertThat(registered.policies.deliveryMode).isEqualTo(DeliveryMode.PR_HANDOFF)
        assertThat(registered.policies.signers.getValue(ExternalEffect.CLOSE)).isEqualTo(Signer.POLICY)
        assertThat(registry.list()).containsExactly(registered)
    }

    @Test
    fun `AC-2 an invalid factory yml is refused and the reply names the invalid field`() {
        val repo = aRepo("bad-policy")
        val noName = aRepo("no-name")
        val noLayers = aRepo("no-layers")
        val source = FakeSource().also {
            it.put(repo, validYaml().replace("close: policy", "close: nobody"))
            it.put(noName, validYaml().replace("name: alpha", "name: ''"))
            it.put(noLayers, "project:\n  name: alpha\nlayers: []\n")
        }
        val registry = ProjectRegistry(source)

        val policy = assertThrows(InvalidProjectConfigException::class.java) { registry.register(repo) }
        val name = assertThrows(InvalidProjectConfigException::class.java) { registry.register(noName) }
        val layers = assertThrows(InvalidProjectConfigException::class.java) { registry.register(noLayers) }

        assertThat(policy.field).isEqualTo("signers.close")
        assertThat(policy.message).contains("signers.close")
        assertThat(name.field).isEqualTo("project.name")
        assertThat(layers.field).isEqualTo("layers")
    }

    @Test
    fun `AC-3 a refused registration leaves the project out of the listing`() {
        val bad = aRepo("bad")
        val missing = aRepo("missing")
        val good = aRepo("good")
        val source = FakeSource().also {
            it.put(bad, validYaml().replace("close: policy", "close: nobody"))
            it.put(good, validYaml("good"))
        }
        val registry = ProjectRegistry(source)

        assertThrows(InvalidProjectConfigException::class.java) { registry.register(bad) }
        assertThrows(ProjectNotFoundException::class.java) { registry.register(missing) }

        assertThat(registry.list()).isEmpty()
        registry.register(good)
        assertThat(registry.list().map { it.name }).containsExactly("good")
    }

    @Test
    fun `AC-4 values come from main even when a branch changes the factory yml`() {
        val repo = aRepo("gitrepo")
        Files.delete(repo.resolve(".git"))
        git(repo, "init", "-q", "-b", "main")
        Files.createDirectories(repo.resolve(".shipfloor"))
        val file = repo.resolve(".shipfloor/factory.yml")
        Files.writeString(file, validYaml("on-main"))
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", "main config")
        git(repo, "checkout", "-q", "-b", "feat/change")
        Files.writeString(file, validYaml("on-branch"))
        git(repo, "commit", "-q", "-am", "branch config")

        val source = GitProjectConfigSource()
        val registered = ProjectRegistry(source).register(repo)

        assertThat(registered.name).isEqualTo("on-main")
        assertThat(source.read(repo)).contains("on-main").doesNotContain("on-branch")
        // Uncommitted edits in the working tree do not count either.
        Files.writeString(file, validYaml("dirty"))
        assertThat(ProjectRegistry(source).register(repo).name).isEqualTo("on-main")
    }

    @Test
    fun `AC-5 a repository without factory yml on main is refused as not found`() {
        val repo = aRepo("empty")
        val registry = ProjectRegistry(FakeSource())

        val failure = assertThrows(ProjectNotFoundException::class.java) { registry.register(repo) }

        assertThat(failure.message).contains(repo.toRealPath().toString())
        assertThat(registry.list()).isEmpty()
    }

    @Test
    fun `AC-6 registering the same repository twice is refused and the listing keeps one entry`() {
        val repo = aRepo("alpha")
        val source = FakeSource().also { it.put(repo, validYaml()) }
        val registry = ProjectRegistry(source)
        val first = registry.register(repo)
        // The same repository reached through another path spelling.
        val alias = Files.createSymbolicLink(dir.resolve("alias"), repo)

        val byPath = assertThrows(ProjectAlreadyRegisteredException::class.java) { registry.register(repo) }
        val bySymlink = assertThrows(ProjectAlreadyRegisteredException::class.java) { registry.register(alias) }

        assertThat(byPath.message).contains(repo.toRealPath().toString())
        assertThat(bySymlink.message).contains(repo.toRealPath().toString())
        assertThat(registry.list()).containsExactly(first)
    }

    // ---- AC-7: through the socket of a real daemon ----

    private fun startDaemon(): Pair<Path, String> {
        val runtime = Files.createDirectories(dir.resolve("run"))
        val env = mapOf("XDG_RUNTIME_DIR" to runtime.toString(), "XDG_DATA_HOME" to dir.resolve("data").toString())
        val launch = launchDaemon(env, PrintStream(ByteArrayOutputStream()))
        val started = launch as DaemonLaunch.Started
        servers.add(started.server)
        val socket = resolveSocketPath(env)
        val proof = Files.readString(socket.resolveSibling("owner.token")).trim()
        return socket to proof
    }

    private fun exchange(socket: Path, line: String): JsonObject {
        SocketChannel.open(UnixDomainSocketAddress.of(socket)).use { channel ->
            val out = Channels.newOutputStream(channel)
            out.write((line + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            val reply = Channels.newInputStream(channel).bufferedReader(Charsets.UTF_8).readLine()
            return Json.parseToJsonElement(reply).jsonObject
        }
    }

    private fun JsonObject.isOk(): Boolean = getValue("ok").jsonPrimitive.boolean

    private fun gitRepoWithConfig(name: String): Path {
        val repo = Files.createDirectories(dir.resolve(name))
        git(repo, "init", "-q", "-b", "main")
        Files.createDirectories(repo.resolve(".shipfloor"))
        Files.writeString(repo.resolve(".shipfloor/factory.yml"), validYaml(name))
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", "config")
        return repo
    }

    @Test
    fun `AC-7 only the owner with a valid proof registers a project`() {
        val (socket, proof) = startDaemon()
        val repo = gitRepoWithConfig("guarded")
        fun add(actor: String, proofPart: String) =
            exchange(socket, """{"cmd":"project.add","actor":"$actor","path":"$repo"$proofPart}""")
        val refused = listOf(
            add("agent:dev", ""","proof":"$proof""""),
            add("adapter:github", ""","proof":"$proof""""),
            add("factory:dispatcher", ""","proof":"$proof""""),
            add("owner", ""),
            add("owner", ""","proof":"deadbeef""""),
            add("owner", ""","proof":"${proof.dropLast(1)}${if (proof.last() == '0') '1' else '0'}""""),
        )

        for (reply in refused) {
            assertThat(reply.isOk()).isFalse()
            assertThat(reply.getValue("error").jsonPrimitive.content).isNotBlank().doesNotContain(proof)
        }
        val listed = exchange(socket, """{"cmd":"project.list"}""")
        assertThat(listed.isOk()).isTrue()
        assertThat(listed.toString()).doesNotContain("guarded")

        val accepted = add("owner", ""","proof":"$proof"""")

        assertThat(accepted.isOk()).isTrue()
        assertThat(accepted.toString()).contains("guarded")
        val after = exchange(socket, """{"cmd":"project.list"}""")
        assertThat(after.isOk()).isTrue()
        assertThat(after.toString()).contains("guarded").contains("core").contains("daemon")
    }

    private fun git(repo: Path, vararg args: String) {
        val command = listOf(
            "git", "-C", repo.toString(),
            "-c", "user.name=QA", "-c", "user.email=qa@example.invalid", "-c", "commit.gpgsign=false",
        ) + args
        val log = Files.createTempFile(dir, "git", ".log")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start()
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
        assertThat(process.exitValue()).describedAs(Files.readString(log)).isZero()
    }
}
