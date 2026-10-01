package hr.raspored.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.raspored.app.data.ScheduleStore
import hr.raspored.app.data.TeamStore
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleHistoryTest {
    private lateinit var context: Context

    @Before
    fun clearStores() {
        context = ApplicationProvider.getApplicationContext()
        resetStores()
    }

    @After
    fun cleanup() {
        resetStores()
    }

    @Test
    fun preservesPersonalSchedulesAcross2016To2036AndMoreThan120Months() {
        val store = ScheduleStore(context)
        val first = YearMonth.of(2016, 1)
        val middle = YearMonth.of(2026, 10)
        val last = YearMonth.of(2036, 12)

        store.saveMonth(first, mapOf(1 to "D"))
        store.saveMonth(middle, mapOf(17 to "N"))
        store.saveMonth(last, mapOf(31 to "GO"))

        val sequenceStart = YearMonth.of(2037, 1)
        repeat(121) { offset ->
            val month = sequenceStart.plusMonths(offset.toLong())
            store.saveMonth(month, mapOf(1 to if (offset % 2 == 0) "D" else "N"))
        }

        // Remove the SharedPreferences compatibility mirror: persistence must
        // continue to work from SQLite alone after process/store recreation.
        context.getSharedPreferences("raspored.schedule", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val reloaded = ScheduleStore(context).load()
        assertEquals("D", reloaded[first.atDay(1).toString()])
        assertEquals("N", reloaded[middle.atDay(17).toString()])
        assertEquals("GO", reloaded[last.atDay(31).toString()])

        repeat(121) { offset ->
            val month = sequenceStart.plusMonths(offset.toLong())
            assertTrue("Missing month $month", reloaded.containsKey(month.atDay(1).toString()))
        }

        // Editing an old month must not remove unrelated future months.
        ScheduleStore(context).saveMonth(first, mapOf(2 to "PD"))
        val edited = ScheduleStore(context).load()
        assertEquals("PD", edited[first.atDay(2).toString()])
        assertEquals("GO", edited[last.atDay(31).toString()])
    }

    @Test
    fun preservesTeamMemberSchedulesAcross2016To2036() {
        val store = TeamStore(context)
        store.saveRecognizedMonth(
            YearMonth.of(2016, 1),
            listOf("Test Osoba" to mapOf(1 to "D"))
        )
        store.saveRecognizedMonth(
            YearMonth.of(2026, 10),
            listOf("Test Osoba" to mapOf(17 to "P1"))
        )
        store.saveRecognizedMonth(
            YearMonth.of(2036, 12),
            listOf("Test Osoba" to mapOf(31 to "N"))
        )

        val member = TeamStore(context).load().single()
        assertEquals("D", member.schedule["2016-01-01"])
        assertEquals("P1", member.schedule["2026-10-17"])
        assertEquals("N", member.schedule["2036-12-31"])
    }

    private fun resetStores() {
        context.getSharedPreferences("raspored.schedule", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase(ScheduleStore.DATABASE_NAME)
        context.getSharedPreferences("raspored.team", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
