package sfv2.daemon

import java.nio.file.Path
import sfv2.ports.Runtime

// Red-gate stub: signatures only, no logic. The engineer replaces this with the real service and empties this file.
@Suppress("unused", "UNUSED_PARAMETER", "LongParameterList")
class ProjectDocsDraft(
    registry: ProjectRegistry,
    files: ProjectFileSource,
    sessions: McpSessions,
    runtime: Runtime,
    ownerProof: OwnerProof,
    scratchRoot: Path,
    mcpPort: Int,
    configDir: Path,
    model: String,
    log: DaemonLog,
) {
    fun handle(request: Request): Response = Response.Error("not implemented")
}
