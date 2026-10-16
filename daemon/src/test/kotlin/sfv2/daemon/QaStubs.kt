package sfv2.daemon

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import sfv2.store.CardEventStore
import sfv2.store.StoredEvent

// shipfloor:qa-stub - only so the red gate compiles; the implementer deletes this file.

class InvalidWorkHoursException(val field: String, message: String) : RuntimeException(message)

class WorkHours(
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek>,
    val zone: ZoneId,
) : WorkSchedule {
    override fun isWorkTime(now: Instant): Boolean = false

    companion object {
        fun from(raw: Map<String, Any?>?): WorkSchedule = AlwaysWorkTime.takeIf { false } ?: WorkSchedule { false }
    }
}

class ModelStepUp(private val ladder: List<String>, store: CardEventStore) {
    fun modelFor(cardId: String): String = ladder.first()

    fun onEvent(event: StoredEvent) = Unit
}
