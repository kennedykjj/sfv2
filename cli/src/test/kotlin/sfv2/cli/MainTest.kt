package sfv2.cli

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CopyOnWriteArrayList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MainTest {
    @TempDir
    lateinit var runtimeDir: Path

    private val out = StringBuilder()
    private val err = StringBuilder()
    private val requests = CopyOnWriteArrayList<String>()
    private var server: ServerSocketChannel? = null

    private fun run(vararg args: String): Int =
        runCli(args.toList(), "1.2.3", { out.appendLine(it) }, { err.appendLine(it) }, env = mapOf("XDG_RUNTIME_DIR" to runtimeDir.toString()))

    private fun socketDir(): Path = runtimeDir.resolve("sfv2")

    private fun tokenPath(): Path = socketDir().resolve("owner.token")

    /** A fake of the daemon protocol: one request per connection, answered with the lines [reply] returns. */
    private fun fakeDaemon(reply: (String) -> List<String>) {
        Files.createDirectories(socketDir())
        val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        channel.bind(UnixDomainSocketAddress.of(socketDir().resolve("daemon.sock")))
        server = channel
        Thread.ofPlatform().daemon().start {
            try {
                while (true) {
                    val client = channel.accept()
                    val input = Channels.newInputStream(client).bufferedReader()
                    val line = input.readLine()
                    if (line != null) {
                        requests.add(line)
                        val output = Channels.newOutputStream(client)
                        reply(line).forEach { output.write((it + "\n").toByteArray()) }
                        output.flush()
                    }
                    client.close()
                }
            } catch (e: ClosedChannelException) {
                // test over
            }
        }
    }

    @AfterEach
    fun stopFake() {
        server?.close()
    }

    @Test
    fun `--version prints the version and exits 0`() {
        assertThat(run("--version")).isZero()
        assertThat(out.toString().trim()).isEqualTo("sfv2 1.2.3")
        assertThat(err).isEmpty()
    }

    @Test
    fun `an unknown argument prints the usage and exits 2`() {
        assertThat(run("--nope")).isEqualTo(2)
        assertThat(err.toString()).contains("Usage: sfv2").contains("--nope")
        assertThat(out).isEmpty()
    }

    @Test
    fun `--help prints the usage and exits 0`() {
        assertThat(run("--help")).isZero()
        assertThat(out.toString()).contains("Usage: sfv2").contains("--version")
    }

    @Test
    fun `the version comes from the build`() {
        assertThat(BuildInfo.version).isNotBlank().doesNotContain("\${")
    }

    @Test
    fun `AC-1 card create prints the new card`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        fakeDaemon { listOf("""{"ok":true,"result":{"id":"c1","title":"Write docs","column":"INBOX"}}""") }

        assertThat(run("card", "create", "Write docs")).isZero()

        assertThat(out.toString().trim()).isEqualTo("c1  INBOX  Write docs")
        assertThat(requests.single()).contains("\"card.create\"").contains("\"Write docs\"")
    }

    @Test
    fun `card import-board sends the tsv rows and prints created and ignored cards`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        val tsv = runtimeDir.resolve("board.tsv")
        Files.writeString(tsv, "12\tTechnical Refinement\textra\n\n13\tNowhere\n")
        fakeDaemon {
            listOf(
                """{"ok":true,"result":[{"id":"c12","title":"Twelve","column":"REFINEMENT"}],"ignored":[{"id":"13","reason":"unknown_column"}]}""",
            )
        }

        assertThat(run("card", "import-board", tsv.toString(), "--project", "p1")).isZero()

        assertThat(out.toString().lines().filter { it.isNotBlank() })
            .containsExactly("c12  REFINEMENT  Twelve", "ignorada: #13 (unknown_column)")
        val request = requests.single()
        assertThat(request).contains("\"card.import_board\"").contains("\"project\":\"p1\"")
            .contains("""{"id":"12","column":"Technical Refinement"}""").contains("""{"id":"13","column":"Nowhere"}""")
    }

    @Test
    fun `card import-board fails in portuguese when the file cannot be read`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")

        assertThat(run("card", "import-board", runtimeDir.resolve("missing.tsv").toString())).isEqualTo(1)

        assertThat(err.toString()).contains("não foi possível ler o arquivo")
    }

    @Test
    fun `AC-1 card list prints one line per card`() {
        fakeDaemon {
            listOf(
                """{"ok":true,"result":[{"id":"c1","title":"One","column":"INBOX"},{"id":"c2","title":"Two","column":"CODING"}]}""",
            )
        }

        assertThat(run("card", "list")).isZero()

        assertThat(out.toString().lines().filter { it.isNotBlank() }).containsExactly("c1  INBOX  One", "c2  CODING  Two")
        assertThat(requests.single()).contains("\"card.list\"")
    }

    @Test
    fun `AC-1 card show prints the card and an unknown card is a daemon error`() {
        fakeDaemon { line ->
            if (line.contains("\"c1\"")) {
                listOf("""{"ok":true,"result":{"id":"c1","title":"One","column":"VALIDATION"}}""")
            } else {
                listOf("""{"ok":false,"error":"card not found: zz"}""")
            }
        }

        assertThat(run("card", "show", "c1")).isZero()
        assertThat(out.toString().trim()).isEqualTo("c1  VALIDATION  One")

        out.clear()
        assertThat(run("card", "show", "zz")).isEqualTo(1)
        assertThat(err.toString()).contains("card not found: zz")
        assertThat(out).isEmpty()
    }

    @Test
    fun `AC-1 card move accepts a lowercase dashed column and rejects an unknown one with usage`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        fakeDaemon { listOf("""{"ok":true,"result":{"id":"c1","title":"One","column":"READY_FOR_DEV"}}""") }

        assertThat(run("card", "move", "c1", "ready-for-dev")).isZero()
        assertThat(out.toString().trim()).isEqualTo("c1  READY_FOR_DEV  One")
        assertThat(requests.single()).contains("\"card.move\"").contains("\"READY_FOR_DEV\"").contains("\"c1\"")

        requests.clear()
        err.clear()
        assertThat(run("card", "move", "c1", "nowhere")).isEqualTo(2)
        assertThat(err.toString()).contains("INBOX").contains("DONE")
        assertThat(requests).isEmpty()
    }

    @Test
    fun `AC-1 events prints each event line, skips the ack and stops after count`() {
        val event1 = """{"event":{"seq":4,"card":"c1","type":"CARD_CREATED"}}"""
        val event2 = """{"event":{"seq":5,"card":"c1","type":"CARD_MOVED"}}"""
        fakeDaemon { listOf("""{"ok":true,"result":"subscribed"}""", event1, event2) }

        assertThat(run("events", "--after", "3", "--count", "2")).isZero()

        assertThat(out.toString().lines().filter { it.isNotBlank() }).containsExactly(event1, event2)
        assertThat(requests.single()).contains("\"events.subscribe\"").containsPattern("\"after\"\\s*:\\s*3")
    }

    @Test
    fun `AC-1 events exits 1 with the message when the stream ends with an error`() {
        fakeDaemon { listOf("""{"ok":true,"result":"subscribed"}""", """{"error":"subscriber too slow"}""") }

        assertThat(run("events")).isEqualTo(1)
        assertThat(err.toString()).contains("subscriber too slow")
    }

    @Test
    fun `AC-5 approve and request-change send the owner decision and print the card, a daemon refusal exits 1`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "secret-token\n")
        fakeDaemon { line ->
            if (line.contains("\"gone\"")) {
                listOf("""{"ok":false,"error":"card gone is in Coding, not in Spec Review"}""")
            } else {
                listOf("""{"ok":true,"result":{"id":"c1","title":"One","column":"READY_FOR_DEV"}}""")
            }
        }

        assertThat(run("card", "approve", "c1")).isZero()
        assertThat(out.toString().trim()).isEqualTo("c1  READY_FOR_DEV  One")
        assertThat(run("card", "request-change", "c1", "--note", "Cover offline")).isZero()

        assertThat(requests).hasSize(2)
        assertThat(requests[0]).contains("\"card.approve\"").contains("\"c1\"")
        assertThat(requests[1]).contains("\"card.request-change\"").contains("\"c1\"").contains("Cover offline")
        requests.forEach {
            assertThat(it).containsPattern("\"proof\"\\s*:\\s*\"secret-token\"").containsPattern("\"actor\"\\s*:\\s*\"owner\"")
        }

        out.clear()
        assertThat(run("card", "approve", "gone")).isEqualTo(1)
        assertThat(err.toString()).contains("card gone is in Coding")
        assertThat(out).isEmpty()
    }

    @Test
    fun `card rebuild sends the owner request with the project and prints one line per card`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "secret-token\n")
        fakeDaemon {
            listOf("""{"ok":true,"result":[{"id":"c1","title":"One","column":"CODING"},{"id":"c2","title":"Two","column":"PR_REVIEW"}]}""")
        }

        assertThat(run("card", "rebuild", "--project", "sfv2")).isZero()

        assertThat(out.toString().lines().filter { it.isNotBlank() })
            .containsExactly("c1  CODING  One", "c2  PR_REVIEW  Two")
        assertThat(requests).hasSize(1)
        assertThat(requests[0]).contains("\"card.rebuild\"").contains("\"sfv2\"")
            .containsPattern("\"proof\"\\s*:\\s*\"secret-token\"").containsPattern("\"actor\"\\s*:\\s*\"owner\"")
    }

    @Test
    fun `AC-5 request-change without a note is a usage error and sends nothing`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        fakeDaemon { listOf("""{"ok":true,"result":{"id":"c1","title":"One","column":"READY_FOR_REFINEMENT"}}""") }

        assertThat(run("card", "request-change", "c1")).isEqualTo(2)

        assertThat(err.toString()).contains("--note")
        assertThat(requests).isEmpty()
        assertThat(out).isEmpty()
    }

    @Test
    fun `AC-5 events shows the owner decisions with actor, moment and the change note`() {
        val approved = """{"event":{"seq":7,"card":"c1","at":"2026-10-16T12:00:00Z","actor":"owner","type":"OWNER_APPROVED","payload":{"from":"SPEC_REVIEW","to":"READY_FOR_DEV"}}}"""
        val changed = """{"event":{"seq":8,"card":"c2","at":"2026-10-16T12:01:00Z","actor":"owner","type":"CARD_MOVED","payload":{"from":"SPEC_REVIEW","to":"READY_FOR_REFINEMENT","note":"Cover offline"}}}"""
        fakeDaemon { listOf("""{"ok":true,"result":"subscribed"}""", approved, changed) }

        assertThat(run("events", "--after", "6", "--count", "2")).isZero()

        val lines = out.toString().lines().filter { it.isNotBlank() }
        assertThat(lines).containsExactly(approved, changed)
        assertThat(lines[0]).contains("\"actor\":\"owner\"").contains("2026-10-16T12:00:00Z").contains("OWNER_APPROVED")
        assertThat(lines[1]).contains("\"actor\":\"owner\"").contains("2026-10-16T12:01:00Z").contains("Cover offline")
    }

    @Test
    fun `AC-2 a stopped daemon is reported with how to start it and exit 1`() {
        // No socket at all.
        assertThat(run("card", "list")).isEqualTo(1)
        assertThat(err.toString()).contains("daemon não está rodando").contains("./gradlew :daemon:run")

        // A stale socket file nobody listens on, with a token present.
        err.clear()
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use {
            it.bind(UnixDomainSocketAddress.of(socketDir().resolve("daemon.sock")))
        }
        assertThat(run("card", "create", "x")).isEqualTo(1)
        assertThat(err.toString()).contains("daemon não está rodando").contains("./gradlew :daemon:run")
        assertThat(out).isEmpty()
    }

    @Test
    fun `AC-4 create and move send the owner token as proof with the owner actor`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "secret-token\n")
        fakeDaemon { listOf("""{"ok":true,"result":{"id":"c1","title":"One","column":"INBOX"}}""") }

        assertThat(run("card", "create", "One")).isZero()
        assertThat(run("card", "move", "c1", "ready_for_refinement")).isZero()

        assertThat(requests).hasSize(2)
        requests.forEach {
            assertThat(it).containsPattern("\"proof\"\\s*:\\s*\"secret-token\"").containsPattern("\"actor\"\\s*:\\s*\"owner\"")
        }
    }

    @Test
    fun `AC-4 reads never send the token`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "secret-token\n")
        fakeDaemon { listOf("""{"ok":true,"result":[]}""") }

        assertThat(run("card", "list")).isZero()

        assertThat(requests.single()).doesNotContain("secret-token")
    }

    @Test
    fun `AC-5 a missing, empty or unreadable token fails before any request reaches the daemon`() {
        fakeDaemon { listOf("""{"ok":true,"result":{"id":"c1","title":"One","column":"INBOX"}}""") }

        // Missing.
        assertThat(run("card", "create", "One")).isEqualTo(1)
        assertThat(err.toString()).contains(tokenPath().toString()).contains("inicie o daemon")

        // Empty.
        err.clear()
        Files.writeString(tokenPath(), "")
        assertThat(run("card", "move", "c1", "refinement")).isEqualTo(1)
        assertThat(err.toString()).contains(tokenPath().toString()).contains("inicie o daemon")

        // Unreadable: a directory where the file should be.
        err.clear()
        Files.delete(tokenPath())
        Files.createDirectory(tokenPath())
        assertThat(run("card", "create", "One")).isEqualTo(1)
        assertThat(err.toString()).contains(tokenPath().toString()).contains("inicie o daemon")

        assertThat(out).isEmpty()
        assertThat(requests).isEmpty()
    }

    // ---- sfv2 project ----

    private fun projectDir(): Path = Files.createDirectories(runtimeDir.resolve("repos").resolve("alpha"))

    @Test
    fun `AC-1 project add registers the canonical path with the owner proof and prints name and repo`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "secret-token\n")
        val dir = projectDir()
        val canonical = dir.toRealPath().toString()
        fakeDaemon { line ->
            if (line.contains("\"project.list\"")) {
                listOf("""{"ok":true,"result":[]}""")
            } else {
                listOf("""{"ok":true,"result":{"name":"alpha","repo":"acme/alpha","layers":[{"name":"core"}]}}""")
            }
        }

        assertThat(run("project", "add", dir.toString())).isZero()

        assertThat(out.toString().trim()).isEqualTo("alpha  acme/alpha")
        val add = requests.single { it.contains("\"project.add\"") }
        assertThat(add).contains(canonical)
            .containsPattern("\"proof\"\\s*:\\s*\"secret-token\"").containsPattern("\"actor\"\\s*:\\s*\"owner\"")
    }

    @Test
    fun `AC-2 project add fails with the daemon message and a non-zero exit`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        fakeDaemon { line ->
            if (line.contains("\"project.list\"")) {
                listOf("""{"ok":true,"result":[]}""")
            } else {
                listOf("""{"ok":false,"error":"factory.yml invalid: layers[0].test is required"}""")
            }
        }

        assertThat(run("project", "add", runtimeDir.resolve("not-a-repo").toString())).isNotZero()

        assertThat(err.toString()).contains("factory.yml invalid: layers[0].test is required")
        assertThat(out).isEmpty()
    }

    @Test
    fun `AC-3 project add on an already registered path exits 0 without sending project add`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "tok\n")
        val dir = projectDir()
        val canonical = dir.toRealPath().toString()
        fakeDaemon { line ->
            if (line.contains("\"project.list\"")) {
                listOf("""{"ok":true,"result":[{"name":"alpha","repo":"$canonical","layers":[]}]}""")
            } else {
                listOf("""{"ok":false,"error":"project already registered: alpha"}""")
            }
        }

        assertThat(run("project", "add", dir.toString())).isZero()

        assertThat(out.toString().trim()).isEqualTo("alpha  $canonical")
        assertThat(err).isEmpty()
        assertThat(requests.none { it.contains("\"project.add\"") }).isTrue()
    }

    @Test
    fun `AC-4 project list prints name and repo per project, without layers or the token`() {
        Files.createDirectories(socketDir())
        Files.writeString(tokenPath(), "secret-token\n")
        fakeDaemon {
            listOf(
                """{"ok":true,"result":[{"name":"alpha","repo":"acme/alpha","layers":[{"name":"core","test":"gradle test"}]},{"name":"beta","repo":"acme/beta","layers":[]}]}""",
            )
        }

        assertThat(run("project", "list")).isZero()

        assertThat(out.toString().lines().filter { it.isNotBlank() }).containsExactly("alpha  acme/alpha", "beta  acme/beta")
        assertThat(out.toString()).doesNotContain("layers").doesNotContain("gradle test")
        assertThat(requests.single()).contains("\"project.list\"").doesNotContain("secret-token")
    }

    // ---- sfv2 doctor ----

    private val checkNames = listOf("java", "git", "gh", "claude", "socket", "banco", "serviço", "permissões", "ambiente do serviço")
    private val secret = "s3cr3t-do-not-print"

    private fun binDir(): Path = runtimeDir.resolve("bin")

    private fun dataHome(): Path = runtimeDir.resolve("data")

    private fun configHome(): Path = runtimeDir.resolve("config")

    private fun serviceEnvPath(): Path = configHome().resolve("sfv2").resolve("service.env")

    private fun chmod(path: Path, mode: String) = Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode))

    private fun fakeTool(name: String) {
        Files.createDirectories(binDir())
        val file = binDir().resolve(name)
        Files.writeString(file, "#!/bin/sh\nexit 0\n")
        chmod(file, "rwxr-xr-x")
    }

    /** A machine where every check passes: tools on PATH, daemon listening, database, unit, token, service.env. */
    private fun healthyMachine() {
        listOf("java", "git", "gh", "claude").forEach { fakeTool(it) }
        fakeDaemon { listOf("""{"ok":true,"result":[]}""") }
        chmod(socketDir(), "rwx------")
        Files.writeString(tokenPath(), "$secret\n")
        chmod(tokenPath(), "rw-------")
        val dataDir = Files.createDirectories(dataHome().resolve("sfv2"))
        chmod(dataDir, "rwx------")
        Files.writeString(dataDir.resolve("sfv2.db"), "")
        Files.createDirectories(configHome().resolve("systemd").resolve("user"))
        Files.writeString(configHome().resolve("systemd").resolve("user").resolve("sfv2.service"), "[Service]\n")
        Files.createDirectories(serviceEnvPath().parent)
        Files.writeString(serviceEnvPath(), "TOKEN=$secret\n")
        chmod(serviceEnvPath(), "rw-------")
    }

    private fun runDoctor(): Int =
        runCli(
            listOf("doctor"),
            "1.2.3",
            { out.appendLine(it) },
            { err.appendLine(it) },
            env = mapOf(
                "XDG_RUNTIME_DIR" to runtimeDir.toString(),
                "XDG_DATA_HOME" to dataHome().toString(),
                "XDG_CONFIG_HOME" to configHome().toString(),
                "HOME" to runtimeDir.resolve("home").toString(),
                "PATH" to binDir().toString(),
            ),
        )

    private fun doctorLines(): List<String> = out.toString().lines().filter { it.isNotBlank() }

    private fun checkLine(name: String): String =
        doctorLines().singleOrNull { it.startsWith("OK $name") || it.startsWith("FAIL $name") }
            ?: error("no line for check '$name' in:\n$out")

    @Test
    fun `AC-1 doctor prints one OK or FAIL line per check`() {
        healthyMachine()

        runDoctor()

        assertThat(doctorLines()).hasSize(checkNames.size)
        checkNames.forEach { assertThat(checkLine(it)).startsWith("OK $it") }
    }

    @Test
    fun `AC-2 a failing check prints FAIL with a concrete instruction`() {
        healthyMachine()
        Files.delete(binDir().resolve("gh"))

        runDoctor()

        val line = checkLine("gh")
        assertThat(line).startsWith("FAIL gh — ")
        assertThat(line.substringAfter("FAIL gh — ").trim()).isNotBlank()
    }

    @Test
    fun `AC-3 doctor exits 0 when every check passes`() {
        healthyMachine()

        assertThat(runDoctor()).isZero()

        assertThat(doctorLines().filter { it.startsWith("FAIL") }).isEmpty()
        assertThat(err).isEmpty()
    }

    @Test
    fun `AC-4 one failing check exits non-zero and the other checks are still listed`() {
        healthyMachine()
        Files.delete(binDir().resolve("claude"))

        assertThat(runDoctor()).isEqualTo(1)

        assertThat(doctorLines()).hasSize(checkNames.size)
        assertThat(checkLine("claude")).startsWith("FAIL claude")
        checkNames.filter { it != "claude" }.forEach { assertThat(checkLine(it)).startsWith("OK $it") }
    }

    @Test
    fun `AC-5 doctor without a running daemon fails socket, banco and serviço with hints and no error`() {
        listOf("java", "git", "gh", "claude").forEach { fakeTool(it) }

        assertThat(runDoctor()).isEqualTo(1)

        listOf("socket", "banco", "serviço").forEach {
            val line = checkLine(it)
            assertThat(line).startsWith("FAIL $it — ")
            assertThat(line.substringAfter("— ").trim()).isNotBlank()
        }
        assertThat(doctorLines()).hasSize(checkNames.size)
        assertThat(err).isEmpty()
    }

    @Test
    fun `AC-5 a stale socket file nobody listens on is a FAIL for socket`() {
        listOf("java", "git", "gh", "claude").forEach { fakeTool(it) }
        Files.createDirectories(socketDir())
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use {
            it.bind(UnixDomainSocketAddress.of(socketDir().resolve("daemon.sock")))
        }

        assertThat(runDoctor()).isEqualTo(1)

        assertThat(checkLine("socket")).startsWith("FAIL socket — ")
    }

    @Test
    fun `AC-6 doctor output never contains the token or the service env content`() {
        healthyMachine()
        runDoctor()
        assertThat(doctorLines()).hasSize(checkNames.size)
        assertThat(out.toString() + err.toString()).doesNotContain(secret)

        // Also on the failure path, where modes and paths are printed.
        out.clear()
        err.clear()
        chmod(tokenPath(), "rw-r--r--")
        chmod(serviceEnvPath(), "rw-r--r--")
        runDoctor()
        assertThat(checkLine("permissões")).startsWith("FAIL permissões")
        assertThat(out.toString() + err.toString()).doesNotContain(secret).doesNotContain("TOKEN=")
    }

    @Test
    fun `AC-7 a token more open than 0600 fails permissões with the chmod 600 command and the path`() {
        healthyMachine()
        chmod(tokenPath(), "rw-r--r--")

        assertThat(runDoctor()).isEqualTo(1)

        val line = checkLine("permissões")
        assertThat(line).startsWith("FAIL permissões")
        assertThat(line).contains("chmod 600").contains(tokenPath().toString())
        assertThat(out.toString()).doesNotContain(secret)
    }

    @Test
    fun `AC-7 a data directory open to group or others fails permissões with the chmod 700 command`() {
        healthyMachine()
        val dataDir = dataHome().resolve("sfv2")
        chmod(dataDir, "rwxr-xr-x")

        assertThat(runDoctor()).isEqualTo(1)

        val line = checkLine("permissões")
        assertThat(line).startsWith("FAIL permissões")
        assertThat(line).contains("chmod 700").contains(dataDir.toString())
    }

    @Test
    fun `AC-8 ambiente do serviço checks service env mode 0600 and warns about the systemd PATH`() {
        healthyMachine()

        runDoctor()

        val ok = checkLine("ambiente do serviço")
        assertThat(ok).startsWith("OK ambiente do serviço").contains("PATH").contains("systemd")

        out.clear()
        chmod(serviceEnvPath(), "rw-rw-r--")
        assertThat(runDoctor()).isEqualTo(1)
        assertThat(checkLine("ambiente do serviço")).startsWith("FAIL ambiente do serviço").contains("chmod 600")

        out.clear()
        Files.delete(serviceEnvPath())
        assertThat(runDoctor()).isEqualTo(1)
        assertThat(checkLine("ambiente do serviço")).startsWith("FAIL ambiente do serviço").contains(serviceEnvPath().toString())
    }
}
