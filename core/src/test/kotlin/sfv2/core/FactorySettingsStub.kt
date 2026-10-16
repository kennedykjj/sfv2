package sfv2.core

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

// shipfloor:qa-stub - lets the red gate compile; the implementer deletes this file and writes core/src/main/kotlin/sfv2/core/FactorySettings.kt.

enum class NotificationType { DECISION_WAITING, RUN_FAILED, FACTORY_STOPPED }

data class WorkHoursSetting(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime, val zone: String)

data class CostSetting(val perCardUsd: BigDecimal?, val perDayUsd: BigDecimal?)

data class NotificationSetting(val enabled: Set<NotificationType>, val quietStart: LocalTime?, val quietEnd: LocalTime?)

data class FactorySettings(
    val poolSize: Int,
    val workHours: WorkHoursSetting?,
    val cost: CostSetting,
    val notifications: NotificationSetting,
)

data class SettingsIssue(val field: String, val reason: String)

object FactorySettingsRules {
    fun validate(settings: FactorySettings, costUnlimitedAllowed: Boolean = true): List<SettingsIssue> = emptyList()
}
