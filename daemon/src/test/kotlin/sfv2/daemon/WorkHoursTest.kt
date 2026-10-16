package sfv2.daemon

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WorkHoursTest {
    // 2026-10-16 is a Friday; 2026-10-17 a Saturday.
    private fun at(iso: String): Instant = Instant.parse(iso)

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    /** The raw `factory.yml` map with a `work_hours` section. */
    private fun raw(vararg entries: Pair<String, Any?>): Map<String, Any?> =
        mapOf("work_hours" to mapOf(*entries))

    @Test
    fun `AC-1 starts runs only inside the window in the configured zone and days`() {
        val schedule = WorkHours(LocalTime.of(9, 0), LocalTime.of(18, 0), weekdays, ZoneId.of("America/Sao_Paulo"))

        // 12:00Z = 09:00 in Sao Paulo (UTC-3): start is inclusive.
        assertThat(schedule.isWorkTime(at("2026-10-16T12:00:00Z"))).isTrue()
        assertThat(schedule.isWorkTime(at("2026-10-16T20:59:00Z"))).isTrue()
        // 21:00Z = 18:00 local: end is exclusive.
        assertThat(schedule.isWorkTime(at("2026-10-16T21:00:00Z"))).isFalse()
        assertThat(schedule.isWorkTime(at("2026-10-16T11:59:00Z"))).isFalse()
        // Saturday inside the hours is outside the days.
        assertThat(schedule.isWorkTime(at("2026-10-17T15:00:00Z"))).isFalse()
    }

    @Test
    fun `AC-1 reads the window from the factory yml map`() {
        val schedule = WorkHours.from(
            raw(
                "start" to "09:00",
                "end" to "18:00",
                "days" to listOf("mon", "tue", "wed", "thu", "fri"),
                "timezone" to "America/Sao_Paulo",
            ),
        )

        assertThat(schedule.isWorkTime(at("2026-10-16T15:00:00Z"))).isTrue()
        assertThat(schedule.isWorkTime(at("2026-10-16T22:00:00Z"))).isFalse()
        assertThat(schedule.isWorkTime(at("2026-10-17T15:00:00Z"))).isFalse()
    }

    @Test
    fun `AC-1 defaults to every day and UTC`() {
        val schedule = WorkHours.from(raw("start" to "09:00", "end" to "18:00"))

        assertThat(schedule.isWorkTime(at("2026-10-17T10:00:00Z"))).isTrue()
        assertThat(schedule.isWorkTime(at("2026-10-17T18:00:00Z"))).isFalse()
    }

    @Test
    fun `AC-2 a window crossing midnight counts both sides and belongs to the day it starts`() {
        val schedule = WorkHours(LocalTime.of(22, 0), LocalTime.of(6, 0), setOf(DayOfWeek.FRIDAY), ZoneId.of("UTC"))

        // Friday night, and Saturday early morning belong to Friday's window.
        assertThat(schedule.isWorkTime(at("2026-10-16T22:00:00Z"))).isTrue()
        assertThat(schedule.isWorkTime(at("2026-10-16T23:59:00Z"))).isTrue()
        assertThat(schedule.isWorkTime(at("2026-10-17T05:59:00Z"))).isTrue()
        assertThat(schedule.isWorkTime(at("2026-10-17T06:00:00Z"))).isFalse()
        assertThat(schedule.isWorkTime(at("2026-10-16T12:00:00Z"))).isFalse()
        // Friday early morning belongs to Thursday's window, which is not configured.
        assertThat(schedule.isWorkTime(at("2026-10-16T03:00:00Z"))).isFalse()
        // Saturday night starts a Saturday window, which is not configured.
        assertThat(schedule.isWorkTime(at("2026-10-17T23:00:00Z"))).isFalse()
    }

    @Test
    fun `AC-3 without work_hours every instant is work time`() {
        assertThat(WorkHours.from(null).isWorkTime(at("2026-10-17T03:00:00Z"))).isTrue()
        assertThat(WorkHours.from(mapOf("delivery_mode" to "autonomous")).isWorkTime(at("2026-10-17T03:00:00Z")))
            .isTrue()
    }

    @Test
    fun `AC-4 a malformed time names the field`() {
        val error = assertThrows<InvalidWorkHoursException> {
            WorkHours.from(raw("start" to "9am", "end" to "18:00"))
        }

        assertThat(error.message).contains("work_hours.start")
    }

    @Test
    fun `AC-4 an unknown day names the field`() {
        val error = assertThrows<InvalidWorkHoursException> {
            WorkHours.from(raw("start" to "09:00", "end" to "18:00", "days" to listOf("mon", "funday")))
        }

        assertThat(error.message).contains("work_hours.days")
    }

    @Test
    fun `AC-4 a nonexistent timezone names the field`() {
        val error = assertThrows<InvalidWorkHoursException> {
            WorkHours.from(raw("start" to "09:00", "end" to "18:00", "timezone" to "Mars/Olympus"))
        }

        assertThat(error.message).contains("work_hours.timezone")
    }

    @Test
    fun `AC-4 a window with equal start and end is invalid`() {
        assertThrows<InvalidWorkHoursException> {
            WorkHours.from(raw("start" to "09:00", "end" to "09:00"))
        }
    }
}
