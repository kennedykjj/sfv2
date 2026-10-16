package sfv2.daemon

import java.time.Clock
import sfv2.store.ProjectPathStore

// shipfloor:qa-stub — lets the acceptance tests compile before the feature exists; the implementer deletes this file.
// Once ProjectRegistry has the real constructor and restore(), the members win over these functions.
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun ProjectRegistry(
    source: ProjectConfigSource,
    paths: ProjectPathStore?,
    log: DaemonLog,
    clock: Clock,
): ProjectRegistry = ProjectRegistry(source)

@Suppress("UnusedReceiverParameter")
fun ProjectRegistry.restore() {
    // Nothing is restored on purpose: the real restore() reads the persisted paths.
}
