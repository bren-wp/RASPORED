package hr.raspored.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeEvidenceEntryTest {
    @Test
    fun calculatesCompletedDurationInMinutes() {
        val entry = TimeEvidenceEntry(
            id = 1L,
            startedAt = 1_000_000L,
            endedAt = 1_000_000L + 7_500_000L,
            note = ""
        )
        assertEquals(125L, entry.durationMinutes(entry.endedAt!!))
    }

    @Test
    fun activeEntryUsesProvidedCurrentTime() {
        val entry = TimeEvidenceEntry(
            id = 1L,
            startedAt = 1_000_000L,
            endedAt = null,
            note = ""
        )
        assertEquals(90L, entry.durationMinutes(1_000_000L + 5_400_000L))
    }

    @Test
    fun negativeClockSkewCannotProduceNegativeDuration() {
        val entry = TimeEvidenceEntry(
            id = 1L,
            startedAt = 2_000_000L,
            endedAt = 1_000_000L,
            note = ""
        )
        assertEquals(0L, entry.durationMinutes(3_000_000L))
    }
}
