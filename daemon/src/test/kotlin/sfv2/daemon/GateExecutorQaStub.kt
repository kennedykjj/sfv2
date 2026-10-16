package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import sfv2.store.CardEventStore

// shipfloor:qa-stub - lets GateExecutorTest compile for the red gate; the implementer deletes this file.

data class GateSpec(val name: String, val command: List<String>, val workingDir: Path, val timeout: Duration)

data class GateRun(val exitCode: Int?, val timedOut: Boolean)

sealed interface GateVerdict {
    data object Pass : GateVerdict

    data class Fail(val reason: String) : GateVerdict
}

interface GateRunner {
    fun run(spec: GateSpec, outputFile: Path): GateRun
}

class GateExecutor(store: CardEventStore, events: EventStream, clock: Clock, runner: GateRunner) {
    fun execute(cardId: String, spec: GateSpec, runDir: Path): GateVerdict = GateVerdict.Pass
}
