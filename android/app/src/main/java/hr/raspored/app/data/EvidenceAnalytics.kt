package hr.raspored.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

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

object EvidenceAnalytics {
    private const val MAX_ENTRY_HOURS = 36L

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
        var weekendHolidayMinutes = 0L
        val weeks = MutableList(5) { 0L }

        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val completed = entries.filter { entry ->
            val rawEnd = entry.endedAt ?: return@filter false
            val safeEnd = minOf(rawEnd, entry.startedAt + MAX_ENTRY_HOURS * 60L * 60L * 1000L)
            entry.startedAt < monthEnd && safeEnd > monthStart
        }

        completed.forEach { entry ->
            val rawEnd = entry.endedAt ?: return@forEach
            val safeEnd = minOf(rawEnd, entry.startedAt + MAX_ENTRY_HOURS * 60L * 60L * 1000L)
            var cursor = maxOf(entry.startedAt, monthStart)
            val end = minOf(safeEnd, monthEnd)
            if (end <= cursor) return@forEach

            while (cursor < end) {
                val cursorZoned = Instant.ofEpochMilli(cursor).atZone(zone)
                val date = cursorZoned.toLocalDate()
                val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val segmentEnd = minOf(end, nextDay)
                val segmentMinutes = Duration.ofMillis(segmentEnd - cursor).toMinutes()
                if (segmentMinutes <= 0L) {
                    cursor = segmentEnd
                    continue
                }

                val nightForDay = nightMinutesForSegment(cursor, segmentEnd, date, zone)
                val dayForDay = (segmentMinutes - nightForDay).coerceAtLeast(0L)

                worked += segmentMinutes
                dayMinutes += dayForDay
                nightMinutes += nightForDay

                when (date.dayOfWeek.value) {
                    6 -> saturdayMinutes += segmentMinutes
                    7 -> sundayMinutes += segmentMinutes
                }
                val holiday = holidays.containsKey(date)
                if (holiday) holidayMinutes += segmentMinutes
                if (date.dayOfWeek.value >= 6 || holiday) {
                    weekendHolidayMinutes += segmentMinutes
                }

                val week = minOf(4, (date.dayOfMonth - 1) / 7)
                weeks[week] += segmentMinutes
                cursor = segmentEnd
            }
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
                val holiday = holidays.containsKey(date)
                if (holiday) holidayMinutes += minutes
                if (date.dayOfWeek.value >= 6 || holiday) {
                    weekendHolidayMinutes += minutes
                }
                weeks[minOf(4, (day - 1) / 7)] += minutes
            }
        }

        otherMinutes = (worked - dayMinutes - nightMinutes).coerceAtLeast(0L)

        return EvidenceMonthSummary(
            plannedMinutes = planned,
            workedMinutes = worked,
            dayMinutes = dayMinutes,
            nightMinutes = nightMinutes,
            otherMinutes = otherMinutes,
            saturdayMinutes = saturdayMinutes,
            sundayMinutes = sundayMinutes,
            holidayMinutes = holidayMinutes,
            weekendHolidayMinutes = weekendHolidayMinutes,
            weekMinutes = weeks
        )
    }

    private fun nightMinutesForSegment(
        segmentStart: Long,
        segmentEnd: Long,
        date: LocalDate,
        zone: ZoneId
    ): Long {
        val midnight = date.atStartOfDay(zone)
        val six = date.atTime(LocalTime.of(6, 0)).atZone(zone)
        val twentyTwo = date.atTime(LocalTime.of(22, 0)).atZone(zone)
        val nextMidnight = date.plusDays(1).atStartOfDay(zone)

        return overlapMinutes(segmentStart, segmentEnd, midnight, six) +
            overlapMinutes(segmentStart, segmentEnd, twentyTwo, nextMidnight)
    }

    private fun overlapMinutes(
        segmentStart: Long,
        segmentEnd: Long,
        windowStart: ZonedDateTime,
        windowEnd: ZonedDateTime
    ): Long {
        val start = maxOf(segmentStart, windowStart.toInstant().toEpochMilli())
        val end = minOf(segmentEnd, windowEnd.toInstant().toEpochMilli())
        return if (end > start) Duration.ofMillis(end - start).toMinutes() else 0L
    }
}
