package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
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
import sfv2.core.ProjectPolicies
import sfv2.ports.ChecksState
import sfv2.ports.FakeForge
import sfv2.ports.FakeRuntime
import sfv2.ports.Runtime
import sfv2.ports.SessionRequest
import sfv2.ports.SessionResult
import sfv2.ports.SessionScript
import sfv2.store.CardEventStore
import sfv2.store.Database

object DevelopFixtures {
    /** Two layers: a round is one QA session plus one engineer session per layer (3 sessions). */
    val project = Project("alpha", "/repos/alpha", listOf(ProjectLayer("daemon", "daemon/"), ProjectLayer("core", "core/")), ProjectPolicies.DEFAULT)
    const val SESSIONS_PER_ROUND = 3

    fun await(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.yield()
        }
        return condition()
    }

    val green = listOf(GateResult("daemon-tests", true, null), GateResult("daemon-build", true, null))
    val red = listOf(GateResult("daemon-tests", false, "tests_failed"), GateResult("daemon-build", true, null), GateResult("scope", false, "outside=1"))

    /**
     * Answers the red gate (always green) and, per round, the green gates of the first layer from [rounds]
     * (the last list repeats); every other layer is green. [calls] interleaves with the sessions of [ProbingRuntime].
     */
    class ScriptedDevelopGates(
        private val rounds: List<List<GateResult>>,
        private val hold: CountDownLatch? = null,
        private val plannedLayers: List<ProjectLayer> = project.layers,
        private val onFailure: (String, List<GateResult>) -> Unit = { _, _ -> },
    ) : DevelopGates {
        val calls = CopyOnWriteArrayList<String>()
        private var round = 0

        override fun layers(worktree: Path): List<ProjectLayer> = plannedLayers

        override fun red(cardId: String, worktree: Path): List<GateResult> {
            hold?.await(10, TimeUnit.SECONDS)
            calls.add("red")
            return listOf(GateResult("acceptance-red", true, null))
        }

        override fun green(cardId: String, layer: ProjectLayer, worktree: Path): List<GateResult> {
            calls.add("green:${layer.name}")
            if (layer.name == "daemon") round++
            if (layer.name != "daemon") return listOf(GateResult("${layer.name}-tests", true, null))
            val result = rounds[minOf(round - 1, rounds.size - 1)]
            val failed = result.filter { !it.passed }
            if (failed.isNotEmpty()) onFailure(cardId, failed)
            return result
        }
    }

    class ProbingRuntime(
        private val inner: Runtime,
        private val calls: MutableList<String>? = null,
        private val hold: CountDownLatch? = null,
        private val failure: RuntimeException? = null,
        private val onLaunch: (SessionRequest) -> Unit = {},
    ) : Runtime {
        val started = CountDownLatch(1)

        override fun launch(request: SessionRequest): SessionResult {
            calls?.add("session")
            onLaunch(request)
            started.countDown()
            hold?.await(10, TimeUnit.SECONDS)
            failure?.let { throw it }
            return inner.launch(request)
        }
    }
}

class DevelopPipelineTest {
    @TempDir
    lateinit var dir: Path

    private val clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
    private val project = DevelopFixtures.project
    private val log = WorktreeFixtures.RecordingLog()
    private val sessions = McpSessions(clock)
    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: EventRecorder
    private lateinit var stepUp: ModelStepUp
    private lateinit var worktrees: WorktreeManager
    private lateinit var configDir: Path
    private lateinit var worktreeRoot: Path

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("develop.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
        stepUp = ModelStepUp(listOf("model-a", "model-b"), store)
        stream.addListener(stepUp::onEvent)
        worktreeRoot = Files.createDirectories(dir.resolve("worktrees"))
        configDir = Files.createDirectories(dir.resolve("mcp-config-dir"))
        worktrees = WorktreeManager(worktreeRoot, WorktreeFixtures.FakeWorktreeGit(), log)
    }

    @AfterEach
    fun close() {
        stepUp.close()
        stream.close()
        database.close()
    }

    private fun card(id: String, column: Column = Column.READY_FOR_DEV) {
        val event = Event(EventId("seed-$id"), clock.instant(), Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Develop $id"))
        store.recordChange(id, Card.create("Develop $id", column), event)
    }

    private fun pipeline(runtime: Runtime, gates: DevelopGates, commit: WorktreeCommit = WorktreeCommit.None, maxRounds: Int = 3) = DevelopPipeline(
        project = project,
        store = store,
        recorder = recorder,
        worktrees = worktrees,
        sessions = sessions,
        mcpPort = 4317,
        runtime = runtime,
        costRecorder = RunCostRecorder(store, recorder, RunCostFixtures.prices(), clock, log),
        gates = gates,
        modelStepUp = stepUp,
        configDir = configDir,
        clock = clock,
        maxRounds = maxRounds,
        log = log,
        commit = commit,
    )

    private fun column(id: String): Column = requireNotNull(store.card(id)).column

    private fun succeeding(times: Int) = FakeRuntime(List(times) { SessionScript.Succeed })

    private fun rounds(count: Int) = count * DevelopFixtures.SESSIONS_PER_ROUND

    @Test
    fun `AC-1 a spec that plans tests for one layer gets one engineer session and green gates for that layer only`() {
        card("c1")
        val daemonOnly = DevelopFixtures.project.layers.filter { it.name == "daemon" }
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green), plannedLayers = daemonOnly)
        val runtime = succeeding(2)

        pipeline(runtime, gates).run("c1")

        assertThat(runtime.launched).hasSize(2)
        assertThat(gates.calls.filter { it.startsWith("green:") }).containsExactly("green:daemon")
        assertThat(column("c1")).isEqualTo(Column.VALIDATION)
    }

    @Test
    fun `AC-1 a card in Ready for Dev moves to Coding, QA runs first and then one engineer session per layer, events with actor`() {
        card("c1")
        val calls = CopyOnWriteArrayList<String>()
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))
        val runtime = succeeding(rounds(1))

        pipeline(DevelopFixtures.ProbingRuntime(runtime, calls), RecordingGates(gates, calls)).run("c1")

        assertThat(runtime.launched).hasSize(rounds(1))
        assertThat(runtime.launched.map { it.stage }).containsOnly("develop")
        assertThat(runtime.launched.map { it.worktree }).containsOnly(worktreeRoot.resolve("alpha").resolve("c1"))
        assertThat(calls.take(2)).containsExactly("session", "red")
        assertThat(gates.calls).contains("green:daemon", "green:core")
        val moves = store.events("c1").filter { it.type == EventType.CARD_MOVED }
        assertThat(moves.map { it.payload["to"] }).containsExactly(Column.CODING.name, Column.VALIDATION.name)
        assertThat(moves.map { it.actor }).containsOnly(Actor.Factory("develop"))
        assertThat(store.events("c1").count { it.type == EventType.RUN_COST_RECORDED }).isEqualTo(rounds(1))
    }

    @Test
    fun `AC-1 every session prompt carries the develop skill, the card and its role, QA first and then one engineer per layer`() {
        card("c1")
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))
        val runtime = succeeding(rounds(1))

        pipeline(runtime, gates).run("c1")

        val prompts = runtime.launched.map { it.prompt }
        assertThat(prompts).allSatisfy { assertThat(it).startsWith(StageSkills.load("develop").trimEnd()).contains("`c1`") }
        assertThat(prompts.first()).contains("QA (passo vermelho)")
        assertThat(prompts.drop(1)).allSatisfy { assertThat(it).contains("engenheiro da camada") }
        assertThat(prompts.drop(1).map { it.substringAfter("camada `").substringBefore("`") })
            .containsExactlyInAnyOrderElementsOf(DevelopFixtures.project.layers.map { it.name })
    }

    @Test
    fun `AC-1 what each session wrote is committed, the QA tests before the red gate and each layer after its engineer`() {
        card("c1")
        val calls = CopyOnWriteArrayList<String>()
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))
        val commits = CopyOnWriteArrayList<String>()
        val commit = WorktreeCommit { _, message -> commits.add(message).also { calls.add("commit") } }
        val runtime = DevelopFixtures.ProbingRuntime(succeeding(rounds(1)), calls)

        pipeline(runtime, RecordingGates(gates, calls), commit).run("c1")

        assertThat(calls.take(3)).containsExactly("session", "commit", "red")
        assertThat(commits).hasSize(1 + DevelopFixtures.project.layers.size)
        assertThat(commits.first()).startsWith("test(tests)")
    }

    @Test
    fun `AC-2 a finished run with every gate green moves the card to Validation`() {
        card("c1")
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))

        pipeline(succeeding(rounds(1)), gates).run("c1")

        assertThat(column("c1")).isEqualTo(Column.VALIDATION)
        assertThat(gates.calls.count { it == "red" }).isEqualTo(1)
    }

    @Test
    fun `AC-3 the agent saying done does not advance the card while a gate is red`() {
        card("c1")
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.red))

        pipeline(succeeding(rounds(1)), gates, maxRounds = 1).run("c1")

        assertThat(column("c1")).isNotEqualTo(Column.VALIDATION)
        assertThat(store.events("c1").flatMap { it.payload.values }).doesNotContain(Column.VALIDATION.name)
    }

    @Test
    fun `AC-4 red gates with rounds left start the next round on the next model of the step-up and the reason stays on the card`() {
        card("c1")
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.red, DevelopFixtures.green)) { cardId, failed ->
            failed.forEach { gate ->
                val payload = mapOf("gate" to gate.name, "verdict" to "FAIL", "reason" to requireNotNull(gate.reason), "summary" to "failed")
                val event = Event(EventId("gate-${gate.name}"), clock.instant(), Actor.Factory("gate"), EventType.GATE_FINISHED, payload)
                recorder.record(cardId, requireNotNull(store.card(cardId)), event)
            }
            // the step-up listens asynchronously; the real gates run long enough, the fake must wait for it
            assertThat(DevelopFixtures.await { stepUp.modelFor(cardId) == "model-b" }).isTrue()
        }
        val runtime = succeeding(rounds(2))

        pipeline(runtime, gates, maxRounds = 3).run("c1")

        assertThat(runtime.launched.map { it.model }).containsExactly(
            "model-a", "model-a", "model-a", "model-b", "model-b",
        )
        assertThat(column("c1")).isEqualTo(Column.VALIDATION)
        val reasons = store.events("c1").filter { it.type == EventType.GATE_FINISHED }.map { it.payload["reason"] }
        assertThat(reasons).contains("tests_failed")
    }

    @Test
    fun `AC-4 a round after a failed green gate reruns only the engineers, with the failure in their prompt, and never replays the red gate`() {
        card("c1")
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.red, DevelopFixtures.green)) { cardId, failed ->
            failed.forEach { gate ->
                val payload = mapOf("gate" to gate.name, "verdict" to "FAIL", "reason" to requireNotNull(gate.reason), "summary" to "failed")
                val event = Event(EventId("gate-${gate.name}"), clock.instant(), Actor.Factory("gate"), EventType.GATE_FINISHED, payload)
                recorder.record(cardId, requireNotNull(store.card(cardId)), event)
            }
            assertThat(DevelopFixtures.await { stepUp.modelFor(cardId) == "model-b" }).isTrue()
        }
        val runtime = succeeding(rounds(2))

        pipeline(runtime, gates, maxRounds = 3).run("c1")

        assertThat(gates.calls.count { it == "red" }).isEqualTo(1)
        val prompts = runtime.launched.map { it.prompt }
        assertThat(prompts.take(3).filterNot { it.contains("Gates que falharam") }).hasSize(3)
        assertThat(prompts.drop(3)).hasSize(DevelopFixtures.project.layers.size)
        assertThat(prompts.drop(3)).allSatisfy {
            assertThat(it).contains("engenheiro da camada").contains("Gates que falharam na rodada anterior").contains("daemon-tests")
        }
        assertThat(column("c1")).isEqualTo(Column.VALIDATION)
    }

    @Test
    fun `AC-5 red gates after the last round block the card naming the failed gates and start no new run`() {
        card("c1")
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.red))
        val runtime = succeeding(rounds(5))

        pipeline(runtime, gates, maxRounds = 3).run("c1")

        assertThat(runtime.launched).hasSize(rounds(1) + 2 * DevelopFixtures.project.layers.size)
        val blocked = store.events("c1").single { it.type == EventType.CARD_BLOCKED }
        assertThat(blocked.actor).isEqualTo(Actor.Factory("develop"))
        val reason = requireNotNull(blocked.payload["reason"])
        assertThat(reason).contains("daemon-tests").contains("scope").doesNotContain("daemon-build")
        assertThat(column("c1")).isNotEqualTo(Column.VALIDATION)
    }

    @Test
    fun `AC-7 every session config lives outside the worktree with mode 0600 and is gone with its session after the run`() {
        card("c1")
        val seen = CopyOnWriteArrayList<Triple<Path, String, String>>()
        val runtime = DevelopFixtures.ProbingRuntime(succeeding(rounds(1)), onLaunch = { request ->
            val perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(request.mcpConfig))
            seen.add(Triple(request.mcpConfig, perms, Files.readString(request.mcpConfig)))
        })

        pipeline(runtime, DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))).run("c1")

        assertThat(seen).hasSize(rounds(1))
        seen.forEach { (path, perms, content) ->
            assertThat(path.toAbsolutePath().normalize().startsWith(worktreeRoot)).isFalse()
            assertThat(path.toAbsolutePath().normalize().startsWith(configDir)).isTrue()
            assertThat(perms).isEqualTo("rw-------")
            assertThat(Files.exists(path)).isFalse()
            val token = tokenIn(content)
            assertThat(sessions.find(token)).isNull()
            assertNoLeak(token)
        }
        assertThat(configDir.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `AC-7 the session is revoked and the config deleted when the run throws`() {
        card("c1")
        val seen = mutableListOf<String>()
        val runtime = DevelopFixtures.ProbingRuntime(
            succeeding(rounds(1)),
            failure = IllegalStateException("boom"),
            onLaunch = { request -> seen.add(Files.readString(request.mcpConfig)) },
        )

        runCatching { pipeline(runtime, DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))).run("c1") }

        val token = tokenIn(seen.single())
        assertThat(configDir.listDirectoryEntries()).isEmpty()
        assertThat(sessions.find(token)).isNull()
        assertNoLeak(token)
    }

    @Test
    fun `AC-7 the token is valid for the develop session while the run lasts`() {
        card("c1")
        val live = CopyOnWriteArrayList<McpSession?>()
        val runtime = DevelopFixtures.ProbingRuntime(succeeding(rounds(1)), onLaunch = { request ->
            live.add(sessions.find(tokenIn(Files.readString(request.mcpConfig))))
        })

        pipeline(runtime, DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))).run("c1")

        assertThat(live).hasSize(rounds(1))
        assertThat(live).containsOnly(McpSession(project.repo, "develop", "c1"))
    }

    @Test
    fun `AC-8 a run that throws releases the card lock of the pool`() {
        assertThat(lockReleasedAfter(failure = IllegalStateException("boom"), script = emptyList())).isTrue()
    }

    @Test
    fun `AC-8 a run that times out releases the card lock of the pool`() {
        assertThat(lockReleasedAfter(failure = null, script = List(rounds(3)) { SessionScript.Hang })).isTrue()
    }

    @Test
    fun `AC-1 the develop launcher takes only the develop stage`() {
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val launcher = DevelopStageLauncher({ error("not reached") }, pool)

        assertThrows<IllegalArgumentException> { launcher.launch(DispatchStage.REFINE, "c1") }
    }

    @Test
    fun `AC-1 the dispatcher moves a ready card to Coding through the launcher and the run holds the lock until it ends`() {
        card("c1")
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val hold = CountDownLatch(1)
        val runtime = DevelopFixtures.ProbingRuntime(succeeding(rounds(1)))
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green), hold)
        val dispatcher = DispatcherFixtures.dispatcherFor(
            setOf(DispatchStage.DEVELOP), store, pool, DevelopStageLauncher({ pipeline(runtime, gates) }, pool),
            DispatcherFixtures.FakePause(), AlwaysWorkTime, clock, log,
        )
        try {
            assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "c1"))
            assertThat(runtime.started.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(column("c1")).isEqualTo(Column.CODING)
            assertThat(pool.isLocked("c1")).isTrue()
            hold.countDown()
            assertThat(DevelopFixtures.await { column("c1") == Column.VALIDATION && !pool.isLocked("c1") }).isTrue()
        } finally {
            hold.countDown()
            dispatcher.close()
        }
    }

    private fun lockReleasedAfter(failure: RuntimeException?, script: List<SessionScript>): Boolean {
        card("c1")
        val pool = RunPool(2, store, clock, ProcessProbe { _, _ -> true }, log, recorder = recorder)
        val hold = CountDownLatch(1)
        val runtime = DevelopFixtures.ProbingRuntime(FakeRuntime(script), hold = hold, failure = failure)
        val gates = DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green))
        val dispatcher = DispatcherFixtures.dispatcherFor(
            setOf(DispatchStage.DEVELOP), store, pool, DevelopStageLauncher({ pipeline(runtime, gates) }, pool),
            DispatcherFixtures.FakePause(), AlwaysWorkTime, clock, log,
        )
        try {
            assertThat(dispatcher.runCycle()).containsExactly(DispatchedRun(DispatchStage.DEVELOP, "c1"))
            assertThat(runtime.started.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(pool.isLocked("c1")).isTrue()
            hold.countDown()
            return DevelopFixtures.await { !pool.isLocked("c1") }
        } finally {
            hold.countDown()
            dispatcher.close()
        }
    }

    /** Adds "red" and "green:<layer>" to the same list the runtime appends "session" to. */
    private class RecordingGates(private val inner: DevelopGates, private val calls: MutableList<String>) : DevelopGates {
        override fun layers(worktree: Path): List<ProjectLayer> = inner.layers(worktree)

        override fun red(cardId: String, worktree: Path): List<GateResult> = inner.red(cardId, worktree).also { calls.add("red") }

        override fun green(cardId: String, layer: ProjectLayer, worktree: Path): List<GateResult> =
            inner.green(cardId, layer, worktree).also { calls.add("green:${layer.name}") }
    }

    private fun greenDelivery(): PullRequestDelivery {
        val forge = FakeForge()
        forge.scriptChecks("sha-1", ChecksState.Passed)
        return PullRequestDelivery(forge, { _, _ -> PushResult.Pushed }, WorktreeHead { "sha-1" }, CiPause { })
    }

    private fun defaultRoundsOfEachPipeline(): Map<String, Any?> {
        val costRecorder = RunCostRecorder(store, recorder, RunCostFixtures.prices(), clock, log)
        val refine = RefinePipeline(
            project, store, recorder, worktrees, sessions, 4317, succeeding(0), costRecorder,
            RefineGates { _, _ -> emptyList() }, configDir, clock, "model-a",
        )
        val develop = DevelopPipeline(
            project, store, recorder, worktrees, sessions, 4317, succeeding(0), costRecorder,
            DevelopFixtures.ScriptedDevelopGates(listOf(DevelopFixtures.green)), stepUp, configDir, clock,
        )
        val validate = ValidatePipeline(
            project, store, recorder, worktrees, sessions, 4317, succeeding(0), costRecorder,
            BranchSync { MergeResult.Merged }, ValidateGates { _, _ -> ValidateGateResults(emptyList()) }, configDir, clock, "model-a",
            delivery = greenDelivery(),
        )
        return listOf("refine" to refine, "develop" to develop, "validate" to validate).associate { (name, pipeline) ->
            name to pipeline.javaClass.getDeclaredField("maxRounds").apply { isAccessible = true }.get(pipeline)
        }
    }

    @Test
    fun `AC-4 refine, develop and validate default to the same 3 rounds from one definition`() {
        assertThat(DEFAULT_MAX_ROUNDS).isEqualTo(3)
        assertThat(defaultRoundsOfEachPipeline()).containsExactlyInAnyOrderEntriesOf(
            mapOf("refine" to 3, "develop" to 3, "validate" to 3),
        )
    }

    @Test
    fun `AC-5 every pipeline default follows DEFAULT_MAX_ROUNDS`() {
        assertThat(defaultRoundsOfEachPipeline().values).containsOnly(DEFAULT_MAX_ROUNDS)
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
