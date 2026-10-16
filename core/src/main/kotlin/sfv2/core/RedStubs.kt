package sfv2.core

import java.time.Instant

// Temporary red-gate stubs: minimal signatures with neutral bodies, replaced by QueueForecast.kt.

sealed interface QueueForecast {
    data object Empty : QueueForecast

    data class FinishesAt(val at: Instant, val ownerSteps: Int) : QueueForecast

    data class Unknown(val gap: ForecastGap) : QueueForecast
}

enum class ForecastGap { NO_POOL, NO_HISTORY }

object QueueForecasts {
    fun of(columns: List<Column>, medians: Map<Column, ColumnTime>, poolCapacity: Int, now: Instant): QueueForecast =
        QueueForecast.Empty
}
