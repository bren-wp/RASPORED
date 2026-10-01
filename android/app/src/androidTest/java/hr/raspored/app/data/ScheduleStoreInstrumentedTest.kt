package hr.raspored.app.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleStoreInstrumentedTest {

    @Test
    fun savingOneMonthPreservesOlderAndNewerSchedulesInSqlite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetStore(context)

        try {
            val store = ScheduleStore(context)
            val current = YearMonth.of(2026, 10)
            val older = current.minusYears(ScheduleStore.ARCHIVE_GUARANTEE_YEARS.toLong())
            val newer = current.plusYears(5)

            store.saveMonth(older, mapOf(1 to "D", 2 to "N", 3 to "J"))
            store.saveMonth(newer, mapOf(7 to "PD", 8 to "P1"))
            store.saveMonth(current, mapOf(1 to "GO", 2 to "SD"))

            // Remove the compatibility mirror to prove SQLite is the source of truth.
            legacyPreferences(context).edit().clear().commit()

            val loaded = ScheduleStore(context).load()
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
            resetStore(context)
        }
    }

    @Test
    fun migratesLegacySharedPreferencesWithoutDataLoss() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetStore(context)

        try {
            val legacy = legacyPreferences(context)
            legacy.edit()
                .putString("2016-01-01", "D")
                .putString("2026-10-17", "N")
                .putString("2036-12-31", "G0")
                .commit()

            val migrated = ScheduleStore(context).load()
            assertEquals("D", migrated["2016-01-01"])
            assertEquals("N", migrated["2026-10-17"])
            assertEquals("GO", migrated["2036-12-31"])

            // If the legacy file disappears after a successful upgrade, SQLite
            // still has the complete migrated history.
            legacy.edit().clear().commit()
            val reopened = ScheduleStore(context).load()
            assertEquals("D", reopened["2016-01-01"])
            assertEquals("N", reopened["2026-10-17"])
            assertEquals("GO", reopened["2036-12-31"])
        } finally {
            resetStore(context)
        }
    }

    private fun resetStore(context: Context) {
        legacyPreferences(context).edit().clear().commit()
        context.deleteDatabase(ScheduleStore.DATABASE_NAME)
    }

    private fun legacyPreferences(context: Context) =
        context.getSharedPreferences("raspored.schedule", Context.MODE_PRIVATE)
}
