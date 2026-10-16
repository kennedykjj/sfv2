package sfv2.adapters

import java.nio.file.Path
import java.time.Duration
import sfv2.ports.CloseResult
import sfv2.ports.Tracker
import sfv2.ports.TrackerItem

// shipfloor:qa-stub — only so the red gate compiles; the implementer deletes it and adds the real AcliTracker in main.
class AcliTracker(
    private val site: String,
    private val acli: String = "acli",
    private val workDir: Path,
    private val tmpDir: Path,
    private val timeout: Duration,
    private val runner: ProcessRunner = ProcessRunnerImpl(),
) : Tracker {
    override fun fetch(id: String): TrackerItem? = null

    override fun search(query: String): List<TrackerItem> = emptyList()

    override fun writeMilestone(id: String, milestone: String) = Unit

    override fun close(id: String, comment: String): CloseResult = CloseResult.AlreadyClosed
}
