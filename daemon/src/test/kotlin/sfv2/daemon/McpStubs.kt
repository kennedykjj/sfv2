package sfv2.daemon

import java.nio.file.Path
import sfv2.core.Column
import sfv2.core.ProjectPolicies
import sfv2.store.CardEventStore
import sfv2.store.SpecStore

// shipfloor:qa-stub — lets McpReadToolsTest compile before the MCP read tools exist; delete this file when the
// real classes are added in daemon/src/main (same names and signatures).

fun CardEventStore.assignProject(cardId: String, repo: String) {
    // intentionally does nothing
}

data class UntrustedText(val source: String, val text: String) {
    val trust: String = "untrusted"
}

interface ProjectFileSource {
    fun read(repo: Path, path: String): String?
}

data class McpSession(val repo: String)

class McpSessions {
    fun open(repo: String): String = ""
}

class McpRequestGuard(private val sessions: McpSessions, private val port: Int) {
    fun authorize(host: String?, origin: String?, authorization: String?): McpSession? = McpSession("")
}

data class ReferenceView(val provider: String, val externalId: String, val url: String)

data class CardView(val id: String, val title: UntrustedText, val column: Column, val references: List<ReferenceView>)

data class SpecView(val cardId: String, val spec: UntrustedText)

data class CriteriaView(val criteria: UntrustedText)

class McpReadTools(
    private val session: McpSession,
    private val store: CardEventStore,
    private val specs: SpecStore,
    private val registry: ProjectRegistry,
    private val files: ProjectFileSource,
) {
    fun cardGet(cardId: String): CardView = CardView("", UntrustedText("", ""), Column.INBOX, emptyList())

    fun specGet(cardId: String): SpecView = SpecView("", UntrustedText("", ""))

    fun reviewCriteriaGet(): CriteriaView = CriteriaView(UntrustedText("", ""))

    fun policiesGet(): ProjectPolicies = ProjectPolicies.DEFAULT

    fun layersGet(): List<ProjectLayer> = emptyList()
}
