package hr.raspored.app.data

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicHealthPayrollTest {
    @Test
    fun usesPublished2026BasePeriodsAndNationalRoleCoefficients() {
        assertEquals(1004.87, PublicHealthPayroll.baseFor(YearMonth.of(2026, 1)), 0.001)
        assertEquals(1015.00, PublicHealthPayroll.baseFor(YearMonth.of(2026, 4)), 0.001)
        assertEquals(1025.00, PublicHealthPayroll.baseFor(YearMonth.of(2026, 9)), 0.001)
        assertEquals(1035.00, PublicHealthPayroll.baseFor(YearMonth.of(2026, 12)), 0.001)
        assertEquals(1.39, PublicHealthPayroll.role("kbc-portir").coefficient, 0.001)
        assertEquals(1.25, PublicHealthPayroll.role("kbc-transport-sss").coefficient, 0.001)
        assertEquals(1.15, PublicHealthPayroll.role("kbc-transport-nss").coefficient, 0.001)
        assertEquals(2.20, PublicHealthPayroll.role("nurse-emergency-specialist").coefficient, 0.001)
        assertTrue(PublicHealthPayroll.institutions.size >= 60)
        assertEquals(
            "kbc-rijeka-observed-2026",
            PublicHealthPayroll.institution("kbc-rijeka").defaultRateProfileId
        )
    }

    @Test
    fun hourlyPriceIncludesSeniorityAndOvertimeIncludesBaseHourPlusPremium() {
        val month = YearMonth.of(2026, 9)
        val estimate = PublicHealthPayroll.estimate(
            month = month,
            entries = emptyList(),
            coefficient = 1.25,
            yearsService = 10,
            rateProfileId = "national-public",
            overtimeHoursOverride = 10.0
        )

        val expectedBasic = 1025.0 * 1.25 * 1.05
        val expectedHourly = expectedBasic / PublicHealthPayroll.monthlyFundHours(month)
        assertEquals(expectedBasic, estimate.basicGross, 0.001)
        assertEquals(expectedHourly, estimate.hourlyGross, 0.001)
        assertEquals(expectedHourly * 10.0, estimate.overtimeBase, 0.001)
        assertEquals(expectedHourly * 10.0 * 0.50, estimate.overtimeAddition, 0.001)
        assertEquals(
            expectedBasic + expectedHourly * 10.0 * 1.50,
            estimate.estimatedGross,
            0.001
        )
    }

    @Test
    fun kbcRijekaProfileKeepsObservedNightRateSeparateFromNationalRate() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 3, 22, 0).atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 10, 3, 23, 0).atZone(zone).toInstant().toEpochMilli()
        val entry = TimeEvidenceEntry(1L, started, ended, "")

        val national = PublicHealthPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(entry),
            coefficient = 1.39,
            yearsService = 0,
            rateProfileId = "national-public",
            zone = zone
        )
        val rijeka = PublicHealthPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(entry),
            coefficient = 1.39,
            yearsService = 0,
            rateProfileId = "kbc-rijeka-observed-2026",
            zone = zone
        )

        assertEquals(60L, national.evidence.nightMinutes)
        assertEquals(60L, national.evidence.saturdayMinutes)
        assertEquals(national.hourlyGross * 0.40, national.nightAddition, 0.001)
        assertEquals(rijeka.hourlyGross * 0.50, rijeka.nightAddition, 0.001)
        assertTrue(rijeka.nightAddition > national.nightAddition)
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
            rateProfileId = "national-public",
            now = now,
            zone = zone
        )

        assertEquals(150L, estimate.evidence.workedMinutes)
        assertTrue(estimate.evidence.hasActiveEntry)
    }
}
