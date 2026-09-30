package hr.raspored.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class PayrollRole(
    val id: String,
    val label: String,
    val officialName: String,
    val code: String,
    val coefficient: Double,
    val note: String = ""
)

data class PayrollParameters(
    val nightRate: Double = 0.40,
    val overtimeRate: Double = 0.50,
    val saturdayRate: Double = 0.25,
    val sundayRate: Double = 0.50,
    val holidayRate: Double = 1.50,
    val secondShiftRate: Double = 0.10,
    val seniorityPerYear: Double = 0.005
)

data class PayrollEvidence(
    val workedMinutes: Long,
    val nightMinutes: Long,
    val saturdayMinutes: Long,
    val sundayMinutes: Long,
    val holidayMinutes: Long,
    val secondShiftMinutes: Long,
    val overtimeMinutes: Long,
    val hasActiveEntry: Boolean
)

data class PayrollEstimate(
    val base: Double,
    val coefficient: Double,
    val monthlyFundHours: Int,
    val basicGross: Double,
    val hourlyGross: Double,
    val evidence: PayrollEvidence,
    val nightAddition: Double,
    val saturdayAddition: Double,
    val sundayAddition: Double,
    val holidayAddition: Double,
    val overtimeAddition: Double,
    val secondShiftAddition: Double,
    val customAddition: Double
) {
    val additions: Double
        get() = nightAddition + saturdayAddition + sundayAddition + holidayAddition +
            overtimeAddition + secondShiftAddition + customAddition

    val estimatedGross: Double
        get() = basicGross + additions
}

object PublicHealthPayroll {
    val parameters = PayrollParameters()

    val roles: List<PayrollRole> = listOf(
        PayrollRole(
            "kbc-transport-nss",
            "KBC Rijeka — transportni radnik / nosač bolesnika (NSS)",
            "Pomoćni radnik u sustavu s posebnim uvjetima rada",
            "10.1.24",
            1.15,
            "KBC Rijeka koristi službeni naziv transportni radnik; provjeri klasifikaciju u ugovoru ili sistematizaciji."
        ),
        PayrollRole(
            "kbc-transport-sss",
            "KBC Rijeka — transportni radnik / nosač bolesnika (SSS)",
            "Radnik III. vrste",
            "10.1.23",
            1.25
        ),
        PayrollRole(
            "kbc-portir",
            "KBC Rijeka — portir",
            "Stručni radnik na tehničkom održavanju",
            "10.1.20",
            1.39
        ),
        PayrollRole("cleaner-special", "Čistač / spremač — posebni uvjeti", "Čistač – spremač u sustavu s posebnim uvjetima rada", "10.1.25", 1.15),
        PayrollRole("cleaner", "Čistač / spremač", "Čistač – spremač", "10.1.26", 1.06),
        PayrollRole("caregiver", "Njegovatelj / njegovateljica", "Njegovatelj", "16.15.1", 1.35),
        PayrollRole("hospital-attendant", "Bolničar / bolničarka", "Bolničar", "16.15.2", 1.35),
        PayrollRole("nurse-bacc-1", "Viša medicinska sestra / prvostupnik sestrinstva — bolnica 1", "Zdravstveni radnik prvostupnik u bolnici 1", "16.11.1", 1.95),
        PayrollRole("nurse-bacc-2", "Viša medicinska sestra / prvostupnik sestrinstva — bolnica 2", "Zdravstveni radnik prvostupnik u bolnici 2", "16.11.2", 1.87),
        PayrollRole("nurse-sss-1", "Medicinska sestra/tehničar — bolnica 1", "Zdravstveni radnik u bolnici 1", "16.13.1", 1.78),
        PayrollRole("nurse-sss-2", "Medicinska sestra/tehničar — bolnica 2", "Zdravstveni radnik u bolnici 2", "16.13.2", 1.70),
        PayrollRole("nurse-master-special", "Magistra sestrinstva / dipl. medicinska sestra — posebni poslovi", "Magistra sestrinstva/diplomirana medicinska sestra na propisanim posebnim poslovima", "16.10.1", 2.45),
        PayrollRole("physio-bacc-1", "Viši fizioterapeut / prvostupnik fizioterapije — bolnica 1", "Zdravstveni radnik prvostupnik u bolnici 1", "16.11.1", 1.95),
        PayrollRole("physio-bacc-2", "Viši fizioterapeut / prvostupnik fizioterapije — bolnica 2", "Zdravstveni radnik prvostupnik u bolnici 2", "16.11.2", 1.87),
        PayrollRole("doctor-1", "Doktor medicine — 1", "Doktor medicine i doktor dentalne medicine 1", "16.4.3", 2.92),
        PayrollRole("doctor-2", "Doktor medicine — 2", "Doktor medicine i doktor dentalne medicine 2", "16.4.6", 2.83),
        PayrollRole("doctor-3", "Doktor medicine — 3", "Doktor medicine i doktor dentalne medicine 3", "16.4.9", 2.81),
        PayrollRole("doctor-specialization", "Doktor medicine na specijalizaciji", "Doktor medicine i doktor dentalne medicine na specijalizaciji", "16.4.10", 2.81),
        PayrollRole("doctor-specialist-1", "Doktor medicine specijalist — 1", "Doktor medicine specijalist i doktor dentalne medicine specijalist 1", "16.4.2", 3.82),
        PayrollRole("doctor-specialist-2", "Doktor medicine specijalist — 2", "Doktor medicine specijalist i doktor dentalne medicine specijalist 2", "16.4.5", 3.74),
        PayrollRole("doctor-specialist-3", "Doktor medicine specijalist — 3", "Doktor medicine specijalist i doktor dentalne medicine specijalist 3", "16.4.8", 3.65)
    )

    fun role(id: String): PayrollRole = roles.firstOrNull { it.id == id } ?: roles.first()

    fun baseFor(month: YearMonth): Double = when {
        month < YearMonth.of(2026, 4) -> 1004.87
        month < YearMonth.of(2026, 8) -> 1015.00
        month < YearMonth.of(2026, 12) -> 1025.00
        else -> 1035.00
    }

    fun monthlyFundHours(month: YearMonth): Int =
        (1..month.lengthOfMonth()).count { day ->
            val dayOfWeek = month.atDay(day).dayOfWeek
            dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
        } * 8

    fun estimate(
        month: YearMonth,
        entries: List<TimeEvidenceEntry>,
        coefficient: Double,
        yearsService: Int,
        extraPercent: Double,
        secondShift: Boolean,
        customBase: Double? = null,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): PayrollEstimate {
        val params = parameters
        val base = customBase?.takeIf { it > 0.0 } ?: baseFor(month)
        val safeCoefficient = coefficient.coerceIn(1.0, 8.0)
        val safeYears = yearsService.coerceIn(0, 60)
        val fundHours = monthlyFundHours(month)
        val basicGross = base * safeCoefficient * (1.0 + safeYears * params.seniorityPerYear)
        val hourly = if (fundHours > 0) basicGross / fundHours else 0.0
        val evidence = summarizeEvidence(month, entries, secondShift, now, zone)
        fun add(minutes: Long, rate: Double): Double = hourly * (minutes / 60.0) * rate
        val custom = basicGross * (extraPercent.coerceIn(0.0, 100.0) / 100.0)

        return PayrollEstimate(
            base = base,
            coefficient = safeCoefficient,
            monthlyFundHours = fundHours,
            basicGross = basicGross,
            hourlyGross = hourly,
            evidence = evidence,
            nightAddition = add(evidence.nightMinutes, params.nightRate),
            saturdayAddition = add(evidence.saturdayMinutes, params.saturdayRate),
            sundayAddition = add(evidence.sundayMinutes, params.sundayRate),
            holidayAddition = add(evidence.holidayMinutes, params.holidayRate),
            overtimeAddition = add(evidence.overtimeMinutes, params.overtimeRate),
            secondShiftAddition = if (secondShift) add(evidence.secondShiftMinutes, params.secondShiftRate) else 0.0,
            customAddition = custom
        )
    }

    private fun summarizeEvidence(
        month: YearMonth,
        entries: List<TimeEvidenceEntry>,
        secondShift: Boolean,
        now: Long,
        zone: ZoneId
    ): PayrollEvidence {
        val holidays = CroatianHolidays.forYear(month.year)
        var worked = 0L
        var night = 0L
        var saturday = 0L
        var sunday = 0L
        var holiday = 0L
        var second = 0L
        var hasActive = false

        entries.forEach { entry ->
            val end = (entry.endedAt ?: now).coerceAtMost(entry.startedAt + 36L * 60L * 60L * 1000L)
            if (entry.endedAt == null) hasActive = true
            if (end <= entry.startedAt) return@forEach
            var minute = entry.startedAt
            while (minute < end) {
                val local = Instant.ofEpochMilli(minute).atZone(zone)
                val date = local.toLocalDate()
                if (YearMonth.from(date) == month) {
                    worked++
                    val hour = local.hour
                    if (hour >= 22 || hour < 6) night++
                    if (date.dayOfWeek == DayOfWeek.SATURDAY) saturday++
                    if (date.dayOfWeek == DayOfWeek.SUNDAY) sunday++
                    if (holidays.containsKey(date)) holiday++
                    if (secondShift && hour in 14..21) second++
                }
                minute += 60_000L
            }
        }

        val overtime = (worked - monthlyFundHours(month) * 60L).coerceAtLeast(0L)
        return PayrollEvidence(worked, night, saturday, sunday, holiday, second, overtime, hasActive)
    }
}
