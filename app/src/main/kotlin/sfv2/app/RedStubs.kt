package sfv2.app

import java.time.Instant
import java.time.ZoneId
import sfv2.core.QueueForecast

// Temporary red-gate stubs: minimal signatures with neutral bodies, replaced by the real forecast cell model.

data class ForecastCell(val headline: String, val secondary: String?, val description: String)

object ForecastCells {
    fun of(forecast: QueueForecast?, now: Instant, zone: ZoneId): ForecastCell = ForecastCell("", null, "")
}
