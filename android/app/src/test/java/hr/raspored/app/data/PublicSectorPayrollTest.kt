package hr.raspored.app.data

import java.time.YearMonth
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
    fun kbcRijekaUsesCurrentPublicServiceRatesWithoutInventedTurnus() {
        val regime = PublicSectorPayroll.regime("kbc-rijeka-2026")
        assertEquals(0.40, regime.rates.night ?: -1.0, 0.001)
        assertEquals(null, regime.rates.turnus)
        assertEquals(0.25, regime.rates.saturday ?: -1.0, 0.001)
        assertEquals(0.50, regime.rates.sunday ?: -1.0, 0.001)
        assertEquals(1.50, regime.rates.holiday ?: -1.0, 0.001)
        assertEquals(0.10, regime.rates.secondShift ?: -1.0, 0.001)
        assertEquals(0.50, regime.rates.overtime ?: -1.0, 0.001)
    }

    @Test
    fun stateServiceUsesCurrentCollectiveAgreementRates() {
        val regime = PublicSectorPayroll.regime("state-service")
        assertEquals(0.40, regime.rates.night ?: -1.0, 0.001)
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
        assertEquals(61, PublicSectorPayroll.institutions.count { it.sector == "Zdravstvo" })
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
    }

    @Test
    fun salaryUsesCalendarNightShiftAcrossWeekendBoundary() {
        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            scheduleCodes = mapOf("2026-10-03" to "N"),
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 12,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false
        )

        assertEquals(12L * 60L, estimate.evidence.workedMinutes)
        assertEquals(8L * 60L, estimate.evidence.nightMinutes)
        assertTrue(estimate.evidence.saturdayMinutes > 0L)
        assertTrue(estimate.evidence.sundayMinutes > 0L)
        assertTrue(estimate.nightAddition > 0.0)
        assertTrue(estimate.estimatedGross > estimate.basicGross)
    }

    @Test
    fun turnusAndSecondShiftAreNotAppliedToSameCalendarMinutes() {
        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            scheduleCodes = mapOf("2026-10-16" to "D"),
            regimeId = "state-service",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = true,
            turnus = true
        )

        assertEquals(12L * 60L, estimate.evidence.workedMinutes)
        assertEquals(12L * 60L, estimate.evidence.turnusMinutes)
        assertEquals(0L, estimate.secondShiftPaidMinutes)
        assertEquals(12L * 60L, estimate.turnusPaidMinutes)
        assertTrue(estimate.turnusAddition > 0.0)
    }

    @Test
    fun explicitSecondShiftUsesCalendarAfternoonWindow() {
        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            scheduleCodes = mapOf("2026-10-16" to "D"),
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = true,
            turnus = false
        )

        assertEquals(5L * 60L, estimate.evidence.secondShiftMinutes)
        assertEquals(5L * 60L, estimate.evidence.shift2Minutes)
        assertEquals(5L * 60L, estimate.secondShiftPaidMinutes)
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
    fun paidAbsenceCountsOnlyRegularFundDays() {
        val schedule = buildMap {
            (2..9).forEach { day ->
                put("2026-06-" + day.toString().padStart(2, '0'), "D")
            }
            (12..22).forEach { day ->
                put("2026-06-" + day.toString().padStart(2, '0'), "GO")
            }
        }

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 6),
            scheduleCodes = schedule,
            regimeId = "kbc-rijeka-2026",
            coefficient = 1.25,
            yearsService = 12,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false
        )

        assertEquals(11, estimate.evidence.goDays)
        assertEquals(56L * 60L, estimate.evidence.compensatedAbsenceMinutes)
        assertEquals(0L, estimate.evidence.overtimeMinutes)
        assertEquals(0.0, estimate.overtimeBasePay, 0.001)
    }

    @Test
    fun actualWorkAboveMonthlyFundCreatesPaidOvertime() {
        val schedule = buildMap {
            (1..16).forEach { day ->
                put("2026-10-" + day.toString().padStart(2, '0'), "D")
            }
        }

        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            scheduleCodes = schedule,
            regimeId = "public-health",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false
        )

        assertEquals(16L * 60L, estimate.evidence.overtimeMinutes)
        assertTrue(estimate.overtimeBasePay > 0.0)
        assertTrue(estimate.overtimeAddition > 0.0)
    }

    @Test
    fun weekendAbsenceDoesNotCreateFundHoursOrOvertime() {
        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            scheduleCodes = mapOf(
                "2026-10-03" to "GO",
                "2026-10-04" to "BO"
            ),
            regimeId = "public-health",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false
        )

        assertEquals(1, estimate.evidence.goDays)
        assertEquals(1, estimate.evidence.boDays)
        assertEquals(0L, estimate.evidence.compensatedAbsenceMinutes)
        assertEquals(0L, estimate.evidence.overtimeMinutes)
    }

    @Test
    fun customCalendarCodesDoNotInventPayrollHours() {
        val estimate = PublicSectorPayroll.estimate(
            month = YearMonth.of(2026, 10),
            scheduleCodes = mapOf(
                "2026-10-01" to "J",
                "2026-10-02" to "P1"
            ),
            regimeId = "public-health",
            coefficient = 1.25,
            yearsService = 0,
            personalAllowance = 600.0,
            taxLower = 20.0,
            taxHigher = 25.0,
            extraPercent = 0.0,
            secondShift = false,
            turnus = false
        )

        assertEquals(0L, estimate.evidence.workedMinutes)
        assertEquals(0L, estimate.evidence.nightMinutes)
    }
}
