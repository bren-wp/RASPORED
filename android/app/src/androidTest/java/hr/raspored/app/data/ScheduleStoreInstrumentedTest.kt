package hr.raspored.app.data

import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleStoreInstrumentedTest {

    @Test
    fun savingNewMonthPreservesScheduleFromTenYearsEarlier() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            "raspored.schedule",
            android.content.Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()

        try {
            val store = ScheduleStore(context)
            val recent = YearMonth.of(2026, 10)
            val old = recent.minusYears(ScheduleStore.ARCHIVE_GUARANTEE_YEARS.toLong())

            store.saveMonth(old, mapOf(1 to "D", 2 to "N", 3 to "J"))
            store.saveMonth(recent, mapOf(1 to "GO", 2 to "SD"))

            val loaded = store.load()
            assertEquals("D", loaded[old.atDay(1).toString()])
            assertEquals("N", loaded[old.atDay(2).toString()])
            assertEquals("J", loaded[old.atDay(3).toString()])
            assertEquals("GO", loaded[recent.atDay(1).toString()])
            assertEquals("SD", loaded[recent.atDay(2).toString()])
            assertTrue(loaded.keys.any { it.startsWith(old.toString()) })
            assertTrue(loaded.keys.any { it.startsWith(recent.toString()) })
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
