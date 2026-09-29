package hr.raspored.app.data

import java.time.LocalDate

object CroatianHolidays {
    fun forYear(year: Int): Map<LocalDate, String> = buildMap {
        put(LocalDate.of(year, 1, 1), "Nova godina")
        put(LocalDate.of(year, 1, 6), "Bogojavljenje")
        put(LocalDate.of(year, 5, 1), "Praznik rada")
        put(LocalDate.of(year, 5, 30), "Dan državnosti")
        put(LocalDate.of(year, 6, 22), "Dan antifašističke borbe")
        put(LocalDate.of(year, 8, 5), "Dan pobjede i domovinske zahvalnosti i Dan hrvatskih branitelja")
        put(LocalDate.of(year, 8, 15), "Velika Gospa")
        put(LocalDate.of(year, 11, 1), "Svi sveti")
        put(LocalDate.of(year, 11, 18), "Dan sjećanja na žrtve Domovinskog rata")
        put(LocalDate.of(year, 12, 25), "Božić")
        put(LocalDate.of(year, 12, 26), "Sveti Stjepan")

        val easter = easterSunday(year)
        put(easter, "Uskrs")
        put(easter.plusDays(1), "Uskrsni ponedjeljak")
        put(easter.plusDays(60), "Tijelovo")
    }

    internal fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
