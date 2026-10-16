package sfv2.core

/** Quem agiu. Cada efeito da fábrica carrega um ator. */
sealed interface Actor {
    data object Owner : Actor

    data class Agent(val role: String) : Actor

    data class Adapter(val provider: String) : Actor

    data class Factory(val component: String) : Actor
}
