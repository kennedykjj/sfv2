package sfv2.core

import java.time.Instant

@JvmInline
value class EventId(val value: String)

/** Immutable record of one effect in the factory. The instant comes in as a parameter. */
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
    CARD_BLOCKED,
    OWNER_APPROVED,
    GATE_FINISHED,
    LOCK_RELEASED,
    RUN_COST_RECORDED,
    AGENT_REPORTED,
    AGENT_WRITE_REQUESTED,
    PR_MERGED,
    MAIN_CI_RERUN,
}

/** Thrown when reading an event whose type is not in the catalog. */
class UnknownEventTypeException(type: String, eventId: String) :
    RuntimeException("unknown event type: $type (event $eventId)")

/** Thrown when an event's JSON is malformed, misses a field or has an unknown actor kind. Never quotes the input. */
class InvalidEventJsonException(message: String) : RuntimeException(message)
