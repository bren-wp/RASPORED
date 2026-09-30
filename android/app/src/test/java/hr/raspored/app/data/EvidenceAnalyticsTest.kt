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
