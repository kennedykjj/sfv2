package sfv2.core

import java.time.Instant

@JvmInline
value class EventId(val value: String)

/** Registro imutável de um efeito da fábrica. O instante entra por parâmetro. */
data class Event(
    val id: EventId,
    val at: Instant,
    val actor: Actor,
    val type: EventType,
    val payload: Map<String, String>,
)

enum class EventType {
    CARD_CREATED,
    CARD_MOVED,
    OWNER_APPROVED,
}

/** Lançada na leitura de um evento cujo tipo não existe no catálogo. */
class UnknownEventTypeException(type: String) : RuntimeException("unknown event type: $type")
