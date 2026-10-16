package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
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
import sfv2.ports.FakeRuntime
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionScript
import sfv2.store.CardEventStore
import sfv2.store.Database

class ValidatePipelineTest {
    @TempDir
    lateinit var dir: Path

    private val clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
    private val project = WorktreeFixtures.aProject()
    private val log = WorktreeFixtures.RecordingLog()
    private val sessions = McpSessions(clock)
    private val order = CopyOnWriteArrayList<String>()
    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: EventRecorder
    private lateinit var worktrees: WorktreeManager
    private lateinit var configDir: Path
    private lateinit var worktreeRoot: Path

    private val green = listOf(GateResult("build-daemon", true, null), GateResult("test-daemon", true, null), GateResult("scope", true, null))
    private val red = listOf(GateResult("build-daemon", true, null), GateResult("test-daemon", false, "2 failed"), GateResult("scope", false, "outside"))
    private val approved = "VERDICT: APPROVED"

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("validate.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
        worktreeRoot = Files.createDirectories(dir.resolve("worktrees"))
        configDir = Files.createDirectories(dir.resolve("mcp-config-dir"))
        worktrees = WorktreeManager(worktreeRoot, WorktreeFixtures.FakeWorktreeGit(), log)
    }

    @AfterEach
    fun close() {
        stream.close()
        database.close()
    }

    private fun card(id: String) {
        val event = Event(EventId("seed-$id"), clock.instant(), Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Validate $id"))
        store.recordChange(id, Card.create("Validate $id", Column.VALIDATION), event)
    }

    private fun report(cardId: String, summary: String, at: Instant) {
        val current = requireNotNull(store.card(cardId))
        val event = Event(
            EventId(UUID.randomUUID().toString()), at, Actor.Agent("validate"), EventType.AGENT_REPORTED,
            mapOf("column" to current.column.name, "summary" to summary),
        )
        recorder.record(cardId, current, event)
    }

    /** Merges answer by the script (the last repeats) and note the call in [order]. */
    private fun sync(vararg results: MergeResult) = BranchSync {
        order.add("merge")
        results[minOf(order.count { it == "merge" } - 1, results.size - 1)]
    }

    private fun gates(vararg rounds: List<GateResult>) = ValidateGates { _, _ ->
        order.add("gates")
        ValidateGateResults(rounds[minOf(order.count { it == "gates" } - 1, rounds.size - 1)])
    }

    /** One session per entry; the entry is what the agent reports during it (null: it reports nothing). */
    private fun reviewing(vararg reports: String?): ReviewingRuntime {
        var index = 0
        return ReviewingRuntime(FakeRuntime(List(reports.size) { SessionScript.Succeed })) {
            order.add("review")
            reports[index++]?.let { report("c1", it, clock.instant().plusSeconds(1)) }
        }
    }

    private class ReviewingRuntime(private val inner: FakeRuntime, private val onLaunch: () -> Unit) : Runtime {
        fun launched(): List<SessionRequest> = inner.launched

        override fun launch(request: SessionRequest) = inner.launch(request).also { onLaunch() }
    }

    private fun pipeline(runtime: Runtime, sync: BranchSync, gates: ValidateGates, maxRounds: Int = 3) = ValidatePipeline(
        project = project,
        store = store,
        recorder = recorder,
        worktrees = worktrees,
        sessions = sessions,
        mcpPort = 4317,
        runtime = runtime,
        costRecorder = RunCostRecorder(store, recorder, RunCostFixtures.prices(), clock, log),
        branchSync = sync,
        gates = gates,
        configDir = configDir,
        clock = clock,
        model = "model-a",
        maxRounds = maxRounds,
        log = log,
    )

    private fun column(id: String): Column = requireNotNull(store.card(id)).column

    private fun events(type: EventType) = store.events("c1").filter { it.type == type }

    @Test
    fun `AC-1 a card in Validation syncs the branch with main, reruns the gates and then runs the review, events with actor`() {
        card("c1")
        val runtime = reviewing(approved)

        pipeline(runtime, sync(MergeResult.Merged), gates(green)).run("c1")

        assertThat(order).containsExactly("merge", "gates", "review")
        assertThat(runtime.launched().single().stage).isEqualTo("validate")
        assertThat(runtime.launched().single().worktree).isEqualTo(worktreeRoot.resolve("alpha").resolve("c1"))
        assertThat(events(EventType.CARD_MOVED).map { it.actor }).containsOnly(Actor.Factory("validate"))
        assertThat(events(EventType.RUN_COST_RECORDED)).hasSize(1)
    }

    @Test
    fun `AC-2 green gates and an APPROVED review move the card to Staged`() {
        card("c1")

        pipeline(reviewing(approved), sync(MergeResult.Merged), gates(green)).run("c1")

        assertThat(column("c1")).isEqualTo(Column.STAGED)
        assertThat(events(EventType.CARD_BLOCKED)).isEmpty()
    }

    @Test
    fun `AC-3 an APPROVED review does not win over a red gate, the card is blocked listing the failed gates`() {
        card("c1")
        val runtime = reviewing(approved)

        pipeline(runtime, sync(MergeResult.Merged), gates(red)).run("c1")

        assertThat(column("c1")).isNotEqualTo(Column.STAGED)
        assertThat(store.events("c1").flatMap { it.payload.values }).doesNotContain(Column.STAGED.name)
        val blocked = events(EventType.CARD_BLOCKED).single()
        assertThat(blocked.actor).isEqualTo(Actor.Factory("validate"))
        assertThat(requireNotNull(blocked.payload["reason"])).contains("test-daemon").contains("scope").doesNotContain("build-daemon")
        assertThat(runtime.launched()).hasSize(1)
    }

    @Test
    fun `AC-4 a blocking finding returns the card to Coding with the layer and reason, ignoring an older report`() {
        card("c1")
        report("c1", approved, clock.instant().minusSeconds(3600))

        pipeline(reviewing("VERDICT: BLOCKED layer=daemon\nAC-2 has no test for the edge case"), sync(MergeResult.Merged), gates(green)).run("c1")

        assertThat(column("c1")).isEqualTo(Column.CODING)
        val move = events(EventType.CARD_MOVED).single()
        assertThat(move.payload["layer"]).isEqualTo("daemon")
        assertThat(move.payload["reason"]).isEqualTo("AC-2 has no test for the edge case")
        assertThat(events(EventType.CARD_BLOCKED)).isEmpty()
    }

    @Test
    fun `AC-5 a conflict with main blocks the card with a readable reason and runs no review`() {
        card("c1")
        val runtime = reviewing(approved)

        pipeline(runtime, sync(MergeResult.Conflict), gates(green)).run("c1")

        assertThat(runtime.launched()).isEmpty()
        assertThat(order).containsExactly("merge")
        assertThat(column("c1")).isNotEqualTo(Column.STAGED)
        val blocked = events(EventType.CARD_BLOCKED).single()
        assertThat(blocked.actor).isEqualTo(Actor.Factory("validate"))
        assertThat(requireNotNull(blocked.payload["reason"])).contains("merge_conflict")
    }

    @Test
    fun `AC-5 a git failure while syncing blocks the card and runs no review`() {
        card("c1")
        val runtime = reviewing(approved)

        pipeline(runtime, sync(MergeResult.Failed), gates(green)).run("c1")

        assertThat(runtime.launched()).isEmpty()
        assertThat(requireNotNull(events(EventType.CARD_BLOCKED).single().payload["reason"])).contains("git_failed")
        assertThat(column("c1")).isNotEqualTo(Column.STAGED)
    }

    @Test
    fun `AC-5 malformed reports in every round block the card after the last round, gates rerun each round`() {
        card("c1")
        val runtime = reviewing("looks fine to me", "VERDICT: MAYBE", null)

        pipeline(runtime, sync(MergeResult.Merged), gates(green), maxRounds = 3).run("c1")

        assertThat(runtime.launched()).hasSize(3)
        assertThat(order.count { it == "gates" }).isEqualTo(3)
        assertThat(column("c1")).isNotEqualTo(Column.STAGED)
        val blocked = events(EventType.CARD_BLOCKED).single()
        assertThat(blocked.actor).isEqualTo(Actor.Factory("validate"))
        assertThat(requireNotNull(blocked.payload["reason"])).contains("review_malformed")
    }

    @Test
    fun `AC-5 a malformed report followed by a valid one in a later round is accepted`() {
        card("c1")

        pipeline(reviewing("garbage", approved), sync(MergeResult.Merged), gates(green), maxRounds = 3).run("c1")

        assertThat(column("c1")).isEqualTo(Column.STAGED)
        assertThat(events(EventType.CARD_BLOCKED)).isEmpty()
    }

    @Test
    fun `AC-6 the MCP config lives outside the worktree with mode 0600 while the run lasts and is gone with its session after`() {
        card("c1")
        val seen = mutableListOf<Triple<Path, String, String>>()
        val runtime = RefineFixtures.ProbingRuntime(FakeRuntime(listOf(SessionScript.Succeed))) { request ->
            val perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(request.mcpConfig))
            seen.add(Triple(request.mcpConfig, perms, Files.readString(request.mcpConfig)))
            report("c1", approved, clock.instant().plusSeconds(1))
        }

        pipeline(runtime, sync(MergeResult.Merged), gates(green)).run("c1")

        val (path, perms, content) = seen.single()
        assertThat(path.toAbsolutePath().normalize().startsWith(worktreeRoot)).isFalse()
        assertThat(path.toAbsolutePath().normalize().startsWith(configDir)).isTrue()
        assertThat(perms).isEqualTo("rw-------")
        val token = tokenIn(content)
        assertThat(Files.exists(path)).isFalse()
        assertThat(configDir.listDirectoryEntries()).isEmpty()
        assertThat(sessions.find(token)).isNull()
        assertNoLeak(token)
    }

    @Test
    fun `AC-6 the session belongs to the validate role and the card while the run lasts`() {
        card("c1")
        var live: McpSession? = null
        val runtime = RefineFixtures.ProbingRuntime(FakeRuntime(listOf(SessionScript.Succeed))) { request ->
            live = sessions.find(tokenIn(Files.readString(request.mcpConfig)))
        }

        pipeline(runtime, sync(MergeResult.Merged), gates(green), maxRounds = 1).run("c1")

        assertThat(live).isEqualTo(McpSession(project.repo, "validate", "c1"))
    }

    @Test
    fun `AC-6 the session is revoked and the config deleted when the run throws`() {
        card("c1")
        val seen = mutableListOf<String>()
        val runtime = RefineFixtures.ProbingRuntime(FakeRuntime(listOf(SessionScript.Succeed)), failure = IllegalStateException("boom")) { request ->
            seen.add(Files.readString(request.mcpConfig))
        }

        runCatching { pipeline(runtime, sync(MergeResult.Merged), gates(green)).run("c1") }

        val token = tokenIn(seen.single())
        assertThat(configDir.listDirectoryEntries()).isEmpty()
        assertThat(sessions.find(token)).isNull()
        assertNoLeak(token)
    }

    @Test
    fun `AC-6 a run that throws releases the card lock of the pool`() {
        assertThat(lockReleasedAfter(failure = IllegalStateException("boom"), script = emptyList())).isTrue()
    }

    @Test
    fun `AC-6 a run that times out releases the card lock of the pool and removes the config`() {
        assertThat(lockReleasedAfter(failure = null, script = listOf(SessionScript.Hang))).isTrue()
        assertThat(configDir.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `AC-1 the dispatcher hands a Validation card to the validate launcher, which holds the lock until the run ends`() {
        card("c1")
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val runtime = reviewing(approved)
        val launcher = ValidateStageLauncher({ pipeline(runtime, sync(MergeResult.Merged), gates(green)) }, pool)
        val dispatcher = DispatcherFixtures.dispatcherFor(
            setOf(DispatchStage.VALIDATE), store, pool, launcher, DispatcherFixtures.FakePause(), AlwaysWorkTime, clock, log,
        )
        try {
            assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.VALIDATE, "c1"))
            assertThat(RefineFixtures.await { column("c1") == Column.STAGED && !pool.isLocked("c1") }).isTrue()
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `AC-6 the validate launcher delegates to the shared PipelineStageLauncher and keeps no thread or lock handling of its own`() {
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val launcher = ValidateStageLauncher({ pipeline(reviewing(approved), sync(MergeResult.Merged), gates(green)) }, pool)

        val fieldTypes = ValidateStageLauncher::class.java.declaredFields.map { it.type }
        val methods = ValidateStageLauncher::class.java.declaredMethods.map { it.name }

        assertThat(fieldTypes).contains(PipelineStageLauncher::class.java)
        assertThat(methods).doesNotContain("execute", "awaitLock")
        assertThat(launcher).isInstanceOf(StageLauncher::class.java)
    }

    @Test
    fun `AC-7 a run that throws is logged as validate_failed with the card id and still frees the lock`() {
        card("c1")
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val runtime = RefineFixtures.ProbingRuntime(FakeRuntime(listOf(SessionScript.Succeed)), hold = null, failure = IllegalStateException("boom"))
        val launcher = ValidateStageLauncher({ pipeline(runtime, sync(MergeResult.Merged), gates(green)) }, pool, log)
        val dispatcher = DispatcherFixtures.dispatcherFor(
            setOf(DispatchStage.VALIDATE), store, pool, launcher, DispatcherFixtures.FakePause(), AlwaysWorkTime, clock, log,
        )
        try {
            assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.VALIDATE, "c1"))
            assertThat(RefineFixtures.await { !pool.isLocked("c1") }).isTrue()
            assertThat(log.lines).anyMatch { it.first == "validate_failed" && it.second["card"] == "c1" }
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `AC-1 a pipeline built without maxRounds runs the documented default constant of rounds`() {
        card("c1")
        val runtime = reviewing(*arrayOfNulls<String>(DEFAULT_VALIDATE_MAX_ROUNDS + 2))
        val defaulted = ValidatePipeline(
            project = project, store = store, recorder = recorder, worktrees = worktrees, sessions = sessions, mcpPort = 4317,
            runtime = runtime, costRecorder = RunCostRecorder(store, recorder, RunCostFixtures.prices(), clock, log),
            branchSync = sync(MergeResult.Merged), gates = gates(green), configDir = configDir, clock = clock, model = "model-a", log = log,
        )

        defaulted.run("c1")

        assertThat(DEFAULT_VALIDATE_MAX_ROUNDS).isEqualTo(3)
        assertThat(runtime.launched()).hasSize(DEFAULT_VALIDATE_MAX_ROUNDS)
        assertThat(order.count { it == "gates" }).isEqualTo(DEFAULT_VALIDATE_MAX_ROUNDS)
    }

    @Test
    fun `AC-2 with maxRounds of N and gates that keep failing the pipeline stops after N rounds and reports the failure`() {
        card("c1")
        val runtime = reviewing(null, null, null, null, null)

        pipeline(runtime, sync(MergeResult.Merged), gates(red), maxRounds = 2).run("c1")

        assertThat(runtime.launched()).hasSize(2)
        assertThat(order.count { it == "gates" }).isEqualTo(2)
        assertThat(column("c1")).isNotEqualTo(Column.STAGED)
        assertThat(events(EventType.CARD_BLOCKED)).hasSize(1)
    }

    @Test
    fun `AC-3 one type turns executor, scope and spec results into the matching GatesState`() {
        val passing = SpecCheck(SpecWeight.STANDARD, emptyList())
        val failing = SpecCheck(SpecWeight.STANDARD, listOf(SpecFinding(SpecGate.WEIGHT, null, "weight mismatch")))

        assertThat(ValidateGateResults.fromExecutor("build-daemon", GateVerdict.Pass).state).isEqualTo(GatesState.GREEN)
        assertThat(ValidateGateResults.fromExecutor("test-daemon", GateVerdict.Fail("2 failed")).state).isEqualTo(GatesState.RED)
        assertThat(ValidateGateResults.fromScope(ScopeVerdict.Pass).state).isEqualTo(GatesState.GREEN)
        assertThat(ValidateGateResults.fromScope(ScopeVerdict.Violations(listOf("CLAUDE.md"), emptyList())).state).isEqualTo(GatesState.RED)
        assertThat(ValidateGateResults.fromSpec(passing).state).isEqualTo(GatesState.GREEN)
        assertThat(ValidateGateResults.fromSpec(failing).state).isEqualTo(GatesState.RED)
        assertThat(ValidateGateResults(emptyList()).state).isEqualTo(GatesState.GREEN)
    }

    @Test
    fun `AC-3 the conversions keep the gate names and reasons the events and the card show`() {
        val executor = ValidateGateResults.fromExecutor("test-daemon", GateVerdict.Fail("2 failed")).results
        val scope = ValidateGateResults.fromScope(ScopeVerdict.Violations(listOf("CLAUDE.md"), listOf("a", "b"))).results
        val spec = ValidateGateResults.fromSpec(
            SpecCheck(SpecWeight.STANDARD, listOf(SpecFinding(SpecGate.WEIGHT, null, "weight mismatch"))),
        ).results

        assertThat(executor).containsExactly(GateResult("test-daemon", false, "2 failed"))
        assertThat(ValidateGateResults.fromExecutor("build-daemon", GateVerdict.Pass).results)
            .containsExactly(GateResult("build-daemon", true, null))
        assertThat(scope).containsExactly(GateResult("scope", false, "forbidden=1 outside=2"))
        assertThat(ValidateGateResults.fromScope(ScopeVerdict.Pass).results).containsExactly(GateResult("scope", true, null))
        assertThat(spec).containsExactly(GateResult("test-plan", true, null), GateResult("weight", false, "weight mismatch"))
    }

    @Test
    fun `AC-4 failedNames lists only the failed gates in order`() {
        assertThat(ValidateGateResults(red).failedNames).containsExactly("test-daemon", "scope")
        assertThat(ValidateGateResults(green).failedNames).isEmpty()
        val spec = SpecCheck(
            SpecWeight.STANDARD,
            listOf(SpecFinding(SpecGate.TEST_PLAN, "AC-1", "no row"), SpecFinding(SpecGate.WEIGHT, null, "mismatch")),
        )
        assertThat(ValidateGateResults.fromSpec(spec).failedNames).containsExactly("test-plan", "weight")
    }

    @Test
    fun `AC-5 the pipeline reports the same gates_failed reason and lets green gates through as before`() {
        card("c1")
        pipeline(reviewing(approved), sync(MergeResult.Merged), gates(red)).run("c1")

        assertThat(events(EventType.CARD_BLOCKED).single().payload["reason"]).isEqualTo("gates_failed: test-daemon,scope")
        assertThat(column("c1")).isNotEqualTo(Column.STAGED)
    }

    private fun lockReleasedAfter(failure: RuntimeException?, script: List<SessionScript>): Boolean {
        card("c1")
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val hold = CountDownLatch(1)
        val runtime = RefineFixtures.ProbingRuntime(FakeRuntime(script), hold = hold, failure = failure)
        val launcher = ValidateStageLauncher({ pipeline(runtime, sync(MergeResult.Merged), gates(green)) }, pool)
        val dispatcher = DispatcherFixtures.dispatcherFor(
            setOf(DispatchStage.VALIDATE), store, pool, launcher, DispatcherFixtures.FakePause(), AlwaysWorkTime, clock, log,
        )
        try {
            assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.VALIDATE, "c1"))
            assertThat(runtime.started.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(pool.isLocked("c1")).isTrue()
            hold.countDown()
            return RefineFixtures.await { !pool.isLocked("c1") }
        } finally {
            hold.countDown()
            dispatcher.close()
        }
    }

    private fun tokenIn(config: String): String {
        val candidates = Regex("[A-Za-z0-9_-]{43}").findAll(config).map { it.value }.toList()
        return requireNotNull(candidates.firstOrNull { sessions.find(it) != null } ?: candidates.lastOrNull()) {
            "no session token found in the MCP config"
        }
    }

    private fun assertNoLeak(token: String) {
        val logged = log.lines.flatMap { listOf(it.first) + it.second.keys + it.second.values }
        val eventValues = store.eventsAfter(0).flatMap { it.event.payload.values }
        assertThat(logged + eventValues).noneMatch { it.contains(token) }
    }
}
