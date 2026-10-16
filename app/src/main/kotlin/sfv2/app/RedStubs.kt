package sfv2.app

import sfv2.core.Column
import sfv2.core.ColumnTime

// Temporary red-gate stubs: signatures only, neutral bodies.
data class TimeSegment(val column: Column, val seconds: Long)

data class TimeSplit(val segments: List<TimeSegment>, val waitPercent: Int) {
    companion object {
        fun of(times: Map<Column, ColumnTime>): TimeSplit? = null
    }
}
