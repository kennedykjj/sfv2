package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.ProjectPolicies
import sfv2.store.CardEventStore
import sfv2.store.Database

class DevelopGatesTest {

    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-16T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var worktree: Path
    private val specs = mutableListOf<GateSpec>()

    private val runner = object : GateRunner {
        override fun run(spec: GateSpec, outputFile: Path): GateRun {
            specs += spec
            Files.writeString(outputFile, "Test PASSED\n")
            return GateRun(exitCode = 0, timedOut = false)
        }
    }

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("cards.db"))
        store = CardEventStore(database)
        store.recordChange(
            "c1",
            Card.create("Develop", Column.CODING),
            Event(EventId("seed"), now, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Develop")),
        )
        worktree = Files.createDirectories(dir.resolve("wt"))
    }

    @AfterEach
    fun close() {
        database.close()
    }

    private fun writeSpec(vararg rows: String) {
        val specDir = Files.createDirectories(worktree.resolve("docs/superpowers/specs"))
        val table = rows.joinToString("\n") { "| AC-1 | daemon | `$it` |" }
        Files.writeString(specDir.resolve("spec.md"), "## Test plan\n\n| AC | Layer | Test file |\n|---|---|---|\n$table\n\n## Security\n")
    }

    private fun gates(layer: ProjectLayer): DevelopGates {
        val project = Project("alpha", "/repos/alpha", listOf(layer), ProjectPolicies.DEFAULT)
        val recorder = EventRecorder(store, EventStream(store))
        val paths = object : ChangedPaths {
            override fun between(worktree: Path, base: String): List<String> = emptyList()
        }
        return RealDevelopGates(
            project, store, recorder, GateExecutor(store, recorder, clock, runner), runner, paths, dir.resolve("runs"), clock,
            head = WorktreeHead { "abcdef1" },
        )
    }

    private val tokens = listOf("../gradlew", ":daemon:test", "--tests", "{selector}", "--rerun")

    @Test
    fun `green repeats the flag and the selector for each test class of the layer`() {
        writeSpec("daemon/src/test/kotlin/sfv2/daemon/FirstTest.kt", "daemon/src/test/kotlin/sfv2/daemon/SecondTest.kt")
        val layer = ProjectLayer("daemon", "daemon/", test = tokens, ran = "PASSED")
        val gates = gates(layer)

        gates.red("c1", worktree)
        val results = gates.green("c1", layer, worktree)

        val test = specs.first { it.name == "daemon-tests" }
        assertThat(test.command).containsExactly(
            "../gradlew", ":daemon:test", "--tests", "FirstTest", "--tests", "SecondTest", "--rerun",
        )
        assertThat(test.workingDir).isEqualTo(worktree.resolve("daemon/"))
        assertThat(results.map { it.name }).contains("daemon-tests", "scope")
    }

    @Test
    fun `a layer without test command fails its gate with no_test_command`() {
        writeSpec("daemon/src/test/kotlin/sfv2/daemon/FirstTest.kt")
        val layer = ProjectLayer("daemon", "daemon/")

        val results = gates(layer).red("c1", worktree)

        assertThat(results).containsExactly(GateResult("daemon-red", false, "no_test_command"))
        assertThat(specs).isEmpty()
    }

    @Test
    fun `a test class name outside the allowed characters fails with invalid_selector and runs nothing`() {
        writeSpec("daemon/src/test/kotlin/sfv2/daemon/Bad-Name.kt")
        val layer = ProjectLayer("daemon", "daemon/", test = tokens)

        val results = gates(layer).red("c1", worktree)

        assertThat(results).containsExactly(GateResult("daemon-red", false, "invalid_selector"))
        assertThat(specs).isEmpty()
    }

    @Test
    fun `selector substitution keeps every token as one argument and handles a single class`() {
        val command = RealDevelopGates.substituteSelector(tokens, listOf("OnlyTest"))

        assertThat(command).containsExactly("../gradlew", ":daemon:test", "--tests", "OnlyTest", "--rerun")
    }
}
