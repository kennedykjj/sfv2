package sfv2.store

import java.nio.file.Path
import sfv2.core.TextHash

// shipfloor:qa-stub - lets the acceptance tests compile; the implementer deletes this file
// and adds the real SpecStore/SpecRead in main.
sealed interface SpecRead {
    data class Valid(val text: String, val hash: TextHash) : SpecRead
    data class Diverged(val path: Path, val expected: TextHash, val actual: TextHash?) : SpecRead
    data class NotRegistered(val cardId: String) : SpecRead
}

class SpecStore(private val database: Database, private val specsDir: Path) {
    fun write(cardId: String, text: String) = Unit

    fun read(cardId: String): SpecRead = SpecRead.Diverged(specsDir, TextHash("stub"), null)
}
