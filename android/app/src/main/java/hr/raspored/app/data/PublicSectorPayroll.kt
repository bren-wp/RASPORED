package hr.raspored.app.data

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId

data class PayrollRates(
    val night: Double? = null,
    val overtime: Double? = null,
    val saturday: Double? = null,
    val sunday: Double? = null,
    val holiday: Double? = null,
    val secondShift: Double? = null,
    val turnus: Double? = null
)

data class PayrollRegime(
    val id: String,
    val sector: String,
    val label: String,
    val baseType: String,
    val rates: PayrollRates,
    val note: String = ""
)

data class PayrollRole(
    val id: String,
    val regimes: Set<String>,
    val label: String,
    val officialName: String,
    val code: String,
    val coefficient: Double?,
    val note: String = ""
)

data class PayrollInstitution(
    val county: String,
    val city: String,
    val name: String,
    val regimeId: String,
    val sector: String
)

data class PayrollTaxLocality(
    val county: String,
    val name: String,
    val lowerRate: Double,
    val higherRate: Double,
    val source: String
)

data class PayrollEvidence(
    val workedMinutes: Long,
    val nightMinutes: Long,
    val saturdayMinutes: Long,
    val sundayMinutes: Long,
    val holidayMinutes: Long,
    val secondShiftMinutes: Long,
    val shift1Minutes: Long,
    val shift2Minutes: Long,
    val shift3Minutes: Long,
    val turnusMinutes: Long,
    val dutyMinutes: Long,
    val standbyMinutes: Long,
    val calloutMinutes: Long,
    val compensatedAbsenceMinutes: Long,
    val holidayCompensatedMinutes: Long,
    val holidayCompensatedDays: Int,
    val goDays: Int,
    val boDays: Int,
    val pdDays: Int,
    val sdDays: Int,
    val overtimeMinutes: Long,
    val workedDays: Int,
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
    val overtimeBasePay: Double,
    val overtimeAddition: Double,
    val secondShiftPaidMinutes: Long,
    val turnusPaidMinutes: Long,
    val secondShiftAddition: Double,
    val turnusAddition: Double,
    val customAddition: Double,
    val pensionContribution: Double,
    val incomeTax: Double,
    val estimatedNet: Double,
    val dailyGross: Double,
    val dailyNet: Double
) {
    val additions: Double
        get() = nightAddition + saturdayAddition + sundayAddition + holidayAddition +
            overtimeBasePay + overtimeAddition + secondShiftAddition + turnusAddition + customAddition

    val estimatedGross: Double
        get() = basicGross + additions
}

object PublicSectorPayroll {
    const val YEAR = 2026
    const val SENIORITY_PER_YEAR = 0.005
    const val PENSION_CONTRIBUTION = 0.20
    const val BASIC_PERSONAL_ALLOWANCE = 600.0
    const val MONTHLY_TAX_THRESHOLD = 5_000.0

    val counties = listOf(
        "Zagrebačka", "Krapinsko-zagorska", "Sisačko-moslavačka", "Karlovačka",
        "Varaždinska", "Koprivničko-križevačka", "Bjelovarsko-bilogorska",
        "Primorsko-goranska", "Ličko-senjska", "Virovitičko-podravska",
        "Požeško-slavonska", "Brodsko-posavska", "Zadarska", "Osječko-baranjska",
        "Šibensko-kninska", "Vukovarsko-srijemska", "Splitsko-dalmatinska",
        "Istarska", "Dubrovačko-neretvanska", "Međimurska", "Grad Zagreb"
    )

    val taxLocalities = listOf(
        PayrollTaxLocality("Grad Zagreb", "Zagreb", 23.0, 33.0, "NN 28/2025"),
        PayrollTaxLocality("Primorsko-goranska", "Rijeka", 20.0, 25.0, "NN 149/2025"),
        PayrollTaxLocality("Splitsko-dalmatinska", "Split", 21.5, 32.0, "RRiF 2026 / NN 35/2025"),
        PayrollTaxLocality("Osječko-baranjska", "Osijek", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Zadarska", "Zadar", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Istarska", "Pazin", 22.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Istarska", "Pula", 22.0, 32.0, "RRiF 2026"),
        PayrollTaxLocality("Karlovačka", "Karlovac", 19.0, 29.0, "RRiF 2026"),
        PayrollTaxLocality("Varaždinska", "Varaždin", 21.0, 32.0, "RRiF 2026"),
        PayrollTaxLocality("Šibensko-kninska", "Šibenik", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Sisačko-moslavačka", "Sisak", 21.6, 31.6, "RRiF 2026"),
        PayrollTaxLocality("Dubrovačko-neretvanska", "Dubrovnik", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Međimurska", "Čakovec", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Bjelovarsko-bilogorska", "Bjelovar", 18.0, 25.0, "RRiF 2026"),
        PayrollTaxLocality("Ličko-senjska", "Gospić", 22.0, 32.0, "RRiF 2026"),
        PayrollTaxLocality("Virovitičko-podravska", "Virovitica", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Požeško-slavonska", "Požega", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Brodsko-posavska", "Slavonski Brod", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Vukovarsko-srijemska", "Vukovar", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Krapinsko-zagorska", "Krapina", 20.0, 30.0, "RRiF 2026"),
        PayrollTaxLocality("Koprivničko-križevačka", "Koprivnica", 20.0, 30.0, "RRiF 2026")
    )

    val regimes = listOf(
        PayrollRegime(
            "public-health", "Zdravstvo", "Javno zdravstvo", "public",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, null),
            "Turnus i druga posebna prava mogu ovisiti o granskom ili ustanovnom pravilu."
        ),
        PayrollRegime(
            "kbc-rijeka-2026", "Zdravstvo", "KBC Rijeka — javne službe 2026", "public",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, null),
            "Dodaci za noć, prekovremeni rad, subotu, nedjelju i blagdan prate TKU javnih službi NN 29/2024. Zaseban postotni dodatak za turnus ne primjenjuje se bez važećeg granskog ili ustanovnog izvora; rad u drugoj smjeni obračunava se prema stvarno odrađenim satima."
        ),
        PayrollRegime(
            "public-education", "Školstvo i obrazovanje", "Škole i učenički domovi", "public",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, null)
        ),
        PayrollRegime(
            "state-service", "Državna služba", "Državna služba / ministarstva", "state",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, 0.05)
        ),
        PayrollRegime(
            "police", "Policija", "MUP / policija", "state",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, 0.05),
            "Policija ima velik broj službenih naziva radnih mjesta; odaberi točan naziv ili koristi ručni unos."
        ),
        PayrollRegime(
            "firefighter", "Vatrogastvo", "Profesionalno vatrogastvo", "state",
            PayrollRates(),
            "Pravilnik propisuje koeficijente/minimalni okvir, dok kolektivni ugovor ili akt postrojbe može biti povoljniji."
        ),
        PayrollRegime(
            "social-care", "Socijalna skrb", "Javna socijalna skrb", "public",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, null)
        ),
        PayrollRegime(
            "culture", "Kultura", "Javne ustanove u kulturi", "public",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, null)
        ),
        PayrollRegime(
            "science", "Znanost i visoko obrazovanje", "Javne ustanove znanosti i visokog obrazovanja", "public",
            PayrollRates(0.40, 0.50, 0.25, 0.50, 1.50, 0.10, null)
        ),
        PayrollRegime(
            "preschool-local", "Vrtići", "Gradski/općinski vrtić", "manual", PayrollRates(),
            "Osnovica i koeficijent ovise o osnivaču, kolektivnom ugovoru i aktu ustanove."
        ),
        PayrollRegime(
            "local-government", "Lokalna i regionalna uprava", "Grad/općina/županija", "manual", PayrollRates(),
            "Osnovicu i koeficijent utvrđuju lokalni kolektivni ugovor i akti jedinice."
        ),
        PayrollRegime(
            "private-manual", "Privatni sektor", "Privatni poslodavac / ručni obračunski parametri", "manual", PayrollRates(),
            "Privatni sektor nema jedinstvenu državnu osnovicu ni koeficijent. Unesi ugovorenu bruto osnovicu/koeficijent i dodatke samo kada su poznati iz ugovora, pravilnika ili kolektivnog ugovora."
        ),
        PayrollRegime(
            "other-public", "Ostalo", "Druga ustanova / poslodavac — ručni unos", "manual", PayrollRates()
        )
    )

    val roles = listOf(
        PayrollRole("health-transport-sss", setOf("public-health","kbc-rijeka-2026"), "Nosač bolesnika / transportni radnik — Radnik III. vrste", "Radnik III. vrste", "10.1.23", 1.25),
        PayrollRole("health-transport-nss", setOf("public-health","kbc-rijeka-2026"), "Nosač bolesnika / pomoćni radnik — posebni uvjeti", "Pomoćni radnik u sustavu s posebnim uvjetima rada", "10.1.24", 1.15),
        PayrollRole("health-portir", setOf("public-health","kbc-rijeka-2026"), "Portir / stručni radnik na tehničkom održavanju", "Stručni radnik na tehničkom održavanju", "10.1.20", 1.39),
        PayrollRole("health-cleaner-special", setOf("public-health","kbc-rijeka-2026"), "Spremač/čistač — posebni uvjeti", "Čistač – spremač u sustavu s posebnim uvjetima rada", "10.1.25", 1.15),
        PayrollRole("health-cleaner", setOf("public-health","kbc-rijeka-2026"), "Spremač/čistač", "Čistač – spremač", "10.1.26", 1.06),
        PayrollRole("health-caregiver", setOf("public-health","kbc-rijeka-2026"), "Njegovatelj / njegovateljica", "Njegovatelj", "16.15.1", 1.35),
        PayrollRole("health-bolnicar", setOf("public-health","kbc-rijeka-2026"), "Bolničar / bolničarka", "Bolničar", "16.15.2", 1.35),
        PayrollRole("health-nurse-sss-1", setOf("public-health","kbc-rijeka-2026"), "Medicinska sestra/tehničar — bolnica 1", "Medicinska sestra/medicinski tehničar / zdravstveni radnik u bolnici 1", "16.13.1", 1.78),
        PayrollRole("health-nurse-sss-2", setOf("public-health","kbc-rijeka-2026"), "Medicinska sestra/tehničar — bolnica 2", "Medicinska sestra/medicinski tehničar / zdravstveni radnik u bolnici 2", "16.13.2", 1.70),
        PayrollRole("health-nurse-bacc-1", setOf("public-health","kbc-rijeka-2026"), "Viša medicinska sestra / prvostupnik sestrinstva — bolnica 1", "Zdravstveni radnik prvostupnik u bolnici 1", "16.11.1", 1.95),
        PayrollRole("health-nurse-bacc-2", setOf("public-health","kbc-rijeka-2026"), "Viša medicinska sestra / prvostupnik sestrinstva — bolnica 2", "Zdravstveni radnik prvostupnik u bolnici 2", "16.11.2", 1.87),
        PayrollRole("health-nurse-master", setOf("public-health","kbc-rijeka-2026"), "Magistra sestrinstva / dipl. medicinska sestra — posebni poslovi", "Magistra sestrinstva/diplomirana medicinska sestra na propisanim posebnim poslovima", "16.10.1", 2.45),
        PayrollRole("health-physio-1", setOf("public-health","kbc-rijeka-2026"), "Viši fizioterapeut / prvostupnik fizioterapije — bolnica 1", "Zdravstveni radnik prvostupnik u bolnici 1", "16.11.1", 1.95),
        PayrollRole("health-physio-2", setOf("public-health","kbc-rijeka-2026"), "Viši fizioterapeut / prvostupnik fizioterapije — bolnica 2", "Zdravstveni radnik prvostupnik u bolnici 2", "16.11.2", 1.87),
        PayrollRole("health-doctor-1", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine — 1", "Doktor medicine i doktor dentalne medicine 1", "16.4.3", 2.92),
        PayrollRole("health-doctor-2", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine — 2", "Doktor medicine i doktor dentalne medicine 2", "16.4.6", 2.83),
        PayrollRole("health-doctor-3", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine — 3", "Doktor medicine i doktor dentalne medicine 3", "16.4.9", 2.81),
        PayrollRole("health-doctor-specialization", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine na specijalizaciji", "Doktor medicine/dentalne medicine na specijalizaciji", "16.4.10", 2.81),
        PayrollRole("health-doctor-specialist-1", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine specijalist — 1", "Doktor medicine specijalist 1", "16.4.2", 3.82),
        PayrollRole("health-doctor-specialist-2", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine specijalist — 2", "Doktor medicine specijalist 2", "16.4.5", 3.74),
        PayrollRole("health-doctor-specialist-3", setOf("public-health","kbc-rijeka-2026"), "Doktor medicine specijalist — 3", "Doktor medicine specijalist 3", "16.4.8", 3.65),

        PayrollRole("edu-teacher", setOf("public-education"), "Učitelj", "Učitelj", "12.5.22", 2.01),
        PayrollRole("edu-secondary-teacher", setOf("public-education"), "Nastavnik", "Nastavnik", "12.5.21", 2.01),
        PayrollRole("edu-educator", setOf("public-education"), "Odgajatelj u učeničkom domu", "Odgajatelj", "12.5.20", 2.01),
        PayrollRole("edu-professional", setOf("public-education"), "Stručni suradnik", "Stručni suradnik", "12.5.23", 2.01),
        PayrollRole("edu-mentor", setOf("public-education"), "Učitelj/nastavnik/odgajatelj — mentor", "Mentor", "12.5.15–18", 2.17),
        PayrollRole("edu-adviser", setOf("public-education"), "Učitelj/nastavnik/odgajatelj — savjetnik", "Savjetnik", "12.5.8–11", 2.38),
        PayrollRole("edu-secretary-1", setOf("public-education"), "Tajnik školske ustanove 1", "Tajnik školske ustanove 1", "12.5.25", 2.01),
        PayrollRole("edu-secretary-2", setOf("public-education"), "Tajnik školske ustanove 2", "Tajnik školske ustanove 2", "12.5.32", 1.77),
        PayrollRole("edu-accounting-1", setOf("public-education"), "Voditelj računovodstva u školi 1", "Voditelj računovodstva u školi 1", "12.5.26", 2.01),
        PayrollRole("edu-night-watch", setOf("public-education"), "Noćni pazitelj u učeničkom domu", "Noćni pazitelj u učeničkom domu", "12.5.38", 1.30),

        PayrollRole("state-senior-adviser", setOf("state-service"), "Viši savjetnik", "Viši savjetnik", "JRM", 2.10),
        PayrollRole("state-associate", setOf("state-service"), "Suradnik", "Suradnik", "JRM", 1.80),
        PayrollRole("state-senior-referent", setOf("state-service"), "Viši referent", "Viši referent", "JRM", 1.70),
        PayrollRole("state-it-technician", setOf("state-service"), "Informatički tehničar", "Informatički tehničar", "JRM", 1.50),
        PayrollRole("state-admin-secretary", setOf("state-service"), "Administrativni tajnik čelnika tijela", "Administrativni tajnik čelnika tijela", "JRM", 1.44),
        PayrollRole("state-referent", setOf("state-service"), "Referent", "Referent", "JRM", 1.43),
        PayrollRole("state-driver", setOf("state-service"), "Vozač", "Vozač", "JRM", 1.37),
        PayrollRole("state-employee-iii", setOf("state-service"), "Namještenik III. vrste", "Namještenik – III. vrste", "JRM", 1.25),
        PayrollRole("state-caretaker", setOf("state-service"), "Domar", "Domar", "JRM", 1.25),
        PayrollRole("state-doorman", setOf("state-service"), "Portir", "Portir", "JRM", 1.06),
        PayrollRole("state-cleaner", setOf("state-service"), "Spremač", "Spremač", "JRM", 1.06),

        PayrollRole("police-station", setOf("police"), "Policijski službenik u policijskoj postaji", "Policijski službenik u policijskoj postaji", "MUP", 1.70),
        PayrollRole("police-intervention", setOf("police"), "Policijski službenik interventne policije", "Policijski službenik interventne policije", "MUP", 1.70),
        PayrollRole("police-contact", setOf("police"), "Kontakt policajac", "Kontakt policajac", "MUP", 1.70),
        PayrollRole("police-patrol-lead", setOf("police"), "Vođa ophodnje u policijskoj postaji", "Vođa ophodnje u policijskoj postaji", "MUP", 1.65),
        PayrollRole("police-border", setOf("police"), "Policijski službenik granične policije", "Policijski službenik granične policije", "MUP", 1.65),
        PayrollRole("police-motorcycle", setOf("police"), "Policijski službenik — motociklist", "Policijski službenik – motociklist", "MUP", 1.75),
        PayrollRole("police-dispatcher", setOf("police"), "Policijski službenik — dispečer", "Policijski službenik – dispečer", "MUP", 1.75),

        PayrollRole("firefighter", setOf("firefighter"), "Vatrogasac", "Vatrogasac", "Prilog 1", 1.10),
        PayrollRole("firefighter-specialist", setOf("firefighter"), "Vatrogasac specijalist", "Vatrogasac specijalist", "Prilog 1", 1.16),
        PayrollRole("firefighter-senior-specialist", setOf("firefighter"), "Stariji vatrogasac specijalist", "Stariji vatrogasac specijalist", "Prilog 1", 1.20),
        PayrollRole("firefighter-driver", setOf("firefighter"), "Vatrogasac — vozač", "Vatrogasac – vozač", "Prilog 1", 1.22),
        PayrollRole("firefighter-group-lead", setOf("firefighter"), "Voditelj vatrogasne grupe", "Voditelj vatrogasne grupe", "Prilog 1", 1.24),
        PayrollRole("firefighter-group-driver", setOf("firefighter"), "Voditelj vatrogasne grupe — vozač", "Voditelj vatrogasne grupe – vozač", "Prilog 1", 1.25),
        PayrollRole("firefighter-section-lead", setOf("firefighter"), "Voditelj vatrogasnog odjeljenja", "Voditelj vatrogasnog odjeljenja", "Prilog 1", 1.30),

        PayrollRole("manual", regimes.map { it.id }.toSet(), "Drugo / ručni unos", "Drugo radno mjesto", "ručno", null)
    )

    val institutions = listOf(
        PayrollInstitution("Primorsko-goranska","Rijeka","Klinički bolnički centar Rijeka","kbc-rijeka-2026","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinički bolnički centar Zagreb","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinički bolnički centar Sestre milosrdnice","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinička bolnica Dubrava","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinička bolnica Merkur","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinička bolnica Sveti Duh","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinika za infektivne bolesti Dr. Fran Mihaljević","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinika za psihijatriju Vrapče","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinika za dječje bolesti Zagreb","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Psihijatrijska bolnica za djecu i mladež","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Specijalna bolnica za zaštitu djece s neurorazvojnim i motoričkim smetnjama","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Specijalna bolnica za plućne bolesti","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Klinika za psihijatriju Sveti Ivan","public-health","Zdravstvo"),
        PayrollInstitution("Grad Zagreb","Zagreb","Dječja bolnica Srebrnjak","public-health","Zdravstvo"),
        PayrollInstitution("Zagrebačka","Ivanić-Grad","Naftalan — Specijalna bolnica za medicinsku rehabilitaciju","public-health","Zdravstvo"),
        PayrollInstitution("Zagrebačka","Gornja Bistra","Specijalna bolnica za kronične bolesti dječje dobi Gornja Bistra","public-health","Zdravstvo"),
        PayrollInstitution("Krapinsko-zagorska","Krapinske Toplice","Magdalena — Klinika za kardiovaskularne bolesti","public-health","Zdravstvo"),
        PayrollInstitution("Krapinsko-zagorska","Krapinske Toplice","Specijalna bolnica za medicinsku rehabilitaciju Krapinske Toplice","public-health","Zdravstvo"),
        PayrollInstitution("Krapinsko-zagorska","Stubičke Toplice","Specijalna bolnica za medicinsku rehabilitaciju Stubičke Toplice","public-health","Zdravstvo"),
        PayrollInstitution("Krapinsko-zagorska","Zabok","Opća bolnica Zabok i bolnica hrvatskih veterana","public-health","Zdravstvo"),
        PayrollInstitution("Sisačko-moslavačka","Sisak","Opća bolnica Dr. Ivo Pedišić Sisak","public-health","Zdravstvo"),
        PayrollInstitution("Sisačko-moslavačka","Popovača","Neuropsihijatrijska bolnica Dr. Ivan Barbot Popovača","public-health","Zdravstvo"),
        PayrollInstitution("Sisačko-moslavačka","Topusko","Lječilište Topusko","public-health","Zdravstvo"),
        PayrollInstitution("Karlovačka","Karlovac","Opća bolnica Karlovac","public-health","Zdravstvo"),
        PayrollInstitution("Karlovačka","Ogulin","Opća bolnica i bolnica branitelja Domovinskog rata Ogulin","public-health","Zdravstvo"),
        PayrollInstitution("Karlovačka","Duga Resa","Specijalna bolnica za produženo liječenje Duga Resa","public-health","Zdravstvo"),
        PayrollInstitution("Varaždinska","Varaždin","Opća bolnica Varaždin","public-health","Zdravstvo"),
        PayrollInstitution("Varaždinska","Varaždinske Toplice","Specijalna bolnica za medicinsku rehabilitaciju Varaždinske Toplice","public-health","Zdravstvo"),
        PayrollInstitution("Koprivničko-križevačka","Koprivnica","Opća bolnica Dr. Tomislav Bardek Koprivnica","public-health","Zdravstvo"),
        PayrollInstitution("Bjelovarsko-bilogorska","Bjelovar","Opća bolnica Bjelovar","public-health","Zdravstvo"),
        PayrollInstitution("Bjelovarsko-bilogorska","Daruvar","Daruvarske Toplice — Specijalna bolnica za medicinsku rehabilitaciju","public-health","Zdravstvo"),
        PayrollInstitution("Primorsko-goranska","Lovran","Klinika za ortopediju Lovran","public-health","Zdravstvo"),
        PayrollInstitution("Primorsko-goranska","Opatija","Thalassotherapia Opatija","public-health","Zdravstvo"),
        PayrollInstitution("Primorsko-goranska","Crikvenica","Thalassotherapia Crikvenica","public-health","Zdravstvo"),
        PayrollInstitution("Primorsko-goranska","Rab","Insula — županijska specijalna bolnica za psihijatriju i rehabilitaciju","public-health","Zdravstvo"),
        PayrollInstitution("Primorsko-goranska","Dražice","Psihijatrijska bolnica Lopača","public-health","Zdravstvo"),
        PayrollInstitution("Primorsko-goranska","Veli Lošinj","Lječilište Veli Lošinj","public-health","Zdravstvo"),
        PayrollInstitution("Ličko-senjska","Gospić","Opća bolnica Gospić","public-health","Zdravstvo"),
        PayrollInstitution("Virovitičko-podravska","Virovitica","Opća bolnica Virovitica","public-health","Zdravstvo"),
        PayrollInstitution("Požeško-slavonska","Požega","Opća županijska bolnica Požega","public-health","Zdravstvo"),
        PayrollInstitution("Požeško-slavonska","Pakrac","Opća županijska bolnica Pakrac i bolnica hrvatskih veterana","public-health","Zdravstvo"),
        PayrollInstitution("Požeško-slavonska","Lipik","Toplice Lipik — Specijalna bolnica za medicinsku rehabilitaciju","public-health","Zdravstvo"),
        PayrollInstitution("Brodsko-posavska","Slavonski Brod","Opća bolnica Dr. Josip Benčević Slavonski Brod","public-health","Zdravstvo"),
        PayrollInstitution("Brodsko-posavska","Nova Gradiška","Opća bolnica Nova Gradiška","public-health","Zdravstvo"),
        PayrollInstitution("Brodsko-posavska","Cernik","Specijalna bolnica za psihijatriju i palijativnu skrb Sveti Rafael Strmac","public-health","Zdravstvo"),
        PayrollInstitution("Zadarska","Zadar","Opća bolnica Zadar","public-health","Zdravstvo"),
        PayrollInstitution("Zadarska","Biograd na Moru","Specijalna bolnica za ortopediju Biograd na Moru","public-health","Zdravstvo"),
        PayrollInstitution("Zadarska","Ugljan","Psihijatrijska bolnica Ugljan","public-health","Zdravstvo"),
        PayrollInstitution("Osječko-baranjska","Osijek","Klinički bolnički centar Osijek","public-health","Zdravstvo"),
        PayrollInstitution("Osječko-baranjska","Našice","Opća županijska bolnica Našice","public-health","Zdravstvo"),
        PayrollInstitution("Šibensko-kninska","Šibenik","Opća bolnica Šibensko-kninske županije","public-health","Zdravstvo"),
        PayrollInstitution("Šibensko-kninska","Knin","Opća i veteranska bolnica Hrvatski ponos Knin","public-health","Zdravstvo"),
        PayrollInstitution("Vukovarsko-srijemska","Vinkovci","Opća županijska bolnica Vinkovci","public-health","Zdravstvo"),
        PayrollInstitution("Vukovarsko-srijemska","Vukovar","Nacionalna memorijalna bolnica Dr. Juraj Njavro Vukovar","public-health","Zdravstvo"),
        PayrollInstitution("Splitsko-dalmatinska","Split","Klinički bolnički centar Split","public-health","Zdravstvo"),
        PayrollInstitution("Splitsko-dalmatinska","Makarska","Biokovka — Specijalna bolnica za medicinsku rehabilitaciju","public-health","Zdravstvo"),
        PayrollInstitution("Istarska","Pula","Opća bolnica Pula","public-health","Zdravstvo"),
        PayrollInstitution("Istarska","Rovinj","Specijalna bolnica za ortopediju i rehabilitaciju Martin Horvat Rovinj-Rovigno","public-health","Zdravstvo"),
        PayrollInstitution("Dubrovačko-neretvanska","Dubrovnik","Opća bolnica Dubrovnik","public-health","Zdravstvo"),
        PayrollInstitution("Dubrovačko-neretvanska","Vela Luka","Specijalna bolnica za medicinsku rehabilitaciju Kalos","public-health","Zdravstvo"),
        PayrollInstitution("Međimurska","Čakovec","Županijska bolnica Čakovec","public-health","Zdravstvo"),
        PayrollInstitution("*","*","Osnovna škola (odaberi županiju / ručni naziv)","public-education","Školstvo i obrazovanje"),
        PayrollInstitution("*","*","Srednja škola (odaberi županiju / ručni naziv)","public-education","Školstvo i obrazovanje"),
        PayrollInstitution("*","*","Učenički dom (odaberi županiju / ručni naziv)","public-education","Školstvo i obrazovanje"),
        PayrollInstitution("*","*","MUP / policijska uprava ili postaja","police","Policija"),
        PayrollInstitution("*","*","Javna vatrogasna postrojba","firefighter","Vatrogastvo"),
        PayrollInstitution("*","*","Gradski/općinski vrtić — ručni naziv","preschool-local","Vrtići"),
        PayrollInstitution("*","*","Županijska uprava","local-government","Lokalna i regionalna uprava"),
        PayrollInstitution("*","*","Gradska uprava","local-government","Lokalna i regionalna uprava"),
        PayrollInstitution("*","*","Općinska uprava","local-government","Lokalna i regionalna uprava"),
        PayrollInstitution("*","*","Ministarstvo / državno tijelo — ručni naziv","state-service","Državna služba"),
        PayrollInstitution("*","*","Javna ustanova socijalne skrbi — ručni naziv","social-care","Socijalna skrb"),
        PayrollInstitution("*","*","Javna ustanova u kulturi — ručni naziv","culture","Kultura"),
        PayrollInstitution("*","*","Javna visokoškolska/znanstvena ustanova — ručni naziv","science","Znanost i visoko obrazovanje"),
        PayrollInstitution("*","*","Druga javna ustanova — ručni unos","other-public","Ostalo")
    )

    val sectors: List<String>
        get() = regimes.map { it.sector }.distinct()

    fun regime(id: String): PayrollRegime =
        regimes.firstOrNull { it.id == id } ?: regimes.last()

    fun defaultRegimeForSector(sector: String): PayrollRegime =
        regimes.firstOrNull { it.sector == sector } ?: regimes.last()

    fun institutionsFor(sector: String, county: String): List<PayrollInstitution> =
        institutions.filter { it.sector == sector && (it.county == county || it.county == "*") }

    fun rolesFor(regimeId: String): List<PayrollRole> =
        roles.filter { regimeId in it.regimes }

    fun role(id: String, regimeId: String): PayrollRole =
        rolesFor(regimeId).firstOrNull { it.id == id } ?: rolesFor(regimeId).firstOrNull() ?: roles.last()

    fun baseFor(month: YearMonth, baseType: String): Double {
        if (month.year != YEAR || baseType == "manual") return 0.0
        return when {
            month < YearMonth.of(YEAR, 4) -> 1004.87
            month < YearMonth.of(YEAR, 8) -> 1015.00
            month < YearMonth.of(YEAR, 12) -> 1025.00
            else -> 1035.00
        }
    }

    fun monthlyFundHours(month: YearMonth): Int =
        (1..month.lengthOfMonth()).count { day ->
            month.atDay(day).dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        } * 8

    fun estimate(
        month: YearMonth,
        scheduleCodes: Map<String, String> = emptyMap(),
        regimeId: String,
        coefficient: Double,
        yearsService: Int,
        personalAllowance: Double,
        taxLower: Double,
        taxHigher: Double,
        extraPercent: Double,
        secondShift: Boolean,
        turnus: Boolean,
        customBase: Double? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): PayrollEstimate {
        val regime = regime(regimeId)
        val base = customBase?.takeIf { it > 0.0 } ?: baseFor(month, regime.baseType)
        val safeCoefficient = coefficient.coerceIn(0.1, 10.0)
        val safeYears = yearsService.coerceIn(0, 60)
        val fundHours = monthlyFundHours(month)
        val basicGross = base * safeCoefficient * (1.0 + safeYears * SENIORITY_PER_YEAR)
        val hourly = if (fundHours > 0) basicGross / fundHours else 0.0
        val evidence = summarizeEvidence(month, scheduleCodes, secondShift, turnus, zone)
        val rates = regime.rates
        fun add(minutes: Long, rate: Double?): Double =
            if (rate == null) 0.0 else hourly * (minutes / 60.0) * rate

        val turnusMinutes =
            if (turnus && rates.turnus != null) evidence.turnusMinutes else 0L
        val secondMinutes = if (secondShift && rates.secondShift != null) {
            when {
                turnusMinutes > 0L -> 0L
                evidence.shift2Minutes > 0L -> evidence.shift2Minutes
                else -> evidence.secondShiftMinutes
            }
        } else {
            0L
        }
        val custom = basicGross * (extraPercent.coerceIn(0.0, 100.0) / 100.0)

        val nightAddition = add(evidence.nightMinutes, rates.night)
        val saturdayAddition = add(evidence.saturdayMinutes, rates.saturday)
        val sundayAddition = add(evidence.sundayMinutes, rates.sunday)
        val holidayAddition = add(evidence.holidayMinutes, rates.holiday)
        val overtimeBasePay = hourly * (evidence.overtimeMinutes / 60.0)
        val overtimeAddition = add(evidence.overtimeMinutes, rates.overtime)
        val secondShiftAddition = add(secondMinutes, rates.secondShift)
        val turnusAddition = add(turnusMinutes, rates.turnus)
        val additions = nightAddition + saturdayAddition + sundayAddition + holidayAddition +
            overtimeBasePay + overtimeAddition + secondShiftAddition + turnusAddition + custom
        val gross = basicGross + additions
        val pension = gross * PENSION_CONTRIBUTION
        val taxable = (gross - pension - personalAllowance.coerceIn(0.0, 10_000.0)).coerceAtLeast(0.0)
        val lowerPart = taxable.coerceAtMost(MONTHLY_TAX_THRESHOLD)
        val higherPart = (taxable - MONTHLY_TAX_THRESHOLD).coerceAtLeast(0.0)
        val tax = lowerPart * (taxLower.coerceIn(0.0, 50.0) / 100.0) +
            higherPart * (taxHigher.coerceIn(0.0, 50.0) / 100.0)
        val net = (gross - pension - tax).coerceAtLeast(0.0)
        val fallbackDays = (fundHours / 8).coerceAtLeast(1)
        val workedDays = evidence.workedDays.takeIf { it > 0 } ?: fallbackDays

        return PayrollEstimate(
            base = base,
            coefficient = safeCoefficient,
            monthlyFundHours = fundHours,
            basicGross = basicGross,
            hourlyGross = hourly,
            evidence = evidence,
            nightAddition = nightAddition,
            saturdayAddition = saturdayAddition,
            sundayAddition = sundayAddition,
            holidayAddition = holidayAddition,
            overtimeBasePay = overtimeBasePay,
            overtimeAddition = overtimeAddition,
            secondShiftPaidMinutes = secondMinutes,
            turnusPaidMinutes = turnusMinutes,
            secondShiftAddition = secondShiftAddition,
            turnusAddition = turnusAddition,
            customAddition = custom,
            pensionContribution = pension,
            incomeTax = tax,
            estimatedNet = net,
            dailyGross = if (workedDays > 0) gross / workedDays else 0.0,
            dailyNet = if (workedDays > 0) net / workedDays else 0.0
        )
    }

    private fun summarizeEvidence(
        month: YearMonth,
        scheduleCodes: Map<String, String>,
        secondShiftEnabled: Boolean,
        turnusEnabled: Boolean,
        zone: ZoneId
    ): PayrollEvidence {
        val holidays = CroatianHolidays.forYear(month.year)
        val workedDates = mutableSetOf<java.time.LocalDate>()
        var worked = 0L
        var night = 0L
        var saturday = 0L
        var sunday = 0L
        var holiday = 0L
        var second = 0L
        var shift1 = 0L
        var shift2 = 0L
        var shift3 = 0L

        fun accountShift(date: java.time.LocalDate, code: String) {
            val start = when (code) {
                "D" -> date.atTime(7, 0).atZone(zone)
                "N" -> date.atTime(19, 0).atZone(zone)
                else -> return
            }
            val end = when (code) {
                "D" -> date.atTime(19, 0).atZone(zone)
                else -> date.plusDays(1).atTime(7, 0).atZone(zone)
            }
            workedDates += date
            var cursor = start
            while (cursor.isBefore(end)) {
                val localDate = cursor.toLocalDate()
                if (YearMonth.from(localDate) == month) {
                    worked++
                    val hour = cursor.hour
                    if (hour >= 22 || hour < 6) night++
                    if (localDate.dayOfWeek == DayOfWeek.SATURDAY) saturday++
                    if (localDate.dayOfWeek == DayOfWeek.SUNDAY) sunday++
                    if (holidays.containsKey(localDate)) holiday++
                    if (hour in 14..21) second++
                    if (code == "D") shift1++ else shift3++
                }
                cursor = cursor.plusMinutes(1)
            }
        }

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            when (scheduleCodes[date.toString()].orEmpty()) {
                "D" -> accountShift(date, "D")
                "N" -> accountShift(date, "N")
            }
        }
        // A night shift starting on the last day of the previous month can
        // contribute hours to this month.
        val previousDate = month.atDay(1).minusDays(1)
        if (scheduleCodes[previousDate.toString()] == "N") {
            accountShift(previousDate, "N")
        }

        if (secondShiftEnabled) shift2 = second
        val turnus = if (turnusEnabled) worked else 0L

        var goDays = 0
        var boDays = 0
        var pdDays = 0
        var sdDays = 0
        val compensatedAbsenceDates = mutableSetOf<java.time.LocalDate>()
        scheduleCodes.forEach { (dateText, code) ->
            val date = runCatching { java.time.LocalDate.parse(dateText) }.getOrNull()
                ?: return@forEach
            if (YearMonth.from(date) != month) return@forEach
            when (code) {
                "GO" -> {
                    goDays++
                    compensatedAbsenceDates += date
                }
                "BO" -> {
                    boDays++
                    compensatedAbsenceDates += date
                }
                "PD" -> {
                    pdDays++
                    compensatedAbsenceDates += date
                }
                "SD" -> sdDays++
            }
        }

        val holidayCompensatedDates = (1..month.lengthOfMonth())
            .map(month::atDay)
            .filter { date ->
                date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
                    holidays.containsKey(date) &&
                    date !in workedDates &&
                    date !in compensatedAbsenceDates
            }
            .toSet()

        val compensatedAbsenceMinutes = compensatedAbsenceDates.size * 8L * 60L
        val holidayCompensatedMinutes = holidayCompensatedDates.size * 8L * 60L
        val overtime = (
            worked +
                compensatedAbsenceMinutes +
                holidayCompensatedMinutes -
                monthlyFundHours(month) * 60L
        ).coerceAtLeast(0L)

        return PayrollEvidence(
            workedMinutes = worked,
            nightMinutes = night,
            saturdayMinutes = saturday,
            sundayMinutes = sunday,
            holidayMinutes = holiday,
            secondShiftMinutes = second,
            shift1Minutes = shift1,
            shift2Minutes = shift2,
            shift3Minutes = shift3,
            turnusMinutes = turnus,
            dutyMinutes = 0L,
            standbyMinutes = 0L,
            calloutMinutes = 0L,
            compensatedAbsenceMinutes = compensatedAbsenceMinutes,
            holidayCompensatedMinutes = holidayCompensatedMinutes,
            holidayCompensatedDays = holidayCompensatedDates.size,
            goDays = goDays,
            boDays = boDays,
            pdDays = pdDays,
            sdDays = sdDays,
            overtimeMinutes = overtime,
            workedDays = workedDates.count { YearMonth.from(it) == month },
            hasActiveEntry = false
        )
    }
}
