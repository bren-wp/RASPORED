package hr.raspored.app.data

import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleStoreInstrumentedTest {

    @Test
    fun savingOneMonthPreservesOlderAndNewerSchedules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            "raspored.schedule",
            android.content.Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()

        try {
            val store = ScheduleStore(context)
            val current = YearMonth.of(2026, 10)
            val older = current.minusYears(ScheduleStore.ARCHIVE_GUARANTEE_YEARS.toLong())
            val newer = current.plusYears(5)

            store.saveMonth(older, mapOf(1 to "D", 2 to "N", 3 to "J"))
            store.saveMonth(newer, mapOf(7 to "PD", 8 to "P1"))
            store.saveMonth(current, mapOf(1 to "GO", 2 to "SD"))

            val loaded = store.load()
            assertEquals("D", loaded[older.atDay(1).toString()])
            assertEquals("N", loaded[older.atDay(2).toString()])
            assertEquals("J", loaded[older.atDay(3).toString()])
            assertEquals("GO", loaded[current.atDay(1).toString()])
            assertEquals("SD", loaded[current.atDay(2).toString()])
            assertEquals("PD", loaded[newer.atDay(7).toString()])
            assertEquals("P1", loaded[newer.atDay(8).toString()])
            assertTrue(loaded.keys.any { it.startsWith(older.toString()) })
            assertTrue(loaded.keys.any { it.startsWith(current.toString()) })
            assertTrue(loaded.keys.any { it.startsWith(newer.toString()) })
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
