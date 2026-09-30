package hr.raspored.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class ScheduleOcrParserTest {
    @Test
    fun parsesEmployeeRowAndShiftCodes() {
        val result = ScheduleOcrParser.parse("6 MARIO EGIMOVIĆ D N D GO BO")
        assertEquals(1, result.rows.size)
        assertEquals(6, result.rows.single().rowNumber)
        assertEquals("MARIO EGIMOVIĆ", result.rows.single().name)
        assertEquals(listOf("D", "N", "D", "GO", "BO"), result.rows.single().shifts)
    }

    @Test
    fun detectsCroatianMonthAndYear() {
        val result = ScheduleOcrParser.parse("LISTOPAD 2026.\n6 MARIO EGIMOVIĆ D N")
        assertEquals(YearMonth.of(2026, 10), result.month)
    }

    @Test
    fun ignoresHeaderWithoutShiftData() {
        assertNull(ScheduleOcrParser.parseRow("RB NOSAČ/BOLesNIKA 1 2 3 4 5"))
    }

    @Test
    fun keepsMultipleEmployeesAsSeparateSelectableRows() {
        val result = ScheduleOcrParser.parse(
            "6 MARIO EGIMOVIĆ D N D GO\n7 ADEMI DENI GO D N BO"
        )

        assertEquals(2, result.rows.size)
        assertEquals("MARIO EGIMOVIĆ", result.rows[0].name)
        assertEquals("ADEMI DENI", result.rows[1].name)
        assertEquals(listOf("D", "N", "D", "GO"), result.rows[0].shifts)
        assertEquals(listOf("GO", "D", "N", "BO"), result.rows[1].shifts)
    }


    @Test
    fun normalizesCommonOcrConfusionsForGoAndBo() {
        val result = ScheduleOcrParser.parse("3 ANA HORVAT G0 B0 D N")
        assertEquals(listOf("GO", "BO", "D", "N"), result.rows.single().shifts)
    }

    @Test
    fun detectsCroatianMonthWithoutDiacritics() {
        val result = ScheduleOcrParser.parse("SIJECANJ 2027.\n3 ANA HORVAT D N")
        assertEquals(YearMonth.of(2027, 1), result.month)
    }

}
