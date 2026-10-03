package hr.raspored.app.ui

import hr.raspored.app.ocr.RecognizedSchedule
import hr.raspored.app.ocr.RecognizedScheduleRow
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAiMergeTest {
    private val month = YearMonth.of(2026, 10)

    @Test
    fun leavesSelectedValueEmptyAndCreatesReviewConflictWhenAiDiffers() {
        val local = RecognizedSchedule(
            month = month,
            rows = listOf(RecognizedScheduleRow(1, "Test Osoba", mapOf(17 to "D"))),
            rawText = "lokalno"
        )
        val ai = RecognizedSchedule(
            month = month,
            rows = listOf(RecognizedScheduleRow(1, "Test Osoba", mapOf(17 to "N"))),
            rawText = ""
        )

        val merged = mergeForAiReview(local, ai)

        assertEquals("D", merged.schedule.rows.single().dayShifts[17])
        val cell = merged.cells.single()
        assertEquals(17, cell.day)
        assertEquals("D", cell.localCode)
        assertEquals("N", cell.aiCode)
        assertNull(cell.selectedCode)
        assertTrue(cell.conflict)
        assertFalse(cell.manuallyConfirmed)
    }

    @Test
    fun identicalLocalAndAiValueIsNotAConflict() {
        val local = RecognizedSchedule(
            month = month,
            rows = listOf(RecognizedScheduleRow(2, "Druga Osoba", mapOf(31 to "GO"))),
            rawText = "lokalno"
        )
        val ai = RecognizedSchedule(
            month = month,
            rows = listOf(RecognizedScheduleRow(2, "Druga Osoba", mapOf(31 to "GO"))),
            rawText = ""
        )

        val merged = mergeForAiReview(local, ai)

        assertEquals("GO", merged.schedule.rows.single().dayShifts[31])
        assertFalse(merged.cells.single().conflict)
        assertEquals("local+ai", merged.cells.single().source)
    }

    @Test
    fun localMonthWinsWhenAiReturnsDifferentMonth() {
        val local = RecognizedSchedule(
            month = month,
            rows = listOf(RecognizedScheduleRow(1, "Test Osoba", mapOf(1 to "D"))),
            rawText = "lokalno"
        )
        val ai = RecognizedSchedule(
            month = YearMonth.of(2026, 11),
            rows = listOf(RecognizedScheduleRow(1, "Test Osoba", mapOf(1 to "D"))),
            rawText = ""
        )

        val merged = mergeForAiReview(local, ai)

        assertEquals(month, merged.schedule.month)
    }

    @Test
    fun aiCanAddMissingPersonWithoutCompressingDayIndex() {
        val local = RecognizedSchedule(
            month = month,
            rows = listOf(RecognizedScheduleRow(1, "Prva Osoba", mapOf(1 to "D"))),
            rawText = "lokalno",
            expectedRowCount = 2
        )
        val ai = RecognizedSchedule(
            month = month,
            rows = listOf(
                RecognizedScheduleRow(1, "Prva Osoba", mapOf(1 to "D")),
                RecognizedScheduleRow(2, "Nova Osoba", mapOf(17 to "P1"))
            ),
            rawText = "",
            expectedRowCount = 2
        )

        val merged = mergeForAiReview(local, ai)

        assertEquals(2, merged.schedule.rows.size)
        assertEquals("P1", merged.schedule.rows[1].dayShifts[17])
        assertFalse(merged.schedule.rows[1].dayShifts.containsKey(16))
        assertFalse(merged.cells.first { it.employeeRow == 2 && it.day == 17 }.conflict)
    }

    @Test
    fun selectsMostCompleteRecognizedPersonForImmediatePersonalImport() {
        val rows = listOf(
            RecognizedScheduleRow(4, "Ana Horvat", mapOf(1 to "D", 2 to "N", 3 to "GO")),
            RecognizedScheduleRow(9, "Luka Babić", mapOf(1 to "D")),
            RecognizedScheduleRow(12, "Petra Novak", mapOf(1 to "N", 17 to "D"))
        )

        assertEquals(0, bestRecognizedRowIndex(rows))
        assertEquals(-1, bestRecognizedRowIndex(emptyList()))
    }


    @Test
    fun singlePersonModeKeepsOnlyMostCompleteRecognizedRow() {
        val schedule = RecognizedSchedule(
            month = month,
            rows = listOf(
                RecognizedScheduleRow(4, "Ana Horvat", mapOf(1 to "D", 2 to "N", 3 to "GO")),
                RecognizedScheduleRow(9, "Luka Babić", mapOf(1 to "D")),
                RecognizedScheduleRow(12, "Petra Novak", mapOf(1 to "N", 17 to "D"))
            ),
            rawText = "tablica",
            expectedRowCount = 27
        )

        val focused = focusSinglePersonResult(schedule)

        assertEquals(1, focused.rows.size)
        assertEquals("Ana Horvat", focused.rows.single().name)
        assertEquals(3, focused.rows.single().dayShifts.size)
        assertEquals(1, focused.expectedRowCount)
    }


    @Test
    fun scanPreviewGeometryAccountsForVerticalLetterboxing() {
        val geometry = scanPreviewGeometry(
            boxWidth = 300f,
            boxHeight = 300f,
            imageWidth = 600f,
            imageHeight = 300f
        )

        requireNotNull(geometry)
        assertEquals(75f, geometry.top, 0.001f)
        assertEquals(150f, geometry.height, 0.001f)
    }

    @Test
    fun draggedCropKeepsHeightAndClampsToImageBounds() {
        val original = 0.34f..0.38f

        val down = shiftCropRange(original, 0.20f)
        assertEquals(0.54f, down.start, 0.001f)
        assertEquals(0.58f, down.endInclusive, 0.001f)

        val clampedTop = shiftCropRange(original, -2f)
        assertEquals(0f, clampedTop.start, 0.001f)
        assertEquals(0.04f, clampedTop.endInclusive, 0.001f)

        val clampedBottom = shiftCropRange(original, 2f)
        assertEquals(0.96f, clampedBottom.start, 0.001f)
        assertEquals(1f, clampedBottom.endInclusive, 0.001f)
    }

}
