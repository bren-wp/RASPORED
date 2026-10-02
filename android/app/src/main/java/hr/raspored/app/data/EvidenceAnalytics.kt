package hr.raspored.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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
    val compensatedAbsenceMinutes: Long,
    val holidayCompensatedMinutes: Long,
    val accountedMinutes: Long,
    val overtimeMinutes: Long,
    val weekMinutes: List<Long>
) {
    val balanceMinutes: Long get() = accountedMinutes - plannedMinutes
}

/**
 * Calendar-derived monthly evidence.
 *
 * D and N are explicit 12-hour work shifts. GO, BO and PD contribute to the
 * monthly obligation only on regular Monday-Friday fund days and never become
 * worked hours. A statutory holiday on a Monday-Friday fund day contributes
 * eight compensated hours when no work/absence code is already present.
 *
 * "Overtime" here is the calendar-derived excess above the monthly fund. The
 * employer's official overtime order/evidence remains authoritative.
 */
object EvidenceAnalytics {
    const val DAY_SHIFT_MINUTES = 12L * 60L
    const val NIGHT_SHIFT_MINUTES = 12L * 60L
    const val NIGHT_WINDOW_MINUTES = 8L * 60L
    const val FUND_DAY_MINUTES = 8L * 60L

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
        var compensatedAbsenceMinutes = 0L
        var holidayCompensatedMinutes = 0L
        val weeks = MutableList(5) { 0L }
        val workedDates = mutableSetOf<LocalDate>()

        fun accountShift(date: LocalDate, code: String) {
            val start: LocalDateTime = when (code) {
                "D" -> date.atTime(7, 0)
                "N" -> date.atTime(19, 0)
                else -> return
            }
            val end: LocalDateTime = when (code) {
                "D" -> date.atTime(19, 0)
                else -> date.plusDays(1).atTime(7, 0)
            }
            var cursor = start
            while (cursor.isBefore(end)) {
                val currentDate = cursor.toLocalDate()
                if (YearMonth.from(currentDate) == month) {
                    worked++
                    workedDates += currentDate
                    val hour = cursor.hour
                    if (hour >= 22 || hour < 6) nightMinutes++ else dayMinutes++
                    when (currentDate.dayOfWeek) {
                        DayOfWeek.SATURDAY -> saturdayMinutes++
                        DayOfWeek.SUNDAY -> sundayMinutes++
                        else -> Unit
                    }
                    val holiday = holidays.containsKey(currentDate)
                    if (holiday) holidayMinutes++
                    if (
                        currentDate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) ||
                        holiday
                    ) {
                        weekendHolidayMinutes++
                    }
                    val weekIndex = minOf(4, (currentDate.dayOfMonth - 1) / 7)
                    weeks[weekIndex]++
                }
                cursor = cursor.plusMinutes(1)
            }
        }

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            when (val code = ScheduleStore.normalizeCode(scheduleCodes[date.toString()])) {
                "D", "N" -> accountShift(date, code)
                else -> Unit
            }
        }
        val previousDate = month.atDay(1).minusDays(1)
        if (ScheduleStore.normalizeCode(scheduleCodes[previousDate.toString()]) == "N") {
            accountShift(previousDate, "N")
        }

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val fundDay = date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            if (!fundDay) continue
            val code = ScheduleStore.normalizeCode(scheduleCodes[date.toString()])
            if (code in setOf("GO", "BO", "PD")) {
                compensatedAbsenceMinutes += FUND_DAY_MINUTES
            } else if (holidays.containsKey(date) && date !in workedDates) {
                holidayCompensatedMinutes += FUND_DAY_MINUTES
            }
        }

        val plannedMinutes = (1..month.lengthOfMonth())
            .count { day ->
                month.atDay(day).dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            }
            .toLong() * FUND_DAY_MINUTES

        val accountedMinutes =
            worked + compensatedAbsenceMinutes + holidayCompensatedMinutes
        val overtimeMinutes = (accountedMinutes - plannedMinutes).coerceAtLeast(0L)

        return EvidenceMonthSummary(
            plannedMinutes = plannedMinutes,
            workedMinutes = worked,
            dayMinutes = dayMinutes,
            nightMinutes = nightMinutes,
            otherMinutes = 0L,
            saturdayMinutes = saturdayMinutes,
            sundayMinutes = sundayMinutes,
            holidayMinutes = holidayMinutes,
            weekendHolidayMinutes = weekendHolidayMinutes,
            compensatedAbsenceMinutes = compensatedAbsenceMinutes,
            holidayCompensatedMinutes = holidayCompensatedMinutes,
            accountedMinutes = accountedMinutes,
            overtimeMinutes = overtimeMinutes,
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
