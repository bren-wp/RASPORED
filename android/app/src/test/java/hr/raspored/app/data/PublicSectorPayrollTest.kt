package hr.raspored.app.data

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSectorPayrollTest {

    @Test
    fun usesPublished2026BasePeriodsForPublicAndStateServices() {
        assertEquals(1004.87, PublicSectorPayroll.baseFor(YearMonth.of(2026, 1), "public"), 0.001)
        assertEquals(1015.00, PublicSectorPayroll.baseFor(YearMonth.of(2026, 4), "public"), 0.001)
        assertEquals(1025.00, PublicSectorPayroll.baseFor(YearMonth.of(2026, 9), "state"), 0.001)
        assertEquals(1035.00, PublicSectorPayroll.baseFor(YearMonth.of(2026, 12), "state"), 0.001)
        assertEquals(0.0, PublicSectorPayroll.baseFor(YearMonth.of(2026, 9), "manual"), 0.001)
        assertEquals(0.0, PublicSectorPayroll.baseFor(YearMonth.of(2027, 1), "public"), 0.001)
    }

    @Test
    fun verifiedKbcRijekaPresetUses2026NightAndTurnusRates() {
        val regime = PublicSectorPayroll.regime("kbc-rijeka-2026")
        assertEquals(0.50, regime.rates.night ?: -1.0, 0.001)
        assertEquals(0.05, regime.rates.turnus ?: -1.0, 0.001)
        assertEquals(0.25, regime.rates.saturday ?: -1.0, 0.001)
        assertEquals(0.50, regime.rates.sunday ?: -1.0, 0.001)
        assertEquals(1.50, regime.rates.holiday ?: -1.0, 0.001)
        assertEquals(0.10, regime.rates.secondShift ?: -1.0, 0.001)
        assertEquals(0.50, regime.rates.overtime ?: -1.0, 0.001)
    }

    @Test
    fun genericPublicServiceKeepsTkuNightRateSeparateFromKbcPreset() {
        val regime = PublicSectorPayroll.regime("public-health")
        assertEquals(0.40, regime.rates.night ?: -1.0, 0.001)
        assertEquals(null, regime.rates.turnus)
    }

    @Test
    fun androidCatalogKeepsHospitalListAndCrossSectorFallbacksAlignedWithWeb() {
        assertEquals(
            61,
            PublicSectorPayroll.institutions.count { it.sector == "Zdravstvo" }
        )
        assertEquals(75, PublicSectorPayroll.institutions.size)
        assertTrue(
            PublicSectorPayroll.institutions.any {
                it.name == "Klinički bolnički centar Rijeka" &&
                    it.regimeId == "kbc-rijeka-2026"
            }
        )
        assertTrue(
            PublicSectorPayroll.institutionsFor("Policija", "Primorsko-goranska")
                .any { it.name == "MUP / policijska uprava ili postaja" }
        )
        assertTrue(
            PublicSectorPayroll.institutionsFor("Vrtići", "Grad Zagreb")
                .any { it.name.contains("vrtić", ignoreCase = true) }
        )
    }

    @Test
    fun catalogContainsVerifiedEducationPoliceAndFirefighterExamples() {
        assertEquals(2.01, PublicSectorPayroll.role("edu-teacher", "public-education").coefficient ?: -1.0, 0.001)
        assertEquals(1.70, PublicSectorPayroll.role("police-station", "police").coefficient ?: -1.0, 0.001)
        assertEquals(1.10, PublicSectorPayroll.role("firefighter", "firefighter").coefficient ?: -1.0, 0.001)
        assertEquals(2.10, PublicSectorPayroll.role("state-senior-adviser", "state-service").coefficient ?: -1.0, 0.001)
        assertEquals(1.06, PublicSectorPayroll.role("state-cleaner", "state-service").coefficient ?: -1.0, 0.001)
        assertEquals(0.50, PublicSectorPayroll.regime("police").rates.night ?: -1.0, 0.001)
        assertEquals(null, PublicSectorPayroll.regime("firefighter").rates.night)
    }

    @Test
    fun salaryUsesActualEvidenceAcrossNightAndSaturday() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 3, 22, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 10, 3, 23, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(TimeEvidenceEntry(1L, started, ended, "")),
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 12,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false,
            zone = zone
        )

        assertEquals(60L, estimate.evidence.workedMinutes)
        assertEquals(60L, estimate.evidence.nightMinutes)
        assertEquals(60L, estimate.evidence.saturdayMinutes)
        assertEquals(1, estimate.evidence.workedDays)
        assertTrue(estimate.nightAddition > 0.0)
        assertTrue(estimate.saturdayAddition > 0.0)
        assertTrue(estimate.estimatedGross > estimate.basicGross)
        assertTrue(estimate.estimatedNet < estimate.estimatedGross)
    }

    @Test
    fun turnusAndSecondShiftAreNotAppliedToSameMinutes() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 16, 14, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 10, 16, 22, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(
                TimeEvidenceEntry(
                    1L,
                    started,
                    ended,
                    "",
                    WorkType.TURNUS
                )
            ),
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = true,
            turnus = true,
            zone = zone
        )

        assertEquals(480L, estimate.evidence.secondShiftMinutes)
        assertEquals(480L, estimate.evidence.turnusMinutes)
        assertEquals(0L, estimate.secondShiftPaidMinutes)
        assertEquals(480L, estimate.turnusPaidMinutes)
        assertEquals(0.0, estimate.secondShiftAddition, 0.001)
        assertTrue(estimate.turnusAddition > 0.0)
    }

    @Test
    fun explicitSecondShiftUsesTaggedDurationInBreakdownCalculation() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 16, 13, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 10, 16, 21, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(
                TimeEvidenceEntry(
                    1L,
                    started,
                    ended,
                    "",
                    WorkType.SHIFT_2
                )
            ),
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = true,
            turnus = false,
            zone = zone
        )

        assertEquals(420L, estimate.evidence.secondShiftMinutes)
        assertEquals(480L, estimate.evidence.shift2Minutes)
        assertEquals(480L, estimate.secondShiftPaidMinutes)
        assertTrue(estimate.secondShiftAddition > 0.0)
    }

    @Test
    fun privateSectorUsesManualParametersInsteadOfInventedNationalValues() {
        val regime = PublicSectorPayroll.defaultRegimeForSector("Privatni sektor")
        assertEquals("private-manual", regime.id)
        assertEquals("manual", regime.baseType)
        assertEquals(0.0, PublicSectorPayroll.baseFor(YearMonth.of(2026, 10), regime.baseType), 0.001)
        assertTrue(PublicSectorPayroll.rolesFor(regime.id).any { it.id == "manual" })
    }

    @Test
    fun manualLocalRegimeRequiresCustomBaseForNonZeroEstimate() {
        val withoutBase = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = emptyList(),
            regimeId = "local-government",
            coefficient = 2.10,
            yearsService = 10,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 30.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false
        )
        val withBase = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = emptyList(),
            regimeId = "local-government",
            coefficient = 2.10,
            yearsService = 10,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 30.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false,
            customBase = 900.0
        )

        assertEquals(0.0, withoutBase.estimatedGross, 0.001)
        assertTrue(withBase.estimatedGross > 0.0)
    }

    @Test
    fun paidAbsenceDaysContributeToOvertimeThresholdWithoutInventingLeaveAverage() {
        val zone = ZoneId.of("Europe/Zagreb")
        val entries = (1..8).map { day ->
            val started = LocalDateTime.of(2026, 6, day + 1, 7, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val ended = LocalDateTime.of(2026, 6, day + 1, 19, 0)
                .atZone(zone).toInstant().toEpochMilli()
            TimeEvidenceEntry(day.toLong(), started, ended, "", WorkType.TURNUS)
        }
        val schedule = (12..22).associate { day ->
            "2026-06-" + day.toString().padStart(2, '0') to "GO"
        }

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 6),
            entries = entries,
            scheduleCodes = schedule,
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 12,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = true,
            zone = zone
        )

        assertEquals(11, estimate.evidence.goDays)
        assertEquals(88L * 60L, estimate.evidence.compensatedAbsenceMinutes)
        assertEquals(8L * 60L, estimate.evidence.overtimeMinutes)
        assertTrue(estimate.overtimeBasePay > 0.0)
        assertTrue(estimate.overtimeAddition > 0.0)
    }

    @Test
    fun workedLeaveDateIsNotCountedTwiceForOvertimeThreshold() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 6, 12, 7, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 6, 12, 15, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 6),
            entries = listOf(TimeEvidenceEntry(1L, started, ended, "", WorkType.REGULAR)),
            scheduleCodes = mapOf("2026-06-12" to "GO"),
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false,
            zone = zone
        )

        assertEquals(1, estimate.evidence.goDays)
        assertEquals(0L, estimate.evidence.compensatedAbsenceMinutes)
        assertEquals(0L, estimate.evidence.overtimeMinutes)
    }

    @Test
    fun activeEvidenceDoesNotCreateNegativeDuration() {
        val zone = ZoneId.of("Europe/Zagreb")
        val started = LocalDateTime.of(2026, 10, 16, 7, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 10, 16, 9, 30)
            .atZone(zone).toInstant().toEpochMilli()

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            entries = listOf(TimeEvidenceEntry(1L, started, null, "")),
            regimeId = "public-health",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false,
            now = now,
            zone = zone
        )

        assertEquals(150L, estimate.evidence.workedMinutes)
        assertTrue(estimate.evidence.hasActiveEntry)
    }
}
