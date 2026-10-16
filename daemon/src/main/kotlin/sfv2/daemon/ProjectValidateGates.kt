package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.store.CardEventStore

/**
 * Build and test of every layer the card touched, scope and spec gates, over the worktree after the sync with main.
 * Each result is recorded as a GATE_FINISHED event.
 */
class ProjectValidateGates(
    private val project: Project,
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val executor: GateExecutor,
    private val changedPaths: ChangedPaths,
    private val clock: Clock,
    private val runDir: Path,
    private val baseRevision: String = "origin/main",
) : ValidateGates {

    override fun evaluate(cardId: String, worktree: Path): List<GateResult> {
        val changed = (changedPaths.between(worktree, baseRevision) + changedPaths.untracked(worktree)).distinct()
        val touched = project.layers.filter { layer -> changed.any { it.startsWith(layer.path) } }
        val dir = runDir.resolve(cardId)
        val results = touched.flatMap { layer -> listOf("build", "test").map { task -> layerGate(cardId, worktree, dir, layer, task) } } +
            scopeGate(cardId, changed, touched.map { it.name }.toSet()) +
            specGates(cardId, worktree, changed)
        return results
    }

    private fun layerGate(cardId: String, worktree: Path, dir: Path, layer: ProjectLayer, task: String): GateResult {
        val name = "$task-${layer.name}"
        val spec = GateSpec(name, listOf("./gradlew", ":${layer.name}:$task", "--console=plain"), worktree, TIMEOUT)
        return when (val verdict = executor.execute(cardId, spec, dir)) {
            GateVerdict.Pass -> GateResult(name, true, null)
            is GateVerdict.Fail -> GateResult(name, false, verdict.reason)
        }
    }

    private fun scopeGate(cardId: String, changed: List<String>, layers: Set<String>): GateResult {
        val result = when (val verdict = ScopeGate.check(project, changed, layers)) {
            ScopeVerdict.Pass -> GateResult("scope", true, null)
            is ScopeVerdict.Violations ->
                GateResult("scope", false, "forbidden=${verdict.forbidden.size} outside=${verdict.outsideLayers.size}")
        }
        return result.also { record(cardId, it) }
    }

    private fun specGates(cardId: String, worktree: Path, changed: List<String>): List<GateResult> {
        val specPath = changed.filter { it.startsWith(SPEC_DIR) && it.endsWith("-design.md") }
            .filter { Files.exists(worktree.resolve(it)) }
            .maxOrNull()
        val results = if (specPath == null) {
            listOf(GateResult("test-plan", false, "no spec file under $SPEC_DIR"))
        } else {
            val layers = project.layers.map { SpecLayer(it.name, it.path, listOf("${it.path.trimEnd('/')}/**")) }
            val check = SpecGates.check(Files.readString(worktree.resolve(specPath)), layers)
            SpecGate.entries.map { gate ->
                val findings = check.findings.filter { it.gate == gate }
                GateResult(
                    gate.name.lowercase().replace('_', '-'),
                    findings.isEmpty(),
                    findings.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.message },
                )
            }
        }
        results.forEach { record(cardId, it) }
        return results
    }

    private fun record(cardId: String, result: GateResult) {
        val card = store.card(cardId) ?: throw GateCardNotFoundException(cardId)
        val payload = buildMap {
            put("gate", result.name)
            put("verdict", if (result.passed) "PASS" else "FAIL")
            result.reason?.let { put("summary", it) }
        }
        val event = Event(EventId(UUID.randomUUID().toString()), clock.instant(), Actor.Factory("gate"), EventType.GATE_FINISHED, payload)
        recorder.record(cardId, card, event)
    }

    private companion object {
        const val SPEC_DIR = "docs/superpowers/specs/"
        val TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}
