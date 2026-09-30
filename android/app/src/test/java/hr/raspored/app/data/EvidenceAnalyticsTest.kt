package hr.raspored.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class EvidenceAnalyticsTest {
    private val zone = ZoneId.of("Europe/Zagreb")

    @Test
    fun usesCompletedEvidenceForWorkedTime() {
        val month = YearMonth.of(2026, 10)
        val start = LocalDateTime.of(2026, 10, 16, 7, 0).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 10, 16, 19, 0).atZone(zone).toInstant().toEpochMilli()
        val summary = EvidenceAnalytics.summarize(
            month = month,
            entries = listOf(TimeEvidenceEntry(start, start, end, "")),
            scheduleCodes = mapOf("2026-10-16" to "D", "2026-10-17" to "N"),
            fallbackToPlanned = false,
            zone = zone
        )

        assertEquals(24L * 60L, summary.plannedMinutes)
        assertEquals(12L * 60L, summary.workedMinutes)
        assertEquals(12L * 60L, summary.dayMinutes)
        assertEquals(0L, summary.nightMinutes)
        assertEquals(-12L * 60L, summary.balanceMinutes)
    }

    @Test
    fun overnightEvidenceIsSplitAcrossMonthBoundaryAndActualNightHours() {
        val start = LocalDateTime.of(2026, 9, 30, 22, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 10, 1, 7, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val entry = TimeEvidenceEntry(start, start, end, "")

        val september = EvidenceAnalytics.summarize(
            month = YearMonth.of(2026, 9),
            entries = listOf(entry),
            scheduleCodes = emptyMap(),
            fallbackToPlanned = false,
            zone = zone
        )
        val october = EvidenceAnalytics.summarize(
            month = YearMonth.of(2026, 10),
            entries = listOf(entry),
            scheduleCodes = emptyMap(),
            fallbackToPlanned = false,
            zone = zone
        )

        assertEquals(2L * 60L, september.workedMinutes)
        assertEquals(2L * 60L, september.nightMinutes)
        assertEquals(7L * 60L, october.workedMinutes)
        assertEquals(6L * 60L, october.nightMinutes)
        assertEquals(1L * 60L, october.dayMinutes)
    }

    @Test
    fun weekendHolidayUnionDoesNotDoubleCountSameMinutes() {
        val easter = CroatianHolidays.easterSunday(2026)
        val start = easter.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val end = easter.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()

        val summary = EvidenceAnalytics.summarize(
            month = YearMonth.from(easter),
            entries = listOf(TimeEvidenceEntry(start, start, end, "")),
            scheduleCodes = emptyMap(),
            fallbackToPlanned = false,
            zone = zone
        )

        assertEquals(8L * 60L, summary.workedMinutes)
        assertEquals(8L * 60L, summary.sundayMinutes)
        assertEquals(8L * 60L, summary.holidayMinutes)
        assertEquals(8L * 60L, summary.weekendHolidayMinutes)
    }

    @Test
    fun previewFallbackUsesPlannedScheduleOnlyWhenNoEvidenceExists() {
        val month = YearMonth.of(2026, 10)
        val summary = EvidenceAnalytics.summarize(
            month = month,
            entries = emptyList(),
            scheduleCodes = mapOf("2026-10-16" to "D", "2026-10-17" to "N"),
            fallbackToPlanned = true,
            zone = zone
        )

        assertEquals(24L * 60L, summary.workedMinutes)
        assertEquals(12L * 60L, summary.dayMinutes)
        assertEquals(12L * 60L, summary.nightMinutes)
        assertEquals(0L, summary.balanceMinutes)
    }
}
