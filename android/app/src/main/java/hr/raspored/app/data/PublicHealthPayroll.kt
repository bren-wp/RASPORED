package hr.raspored.app.data

import java.time.DayOfWeek
import java.time.Instant
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

data class PayrollInstitution(
    val id: String,
    val name: String,
    val city: String,
    val defaultRateProfileId: String = "national-public",
    val verifyRegime: Boolean = false
)

data class PayrollRateProfile(
    val id: String,
    val label: String,
    val nightRate: Double,
    val overtimeRate: Double = 0.50,
    val saturdayRate: Double = 0.25,
    val sundayRate: Double = 0.50,
    val holidayRate: Double = 1.50,
    val secondShiftRate: Double = 0.10,
    val turnusRate: Double = 0.05,
    val note: String
)

data class PayrollEvidence(
    val workedMinutes: Long,
    val nightMinutes: Long,
    val saturdayMinutes: Long,
    val sundayMinutes: Long,
    val holidayMinutes: Long,
    val secondShiftClockMinutes: Long,
    val automaticOvertimeMinutes: Long,
    val hasActiveEntry: Boolean
)

data class PayrollEstimate(
    val base: Double,
    val coefficient: Double,
    val monthlyFundHours: Int,
    val basicGross: Double,
    val hourlyGross: Double,
    val evidence: PayrollEvidence,
    val overtimeHours: Double,
    val overtimeBase: Double,
    val nightAddition: Double,
    val saturdayAddition: Double,
    val sundayAddition: Double,
    val holidayAddition: Double,
    val overtimeAddition: Double,
    val turnusAddition: Double,
    val secondShiftAddition: Double,
    val customPercentAddition: Double,
    val grossAdjustment: Double
) {
    val additions: Double
        get() = nightAddition + saturdayAddition + sundayAddition + holidayAddition +
            overtimeAddition + turnusAddition + secondShiftAddition +
            customPercentAddition + grossAdjustment

    val estimatedGross: Double
        get() = basicGross + overtimeBase + additions
}

object PublicHealthPayroll {
    const val SENIORITY_PER_YEAR = 0.005

    val rateProfiles: List<PayrollRateProfile> = listOf(
        PayrollRateProfile(
            id = "national-public",
            label = "Javne službe — službeni minimum/propis",
            nightRate = 0.40,
            note = "TKU: noć 40%, prekovremeni 50%, subota 25%, nedjelja 50%, blagdan 150%, druga smjena 10%; turnus 5% prema tumačenju članka 109."
        ),
        PayrollRateProfile(
            id = "kbc-rijeka-observed-2026",
            label = "KBC Rijeka — obračunski profil 2026",
            nightRate = 0.50,
            note = "Noćni dodatak 50% potvrđen je na obračunskim ispravama KBC Rijeka iz 2026. Profil je odvojen od općeg TKU minimuma i ne primjenjuje se automatski na druge ustanove."
        )
    )

    val institutions: List<PayrollInstitution> = listOf(
        PayrollInstitution("kbc-zagreb", "KLINIČKI BOLNIČKI CENTAR ZAGREB", "Zagreb"),
        PayrollInstitution("kbc-rijeka", "KLINIČKI BOLNIČKI CENTAR RIJEKA", "Rijeka", "kbc-rijeka-observed-2026"),
        PayrollInstitution("kbc-split", "KLINIČKI BOLNIČKI CENTAR SPLIT", "Split"),
        PayrollInstitution("kbc-sestre-milosrdnice", "KLINIČKI BOLNIČKI CENTAR SESTRE MILOSRDNICE", "Zagreb"),
        PayrollInstitution("kbc-osijek", "KLINIČKI BOLNIČKI CENTAR OSIJEK", "Osijek"),
        PayrollInstitution("kb-dubrava", "KLINIČKA BOLNICA DUBRAVA", "Zagreb"),
        PayrollInstitution("kb-merkur", "KLINIČKA BOLNICA \"MERKUR\"", "Zagreb"),
        PayrollInstitution("kb-sveti-duh", "KLINIČKA BOLNICA \"SVETI DUH\"", "Zagreb"),
        PayrollInstitution("klinika-ortopedija-lovran", "KLINIKA ZA ORTOPEDIJU LOVRAN", "Lovran"),
        PayrollInstitution("klinika-fran-mihaljevic", "KLINIKA ZA INFEKTIVNE BOLESTI \"DR. FRAN MIHALJEVIĆ\"", "Zagreb"),
        PayrollInstitution("klinika-vrapce", "KLINIKA ZA PSIHIJATRIJU VRAPČE", "Zagreb"),
        PayrollInstitution("kdb-zagreb", "KLINIKA ZA DJEČJE BOLESTI ZAGREB", "Zagreb"),
        PayrollInstitution("magdalena", "MAGDALENA - KLINIKA ZA KARDIOVASKULARNE BOLESTI", "Krapinske Toplice", verifyRegime = true),
        PayrollInstitution("ob-sisak", "OPĆA BOLNICA \"DR. IVO PEDIŠIĆ\" SISAK", "Sisak"),
        PayrollInstitution("ob-karlovac", "OPĆA BOLNICA KARLOVAC", "Karlovac"),
        PayrollInstitution("ob-ogulin", "OPĆA BOLNICA I BOLNICA BRANITELJA DOMOVINSKOG RATA OGULIN", "Ogulin"),
        PayrollInstitution("ob-varazdin", "OPĆA BOLNICA VARAŽDIN", "Varaždin"),
        PayrollInstitution("ob-koprivnica", "OPĆA BOLNICA \"DR. TOMISLAV BARDEK\" KOPRIVNICA", "Koprivnica"),
        PayrollInstitution("ob-bjelovar", "OPĆA BOLNICA BJELOVAR", "Bjelovar"),
        PayrollInstitution("ob-gospic", "OPĆA BOLNICA GOSPIĆ", "Gospić"),
        PayrollInstitution("ob-virovitica", "OPĆA BOLNICA VIROVITICA", "Virovitica"),
        PayrollInstitution("ozb-pozega", "OPĆA ŽUPANIJSKA BOLNICA POŽEGA", "Požega"),
        PayrollInstitution("ozb-pakrac", "OPĆA ŽUPANIJSKA BOLNICA PAKRAC I BOLNICA HRVATSKIH VETERANA", "Pakrac"),
        PayrollInstitution("ob-slavonski-brod", "OPĆA BOLNICA \"DR. JOSIP BENČEVIĆ\" SLAVONSKI BROD", "Slavonski Brod"),
        PayrollInstitution("ob-nova-gradiska", "OPĆA BOLNICA NOVA GRADIŠKA", "Nova Gradiška"),
        PayrollInstitution("ob-zadar", "OPĆA BOLNICA ZADAR", "Zadar"),
        PayrollInstitution("ozb-nasice", "OPĆA ŽUPANIJSKA BOLNICA NAŠICE", "Našice"),
        PayrollInstitution("ob-sibenik", "OPĆA BOLNICA ŠIBENSKO-KNINSKE ŽUPANIJE", "Šibenik"),
        PayrollInstitution("ob-knin", "OPĆA I VETERANSKA BOLNICA \"HRVATSKI PONOS\" KNIN", "Knin"),
        PayrollInstitution("ozb-vinkovci", "OPĆA ŽUPANIJSKA BOLNICA VINKOVCI", "Vinkovci"),
        PayrollInstitution("nmb-vukovar", "NACIONALNA MEMORIJALNA BOLNICA \"DR. JURAJ NJAVRO\" VUKOVAR", "Vukovar"),
        PayrollInstitution("ob-pula", "OPĆA BOLNICA PULA", "Pula"),
        PayrollInstitution("ob-dubrovnik", "OPĆA BOLNICA DUBROVNIK", "Dubrovnik"),
        PayrollInstitution("zb-cakovec", "ŽUPANIJSKA BOLNICA ČAKOVEC", "Čakovec"),
        PayrollInstitution("ob-zabok", "OPĆA BOLNICA ZABOK I BOLNICA HRVATSKIH VETERANA", "Zabok"),
        PayrollInstitution("pb-djeca-mladez", "PSIHIJATRIJSKA BOLNICA ZA DJECU I MLADEŽ", "Zagreb"),
        PayrollInstitution("sb-goljak", "SPECIJALNA BOLNICA ZA ZAŠTITU DJECE S NEURORAZVOJNIM I MOTORIČKIM SMETNJAMA", "Zagreb"),
        PayrollInstitution("sb-plucne-bolesti", "SPECIJALNA BOLNICA ZA PLUĆNE BOLESTI", "Zagreb"),
        PayrollInstitution("klinika-sveti-ivan", "KLINIKA ZA PSIHIJATRIJU SVETI IVAN", "Zagreb"),
        PayrollInstitution("djecja-bolnica-srebrnjak", "DJEČJA BOLNICA SREBRNJAK", "Zagreb"),
        PayrollInstitution("naftalan", "NAFTALAN, SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU", "Ivanić-Grad"),
        PayrollInstitution("sb-gornja-bistra", "SPECIJALNA BOLNICA ZA KRONIČNE BOLESTI DJEČJE DOBI GORNJA BISTRA", "Gornja Bistra"),
        PayrollInstitution("sb-krapinske-toplice", "SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU KRAPINSKE TOPLICE", "Krapinske Toplice"),
        PayrollInstitution("sb-stubicke-toplice", "SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU STUBIČKE TOPLICE", "Stubičke Toplice"),
        PayrollInstitution("npb-popovaca", "NEUROPSIHIJATRIJSKA BOLNICA DR. IVAN BARBOT POPOVAČA", "Popovača"),
        PayrollInstitution("sb-duga-resa", "SPECIJALNA BOLNICA ZA PRODUŽENO LIJEČENJE DUGA RESA", "Duga Resa"),
        PayrollInstitution("sb-varazdinske-toplice", "SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU VARAŽDINSKE TOPLICE", "Varaždinske Toplice"),
        PayrollInstitution("daruvarske-toplice", "DARUVARSKE TOPLICE SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU", "Daruvar"),
        PayrollInstitution("thalassotherapia-opatija", "THALASSOTHERAPIA SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU BOLESTI SRCA, PLUĆA I REUMATIZMA", "Opatija"),
        PayrollInstitution("thalassotherapia-crikvenica", "THALASSOTHERAPIA - CRIKVENICA SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU PRIMORSKO-GORANSKE ŽUPANIJE", "Crikvenica"),
        PayrollInstitution("insula-rab", "INSULA - ŽUPANIJSKA SPECIJALNA BOLNICA ZA PSIHIJATRIJU I REHABILITACIJU", "Rab"),
        PayrollInstitution("toplice-lipik", "TOPLICE LIPIK - SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU", "Lipik"),
        PayrollInstitution("sb-ortopedija-biograd", "SPECIJALNA BOLNICA ZA ORTOPEDIJU", "Biograd na Moru"),
        PayrollInstitution("pb-ugljan", "PSIHIJATRIJSKA BOLNICA UGLJAN", "Ugljan"),
        PayrollInstitution("biokovka", "\"BIOKOVKA\" SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU", "Makarska"),
        PayrollInstitution("sb-martin-horvat-rovinj", "SPECIJALNA BOLNICA ZA ORTOPEDIJU I REHABILITACIJU „MARTIN HORVAT“ ROVINJ-ROVIGNO", "Rovinj-Rovigno"),
        PayrollInstitution("sb-kalos", "SPECIJALNA BOLNICA ZA MEDICINSKU REHABILITACIJU KALOS", "Vela Luka"),
        PayrollInstitution("pb-lopaca", "PSIHIJATRIJSKA BOLNICA LOPAČA", "Dražice"),
        PayrollInstitution("sb-sveti-rafael-strmac", "SPECIJALNA BOLNICA ZA PSIHIJATRIJU I PALIJATIVNU SKRB \"SVETI RAFAEL\" STRMAC", "Cernik", verifyRegime = true),
        PayrollInstitution("ljeciliste-topusko", "LJEČILIŠTE TOPUSKO", "Topusko"),
        PayrollInstitution("ljeciliste-veli-losinj", "LJEČILIŠTE VELI LOŠINJ", "Veli Lošinj"),
        PayrollInstitution("ljeciliste-bizovacke-toplice", "LJEČILIŠTE BIZOVAČKE TOPLICE", "Bizovac")
    )

    val roles: List<PayrollRole> = listOf(
        PayrollRole("kbc-transport-nss", "Nosač bolesnika / transportni radnik — NSS", "Pomoćni radnik u sustavu s posebnim uvjetima rada", "10.1.24", 1.15, "KBC Rijeka sistematizacijom koristi naziv Transportni radnik za NSS varijantu."),
        PayrollRole("kbc-transport-sss", "Nosač bolesnika / transportni radnik — SSS", "Radnik III. vrste", "10.1.23", 1.25, "KBC Rijeka sistematizacijom koristi naziv Transportni radnik i za SSS varijantu."),
        PayrollRole("kbc-portir", "Portir — tehničko održavanje", "Stručni radnik na tehničkom održavanju", "10.1.20", 1.39),
        PayrollRole("cleaner-special", "Spremačica / spremač — posebni uvjeti", "Čistač – spremač u sustavu s posebnim uvjetima rada", "10.1.25", 1.15),
        PayrollRole("cleaner", "Spremačica / spremač", "Čistač – spremač", "10.1.26", 1.06),
        PayrollRole("caregiver", "Njegovateljica / njegovatelj", "Njegovatelj", "16.15.1", 1.35),
        PayrollRole("hospital-attendant", "Bolničarka / bolničar", "Bolničar", "16.15.2", 1.35),
        PayrollRole("nurse-bacc-1", "Viša medicinska sestra / prvostupnik sestrinstva — bolnica 1", "Zdravstveni radnik prvostupnik u bolnici 1", "16.11.1", 1.95),
        PayrollRole("nurse-bacc-2", "Viša medicinska sestra / prvostupnik sestrinstva — bolnica 2", "Zdravstveni radnik prvostupnik u bolnici 2", "16.11.2", 1.87),
        PayrollRole("nurse-bacc-3", "Prvostupnik zdravstvene struke — razina 3", "Zdravstveni radnik prvostupnik 3", "16.11.3", 1.82),
        PayrollRole("nurse-emergency-specialist", "Medicinska sestra/tehničar specijalist hitne medicine", "Medicinska sestra/medicinski tehničar specijalist hitne medicine", "16.11.4", 2.20),
        PayrollRole("nurse-sss-1", "Medicinska sestra/tehničar — bolnica 1", "Zdravstveni radnik u bolnici 1", "16.13.1", 1.78),
        PayrollRole("nurse-sss-2", "Medicinska sestra/tehničar — bolnica 2", "Zdravstveni radnik u bolnici 2", "16.13.2", 1.70),
        PayrollRole("nurse-sss-home", "Medicinska sestra/tehničar — kućna njega/sanitetski prijevoz", "Zdravstveni radnik III. vrste — kućna njega/sanitetski prijevoz/stacionar", "16.13.3", 1.64),
        PayrollRole("nurse-sss-primary", "Medicinska sestra/tehničar — primarna/poliklinika/javno zdravstvo 3", "Zdravstveni radnik 3", "16.13.5", 1.55),
        PayrollRole("nurse-master-special", "Magistra sestrinstva / dipl. medicinska sestra — posebni poslovi", "Magistra sestrinstva/diplomirana medicinska sestra na propisanim posebnim poslovima", "16.10.1", 2.45),
        PayrollRole("physio-bacc-1", "Viši fizioterapeut / prvostupnik fizioterapije — bolnica 1", "Zdravstveni radnik prvostupnik u bolnici 1", "16.11.1", 1.95),
        PayrollRole("physio-bacc-2", "Viši fizioterapeut / prvostupnik fizioterapije — bolnica 2", "Zdravstveni radnik prvostupnik u bolnici 2", "16.11.2", 1.87),
        PayrollRole("doctor-1", "Doktor medicine — skupina 1", "Doktor medicine i doktor dentalne medicine 1", "16.4.3", 2.92),
        PayrollRole("doctor-2", "Doktor medicine — skupina 2", "Doktor medicine i doktor dentalne medicine 2", "16.4.6", 2.83),
        PayrollRole("doctor-3", "Doktor medicine — skupina 3", "Doktor medicine i doktor dentalne medicine 3", "16.4.9", 2.81),
        PayrollRole("doctor-specialization", "Doktor medicine na specijalizaciji", "Doktor medicine i doktor dentalne medicine na specijalizaciji", "16.4.10", 2.81),
        PayrollRole("doctor-specialist-1", "Doktor medicine specijalist — skupina 1", "Doktor medicine specijalist i doktor dentalne medicine specijalist 1", "16.4.2", 3.82),
        PayrollRole("doctor-specialist-2", "Doktor medicine specijalist — skupina 2", "Doktor medicine specijalist i doktor dentalne medicine specijalist 2", "16.4.5", 3.74),
        PayrollRole("doctor-specialist-3", "Doktor medicine specijalist — skupina 3", "Doktor medicine specijalist i doktor dentalne medicine specijalist 3", "16.4.8", 3.65),
        PayrollRole("doctor-advisor-1", "Doktor medicine specijalist savjetnik — skupina 1", "Doktor medicine specijalist savjetnik 1", "16.4.1", 3.95),
        PayrollRole("doctor-advisor-2", "Doktor medicine specijalist savjetnik — skupina 2", "Doktor medicine specijalist savjetnik 2", "16.4.4", 3.85),
        PayrollRole("doctor-advisor-3", "Doktor medicine specijalist savjetnik — skupina 3", "Doktor medicine specijalist savjetnik 3", "16.4.7", 3.83)
    )

    fun role(id: String): PayrollRole = roles.firstOrNull { it.id == id } ?: roles.first()
    fun institution(id: String): PayrollInstitution = institutions.firstOrNull { it.id == id } ?: institutions.first()
    fun rateProfile(id: String): PayrollRateProfile = rateProfiles.firstOrNull { it.id == id } ?: rateProfiles.first()

    fun baseFor(month: YearMonth): Double = when {
        month.year != 2026 -> 0.0
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
        rateProfileId: String = "national-public",
        extraPercent: Double = 0.0,
        customBase: Double? = null,
        overtimeHoursOverride: Double? = null,
        turnusHours: Double = 0.0,
        secondShiftHours: Double = 0.0,
        grossAdjustment: Double = 0.0,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): PayrollEstimate {
        val profile = rateProfile(rateProfileId)
        val base = customBase?.takeIf { it > 0.0 } ?: baseFor(month)
        val safeCoefficient = coefficient.coerceIn(1.0, 8.0)
        val safeYears = yearsService.coerceIn(0, 60)
        val fundHours = monthlyFundHours(month)
        val basicGross = base * safeCoefficient * (1.0 + safeYears * SENIORITY_PER_YEAR)
        val hourly = if (fundHours > 0) basicGross / fundHours else 0.0
        val evidence = summarizeEvidence(month, entries, now, zone)
        val overtimeHours = (overtimeHoursOverride ?: evidence.automaticOvertimeMinutes / 60.0)
            .coerceIn(0.0, 250.0)
        val safeTurnus = turnusHours.coerceIn(0.0, 300.0)
        val safeSecond = secondShiftHours.coerceIn(0.0, 300.0)
        fun add(hours: Double, rate: Double): Double = hourly * hours * rate
        val customPercent = basicGross * (extraPercent.coerceIn(0.0, 100.0) / 100.0)

        return PayrollEstimate(
            base = base,
            coefficient = safeCoefficient,
            monthlyFundHours = fundHours,
            basicGross = basicGross,
            hourlyGross = hourly,
            evidence = evidence,
            overtimeHours = overtimeHours,
            overtimeBase = hourly * overtimeHours,
            nightAddition = add(evidence.nightMinutes / 60.0, profile.nightRate),
            saturdayAddition = add(evidence.saturdayMinutes / 60.0, profile.saturdayRate),
            sundayAddition = add(evidence.sundayMinutes / 60.0, profile.sundayRate),
            holidayAddition = add(evidence.holidayMinutes / 60.0, profile.holidayRate),
            overtimeAddition = add(overtimeHours, profile.overtimeRate),
            turnusAddition = add(safeTurnus, profile.turnusRate),
            secondShiftAddition = add(safeSecond, profile.secondShiftRate),
            customPercentAddition = customPercent,
            grossAdjustment = grossAdjustment.coerceIn(-10_000.0, 10_000.0)
        )
    }

    private fun summarizeEvidence(
        month: YearMonth,
        entries: List<TimeEvidenceEntry>,
        now: Long,
        zone: ZoneId
    ): PayrollEvidence {
        val holidays = CroatianHolidays.forYear(month.year)
        var worked = 0L
        var night = 0L
        var saturday = 0L
        var sunday = 0L
        var holiday = 0L
        var secondShiftClock = 0L
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
                    if (hour in 14..21) secondShiftClock++
                }
                minute += 60_000L
            }
        }

        val overtime = (worked - monthlyFundHours(month) * 60L).coerceAtLeast(0L)
        return PayrollEvidence(
            workedMinutes = worked,
            nightMinutes = night,
            saturdayMinutes = saturday,
            sundayMinutes = sunday,
            holidayMinutes = holiday,
            secondShiftClockMinutes = secondShiftClock,
            automaticOvertimeMinutes = overtime,
            hasActiveEntry = hasActive
        )
    }
}
