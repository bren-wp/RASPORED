package hr.raspored.app.data

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicHealthPayrollTest {
    @Test
    fun usesPublished2026BasePeriodsAndRoleCoefficients() {
        assertEquals(1004.87, PublicHealthPayroll.baseFor(YearMonth.of(2026, 1)), 0.001)
        assertEquals(1015.00, PublicHealthPayroll.baseFor(YearMonth.of(2026, 4)), 0.001)
        assertEquals(1025.00, PublicHealthPayroll.baseFor(YearMonth.of(2026, 9)), 0.001)
        assertEquals(1035.00, PublicHealthPayroll.baseFor(YearMonth.of(2026, 12)), 0.001)
        assertEquals(1.39, PublicHealthPayroll.role("kbc-portir").coefficient, 0.001)
        assertEquals(1.15, PublicHealthPayroll.role("kbc-transport-nss").coefficient, 0.001)
    }

    @Test
    fun salaryAdditionsUseActualEvidenceAcrossNightAndSaturday() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 3, 22, 0).atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 10, 3, 23, 0).atZone(zone).toInstant().toEpochMilli()
        val entry = TimeEvidenceEntry(1L, started, ended, "")
        val estimate = PublicHealthPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(entry),
            coefficient = 1.39,
            yearsService = 10,
            extraPercent = 0.0,
            secondShift = false,
            zone = zone
        )

        assertEquals(60L, estimate.evidence.workedMinutes)
        assertEquals(60L, estimate.evidence.nightMinutes)
        assertEquals(60L, estimate.evidence.saturdayMinutes)
        assertTrue(estimate.nightAddition > 0.0)
        assertTrue(estimate.saturdayAddition > 0.0)
        assertEquals(0.0, estimate.sundayAddition, 0.001)
    }

    @Test
    fun activeEvidenceDoesNotCreateNegativeDuration() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 16, 7, 0).atZone(zone).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 10, 16, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val estimate = PublicHealthPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(TimeEvidenceEntry(1L, started, null, "")),
            coefficient = 1.15,
            yearsService = 0,
            extraPercent = 0.0,
            secondShift = false,
            now = now,
            zone = zone
        )

        assertEquals(150L, estimate.evidence.workedMinutes)
        assertTrue(estimate.evidence.hasActiveEntry)
    }
}
