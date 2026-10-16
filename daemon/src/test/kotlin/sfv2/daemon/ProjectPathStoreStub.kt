package sfv2.store

import java.time.Instant

// shipfloor:qa-stub — lets the acceptance tests compile before the feature exists; the implementer deletes this file.
class ProjectPathStore(@Suppress("UNUSED_PARAMETER") database: Database) {
    fun add(path: String, at: Instant) {
        // Not persisted on purpose: the real store writes the project table.
    }

    fun all(): List<String> = emptyList()
}
