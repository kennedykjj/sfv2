package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore
import sfv2.store.Database

class GateExecutorTest {

    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-16T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var runDir: Path

    /** Fake runner: writes [output] to the output file and reports [run]; counts how often it ran. */
    private class FakeRunner(private val output: String, private val run: GateRun) : GateRunner {
        var calls = 0

        override fun run(spec: GateSpec, outputFile: Path): GateRun {
            calls++
            Files.writeString(outputFile, output)
            return run
        }
    }

    @BeforeEach
    fun openStore() {
        database = Database.open(dir.resolve("cards.db"))
        store = CardEventStore(database)
        store.recordChange(
            "card-1",
            Card.create("Executor de gates", Column.INBOX),
            Event(EventId("evt-0"), now, Actor.Owner, EventType.CARD_CREATED, mapOf("card" to "card-1")),
        )
        runDir = dir.resolve("runs").resolve("run-1")
    }

    @AfterEach
    fun closeStore() {
        database.close()
    }

    private fun spec(name: String = "lint") = GateSpec(
        name = name,
        command = listOf("./gate.sh"),
        workingDir = dir,
        timeout = Duration.ofSeconds(30),
    )

    private fun executor(runner: GateRunner) = GateExecutor(store, EventStream(store), clock, runner)

    private fun gateEvents(): List<Event> = store.events("card-1").filter { it.type.name == "GATE_FINISHED" }

    @Test
    fun `AC-1 a passing gate is recorded as a factory gate event with verdict and summary`() {
        val runner = FakeRunner("all good\n", GateRun(exitCode = 0, timedOut = false))

        val verdict = executor(runner).execute("card-1", spec(), runDir)

        assertThat(verdict).isEqualTo(GateVerdict.Pass)
        val event = gateEvents().single()
        assertThat(event.actor).isEqualTo(Actor.Factory("gate"))
        assertThat(event.at).isEqualTo(now)
        assertThat(event.payload["gate"]).isEqualTo("lint")
        assertThat(event.payload["verdict"]).isEqualTo("PASS")
        assertThat(event.payload["exit_code"]).isEqualTo("0")
        assertThat(event.payload["summary"]).contains("all good")
    }

    @Test
    fun `AC-1 a failing gate is recorded with verdict FAIL, exit code and the tail of its output`() {
        val runner = FakeRunner("compiling\nerror: boom\n", GateRun(exitCode = 3, timedOut = false))

        val verdict = executor(runner).execute("card-1", spec(), runDir)

        assertThat(verdict).isInstanceOf(GateVerdict.Fail::class.java)
        val event = gateEvents().single()
        assertThat(event.payload["verdict"]).isEqualTo("FAIL")
        assertThat(event.payload["reason"]).isEqualTo("exit_code")
        assertThat(event.payload["exit_code"]).isEqualTo("3")
        assertThat(event.payload["summary"]).contains("error: boom")
    }

    @Test
    fun `AC-2 a gate that exceeds the time limit fails with reason timeout and no exit code`() {
        val runner = FakeRunner("still working\n", GateRun(exitCode = null, timedOut = true))

        val verdict = executor(runner).execute("card-1", spec(), runDir)

        assertThat(verdict).isInstanceOf(GateVerdict.Fail::class.java)
        val event = gateEvents().single()
        assertThat(event.payload["verdict"]).isEqualTo("FAIL")
        assertThat(event.payload["reason"]).isEqualTo("timeout")
        assertThat(event.payload).doesNotContainKey("exit_code")
    }

    @Test
    fun `AC-3 long output stays in the run file and the event keeps only a bounded summary`() {
        val lines = (1..200).joinToString("\n") { "line-$it" } + "\n"
        val runner = FakeRunner(lines, GateRun(exitCode = 1, timedOut = false))

        executor(runner).execute("card-1", spec(), runDir)

        val event = gateEvents().single()
        val outputFile = Path.of(requireNotNull(event.payload["output_file"]) { "output_file missing" })
        assertThat(outputFile.startsWith(runDir)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(lines)
        val summary = requireNotNull(event.payload["summary"]) { "summary missing" }
        assertThat(summary.lines().filter { it.isNotBlank() }).hasSize(20)
        assertThat(summary).contains("line-200").contains("line-181").doesNotContain("line-180\n")
        assertThat(summary).doesNotContain("line-1\n")
        assertThat(event.payload.values.sumOf { it.length }).isLessThan(lines.length)
    }

    @Test
    fun `AC-3 a single huge line is cut to at most 2000 characters in the summary`() {
        val runner = FakeRunner("x".repeat(100_000) + "\n", GateRun(exitCode = 1, timedOut = false))

        executor(runner).execute("card-1", spec(), runDir)

        val event = gateEvents().single()
        assertThat(requireNotNull(event.payload["summary"]).length).isLessThanOrEqualTo(2000)
        assertThat(Files.size(Path.of(requireNotNull(event.payload["output_file"])))).isGreaterThan(100_000L)
    }

    @Test
    fun `AC-4 an invalid gate name is refused naming the field, with no process and no file`() {
        listOf("", "..", "a/b", "../x", "-lint", "Lint", "a b").forEach { bad ->
            val runner = FakeRunner("never\n", GateRun(exitCode = 0, timedOut = false))

            val error = assertThrows<RuntimeException> { executor(runner).execute("card-1", spec(bad), runDir) }

            assertThat(error).hasMessageContaining("name")
            assertThat(runner.calls).isZero()
            assertThat(runDir).doesNotExist()
            assertThat(gateEvents()).isEmpty()
        }
    }
}
