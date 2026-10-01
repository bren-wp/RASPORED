package hr.raspored.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class EvidenceMonthSummary(
    val plannedMinutes: Long,
    val workedMinutes: Long,
    val dayMinutes: Long,
    val nightMinutes: Long,
    val otherMinutes: Long,
    val saturdayMinutes: Long,
    val sundayMinutes: Long,
    val holidayMinutes: Long,
    val weekendHolidayMinutes: Long,
    val weekMinutes: List<Long>
) {
    val balanceMinutes: Long get() = workedMinutes - plannedMinutes
}

/**
 * Calendar-derived work evidence.
 *
 * RASPORED no longer requires clock-in/clock-out tracking. The calendar is the
 * source of truth: D and N represent 12-hour shifts. Unknown/custom codes are
 * preserved by the schedule store but do not receive invented durations.
 */
object EvidenceAnalytics {
    const val DAY_SHIFT_MINUTES = 12L * 60L
    const val NIGHT_SHIFT_MINUTES = 12L * 60L
    const val NIGHT_WINDOW_MINUTES = 8L * 60L

    fun summarize(
        month: YearMonth,
        scheduleCodes: Map<String, String>
    ): EvidenceMonthSummary {
        val holidays = CroatianHolidays.forYear(month.year)
        var worked = 0L
        var dayMinutes = 0L
        var nightMinutes = 0L
        var saturdayMinutes = 0L
        var sundayMinutes = 0L
        var holidayMinutes = 0L
        var weekendHolidayMinutes = 0L
        val weeks = MutableList(5) { 0L }

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val code = scheduleCodes[date.toString()].orEmpty()
            val shiftMinutes = minutesForCode(code)
            if (shiftMinutes == 0L) continue

            worked += shiftMinutes
            if (code == "D") {
                dayMinutes += shiftMinutes
            } else if (code == "N") {
                nightMinutes += NIGHT_WINDOW_MINUTES
                dayMinutes += shiftMinutes - NIGHT_WINDOW_MINUTES
            }

            when (date.dayOfWeek) {
                DayOfWeek.SATURDAY -> saturdayMinutes += shiftMinutes
                DayOfWeek.SUNDAY -> sundayMinutes += shiftMinutes
                else -> Unit
            }
            val holiday = holidays.containsKey(date)
            if (holiday) holidayMinutes += shiftMinutes
            if (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) || holiday) {
                weekendHolidayMinutes += shiftMinutes
            }
            weeks[minOf(4, (day - 1) / 7)] += shiftMinutes
        }

        return EvidenceMonthSummary(
            plannedMinutes = worked,
            workedMinutes = worked,
            dayMinutes = dayMinutes,
            nightMinutes = nightMinutes,
            otherMinutes = 0L,
            saturdayMinutes = saturdayMinutes,
            sundayMinutes = sundayMinutes,
            holidayMinutes = holidayMinutes,
            weekendHolidayMinutes = weekendHolidayMinutes,
            weekMinutes = weeks
        )
    }

    fun minutesForCode(code: String?): Long = when (ScheduleStore.normalizeCode(code)) {
        "D" -> DAY_SHIFT_MINUTES
        "N" -> NIGHT_SHIFT_MINUTES
        else -> 0L
    }

    fun hoursLabel(code: String?): String = when (ScheduleStore.normalizeCode(code)) {
        "D", "N" -> "12 h"
        "GO", "BO", "PD", "SD" -> "—"
        null -> "—"
        else -> "Nije definirano"
    }

    fun shiftLabel(code: String?): String = when (val normalized = ScheduleStore.normalizeCode(code)) {
        "D" -> "Dnevna smjena"
        "N" -> "Noćna smjena"
        "GO" -> "Godišnji odmor"
        "BO" -> "Bolovanje"
        "PD" -> "Plaćeni dopust"
        "SD" -> "Slobodan dan"
        null -> "Bez smjene"
        else -> "Vlastita oznaka $normalized"
    }

    fun isWeekendOrHoliday(date: LocalDate): Boolean =
        date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) ||
            CroatianHolidays.forYear(date.year).containsKey(date)
}
