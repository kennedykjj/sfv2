package sfv2.app

// shipfloor:qa-stub — test-only stub so the red gate compiles; the implementer deletes it.

interface AssistantSkin {
    val id: String

    fun show(history: List<String>)
}

data class TerminalWindow(val lines: List<String>)

class UnknownSkinException(id: String) : RuntimeException("unknown skin $id")

class TerminalSkin(maxLines: Int = 5, maxColumns: Int = 40) : AssistantSkin {
    override val id: String = "terminal"
    var current: TerminalWindow = TerminalWindow(emptyList())

    fun window(history: List<String>): TerminalWindow = TerminalWindow(emptyList())

    override fun show(history: List<String>) = Unit
}

class SkinRegistry(private val skins: List<AssistantSkin>, initial: String) {
    val active: AssistantSkin = skins.first { it.id == initial }
    val history: List<String> = emptyList()

    fun activate(id: String) = Unit

    fun publish(phrase: String) = Unit
}
