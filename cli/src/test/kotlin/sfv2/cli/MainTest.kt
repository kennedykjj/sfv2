package sfv2.cli

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
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
}
