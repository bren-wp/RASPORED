package hr.raspored.app.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceAnalyticsTest {

    @Test
    fun derivesWorkedHoursDirectlyFromCalendarShifts() {
        val month = YearMonth.of(2026, 10)
        val summary = EvidenceAnalytics.summarize(
            month = month,
            scheduleCodes = mapOf(
                "2026-10-16" to "D",
                "2026-10-17" to "N"
            )
        )

        assertEquals(24L * 60L, summary.plannedMinutes)
        assertEquals(24L * 60L, summary.workedMinutes)
        assertEquals(16L * 60L, summary.dayMinutes)
        assertEquals(8L * 60L, summary.nightMinutes)
        assertEquals(0L, summary.balanceMinutes)
    }

    @Test
    fun customAndAbsenceCodesDoNotInventWorkedHours() {
        val month = YearMonth.of(2026, 10)
        val summary = EvidenceAnalytics.summarize(
            month = month,
            scheduleCodes = mapOf(
                "2026-10-01" to "J",
                "2026-10-02" to "P1",
                "2026-10-03" to "GO",
                "2026-10-04" to "BO",
                "2026-10-05" to "PD",
                "2026-10-06" to "SD"
            )
        )

        assertEquals(0L, summary.workedMinutes)
        assertEquals("Nije definirano", EvidenceAnalytics.hoursLabel("J"))
        assertEquals("—", EvidenceAnalytics.hoursLabel("GO"))
    }

    @Test
    fun weekendAndHolidayHoursComeFromScheduledShifts() {
        val easter = CroatianHolidays.easterSunday(2026)
        val summary = EvidenceAnalytics.summarize(
            month = YearMonth.from(easter),
            scheduleCodes = mapOf(easter.toString() to "D")
        )

        assertEquals(12L * 60L, summary.workedMinutes)
        assertEquals(12L * 60L, summary.sundayMinutes)
        assertEquals(12L * 60L, summary.holidayMinutes)
        assertEquals(12L * 60L, summary.weekendHolidayMinutes)
    }
}
