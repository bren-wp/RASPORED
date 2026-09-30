package hr.raspored.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class ScheduleOcrParserTest {
    @Test
    fun parsesEmployeeRowAndShiftCodes() {
        val result = ScheduleOcrParser.parse("6 ANA HORVAT 1 D 2 N 3 D 4 GO 5 BO")
        assertEquals(1, result.rows.size)
        assertEquals(6, result.rows.single().rowNumber)
        assertEquals("ANA HORVAT", result.rows.single().name)
        assertEquals(listOf("D", "N", "D", "GO", "BO"), result.rows.single().shifts)
    }

    @Test
    fun detectsCroatianMonthAndYear() {
        val result = ScheduleOcrParser.parse("LISTOPAD 2026.\n6 ANA HORVAT D N")
        assertEquals(YearMonth.of(2026, 10), result.month)
    }

    @Test
    fun ignoresHeaderWithoutShiftData() {
        assertNull(ScheduleOcrParser.parseRow("RB NOSAČ/BOLesNIKA 1 2 3 4 5"))
    }

    @Test
    fun keepsMultipleEmployeesAsSeparateSelectableRows() {
        val result = ScheduleOcrParser.parse(
            "6 ANA HORVAT 1 D 2 N 3 D 4 GO\n7 LUKA BABIĆ 1 GO 2 D 3 N 4 BO"
        )

        assertEquals(2, result.rows.size)
        assertEquals("ANA HORVAT", result.rows[0].name)
        assertEquals("LUKA BABIĆ", result.rows[1].name)
        assertEquals(listOf("D", "N", "D", "GO"), result.rows[0].shifts)
        assertEquals(listOf("GO", "D", "N", "BO"), result.rows[1].shifts)
    }


    @Test
    fun normalizesCommonOcrConfusionsForGoAndBo() {
        val result = ScheduleOcrParser.parse("3 ANA HORVAT 1 G0 2 B0 3 D 4 N 5 PD 6 SD")
        assertEquals(listOf("GO", "BO", "D", "N", "PD", "SD"), result.rows.single().shifts)
    }

    @Test
    fun preservesExplicitDayNumbersWithoutCompressingEmptyDays() {
        val result = ScheduleOcrParser.parse("6 ANA HORVAT 1 D 2 N 4 GO 7 PD 9 SD")
        assertEquals(
            mapOf(1 to "D", 2 to "N", 4 to "GO", 7 to "PD", 9 to "SD"),
            result.rows.single().dayShifts
        )
    }

    @Test
    fun detectsNumericMonthFormats() {
        assertEquals(
            YearMonth.of(2026, 10),
            ScheduleOcrParser.parse("10/2026\n6 ANA HORVAT D N").month
        )
        assertEquals(
            YearMonth.of(2026, 10),
            ScheduleOcrParser.parse("2026-10\n6 ANA HORVAT D N").month
        )
    }

    @Test
    fun doesNotInventCalendarDaysWhenGeometryIsMissing() {
        val result = ScheduleOcrParser.parse("6 ANA HORVAT D N GO BO")
        assertEquals(1, result.rows.size)
        assertEquals(emptyMap<Int, String>(), result.rows.single().dayShifts)
    }

    @Test
    fun interpolatesMissingDayCentersFromFragmentedHeader() {
        val centers = ScheduleOcrParser.inferAllDayCenters(
            mapOf(1 to 100, 16 to 400, 31 to 700),
            31
        )
        requireNotNull(centers)
        assertEquals(120, centers[2])
        assertEquals(400, centers[16])
        assertEquals(680, centers[30])
        assertEquals(31, centers.size)
    }

    @Test
    fun detectsCroatianMonthWithoutDiacritics() {
        val result = ScheduleOcrParser.parse("SIJECANJ 2027.\n3 ANA HORVAT D N")
        assertEquals(YearMonth.of(2027, 1), result.month)
    }

}
