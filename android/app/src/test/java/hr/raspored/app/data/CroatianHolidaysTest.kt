package hr.raspored.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CroatianHolidaysTest {
    @Test
    fun calculatesMovableHolidaysFor2026() {
        val holidays = CroatianHolidays.forYear(2026)
        assertEquals("Uskrs", holidays[LocalDate.of(2026, 4, 5)])
        assertEquals("Uskrsni ponedjeljak", holidays[LocalDate.of(2026, 4, 6)])
        assertEquals("Tijelovo", holidays[LocalDate.of(2026, 6, 4)])
    }

    @Test
    fun includesFixedCroatianHolidays() {
        val holidays = CroatianHolidays.forYear(2026)
        assertEquals("Dan državnosti", holidays[LocalDate.of(2026, 5, 30)])
        assertEquals("Božić", holidays[LocalDate.of(2026, 12, 25)])
    }
}
