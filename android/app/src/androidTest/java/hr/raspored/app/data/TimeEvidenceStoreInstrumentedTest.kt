package hr.raspored.app.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeEvidenceStoreInstrumentedTest {

    @Test
    fun preservesMoreThanTenYearsOfDailyEvidenceWithoutPruning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            "raspored.time_evidence",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()

        try {
            val day = 24L * 60L * 60L * 1000L
            val start = 1_451_606_400_000L // 2016-01-01T00:00:00Z
            val entries = List(3_660) { index ->
                val startedAt = start + index * day
                TimeEvidenceEntry(
                    id = startedAt,
                    startedAt = startedAt,
                    endedAt = startedAt + 8L * 60L * 60L * 1000L,
                    note = "Test zapis $index",
                    workType = WorkType.REGULAR
                )
            }

            TimeEvidenceStore(context).replaceAll(entries)

            val reloaded = TimeEvidenceStore(context).load()
            assertEquals(3_660, reloaded.size)
            assertEquals(entries.first().startedAt, reloaded.first().startedAt)
            assertEquals(entries.last().startedAt, reloaded.last().startedAt)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
