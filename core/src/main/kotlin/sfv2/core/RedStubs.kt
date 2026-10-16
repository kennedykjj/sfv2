package sfv2.core

import java.time.Instant

// Temporary red-step signatures; the engineer replaces them with the real ColumnTimes.kt.

enum class ColumnTimeKind { MEDIAN, MEAN_WAIT }

data class ColumnTime(val kind: ColumnTimeKind, val seconds: Long, val stays: Int)

object ColumnTimes {
    fun of(eventsByCard: List<List<Event>>, from: Instant, toExclusive: Instant): Map<Column, ColumnTime> = emptyMap()
}
