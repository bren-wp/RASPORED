package hr.raspored.app.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class EvidenceMonthSummary(
    val plannedMinutes: Long,
    val workedMinutes: Long,
    val dayMinutes: Long,
    val nightMinutes: Long,
    val otherMinutes: Long,
    val saturdayMinutes: Long,
    val sundayMinutes: Long,
    val holidayMinutes: Long,
    val weekMinutes: List<Long>
) {
    val balanceMinutes: Long get() = workedMinutes - plannedMinutes
}

object EvidenceAnalytics {
    fun summarize(
        month: YearMonth,
        entries: List<TimeEvidenceEntry>,
        scheduleCodes: Map<String, String>,
        fallbackToPlanned: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): EvidenceMonthSummary {
        val holidays = CroatianHolidays.forYear(month.year)
        val planned = (1..month.lengthOfMonth()).sumOf { day ->
            when (scheduleCodes[month.atDay(day).toString()]) {
                "D", "N" -> 12L * 60L
                else -> 0L
            }
        }

        var worked = 0L
        var dayMinutes = 0L
        var nightMinutes = 0L
        var otherMinutes = 0L
        var saturdayMinutes = 0L
        var sundayMinutes = 0L
        var holidayMinutes = 0L
        val weeks = MutableList(5) { 0L }

        val completed = entries.filter { entry ->
            if (entry.endedAt == null) return@filter false
            val date = Instant.ofEpochMilli(entry.startedAt).atZone(zone).toLocalDate()
            YearMonth.from(date) == month
        }

        completed.forEach { entry ->
            val date = Instant.ofEpochMilli(entry.startedAt).atZone(zone).toLocalDate()
            val minutes = entry.durationMinutes(entry.endedAt ?: entry.startedAt)
            val code = scheduleCodes[date.toString()].orEmpty()
            worked += minutes
            when (code) {
                "D" -> dayMinutes += minutes
                "N" -> nightMinutes += minutes
                else -> otherMinutes += minutes
            }
            when (date.dayOfWeek.value) {
                6 -> saturdayMinutes += minutes
                7 -> sundayMinutes += minutes
            }
            if (holidays.containsKey(date)) holidayMinutes += minutes
            val week = minOf(4, (date.dayOfMonth - 1) / 7)
            weeks[week] += minutes
        }

        if (completed.isEmpty() && fallbackToPlanned) {
            worked = planned
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val code = scheduleCodes[date.toString()].orEmpty()
                if (code != "D" && code != "N") continue
                val minutes = 12L * 60L
                if (code == "D") dayMinutes += minutes else nightMinutes += minutes
                when (date.dayOfWeek.value) {
                    6 -> saturdayMinutes += minutes
                    7 -> sundayMinutes += minutes
                }
                if (holidays.containsKey(date)) holidayMinutes += minutes
                weeks[minOf(4, (day - 1) / 7)] += minutes
            }
        }

        return EvidenceMonthSummary(
            plannedMinutes = planned,
            workedMinutes = worked,
            dayMinutes = dayMinutes,
            nightMinutes = nightMinutes,
            otherMinutes = otherMinutes,
            saturdayMinutes = saturdayMinutes,
            sundayMinutes = sundayMinutes,
            holidayMinutes = holidayMinutes,
            weekMinutes = weeks
        )
    }
}
