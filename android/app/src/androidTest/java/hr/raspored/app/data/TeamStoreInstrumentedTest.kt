package hr.raspored.app.data

import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamStoreInstrumentedTest {

    @Test
    fun teamImportPreservesCustomCodesAndOlderMonths() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            "raspored.team",
            android.content.Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()

        try {
            val store = TeamStore(context)
            val oldMonth = YearMonth.of(2016, 10)
            val recentMonth = YearMonth.of(2026, 10)

            store.saveRecognizedMonth(
                oldMonth,
                listOf(
                    "Ana Horvat" to mapOf(1 to "J", 2 to "S", 3 to "P1")
                )
            )
            store.saveRecognizedMonth(
                recentMonth,
                listOf(
                    "Ana Horvat" to mapOf(1 to "D", 2 to "N", 3 to "GO")
                )
            )

            val member = store.load().single()
            assertEquals("J", member.schedule[oldMonth.atDay(1).toString()])
            assertEquals("S", member.schedule[oldMonth.atDay(2).toString()])
            assertEquals("P1", member.schedule[oldMonth.atDay(3).toString()])
            assertEquals("D", member.schedule[recentMonth.atDay(1).toString()])
            assertEquals("N", member.schedule[recentMonth.atDay(2).toString()])
            assertTrue(member.schedule.keys.any { it.startsWith(oldMonth.toString()) })
            assertTrue(member.schedule.keys.any { it.startsWith(recentMonth.toString()) })
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
