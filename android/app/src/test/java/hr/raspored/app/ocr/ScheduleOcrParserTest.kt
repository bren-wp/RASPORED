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
    fun infersDenseMonthGridFromShiftColumnsWhenHeaderIsUnreadable() {
        val xs = buildList {
            for (day in 1..31) {
                val center = 120 + (day - 1) * 42
                add(center - 2)
                add(center)
                add(center + 2)
            }
        }
        val centers = ScheduleOcrParser.inferDayCentersFromShiftXs(xs, 31)
        requireNotNull(centers)
        assertEquals(120, centers[1])
        assertEquals(750, centers[16])
        assertEquals(1380, centers[31])
    }

    @Test
    fun rejectsAmbiguousLeadingBlankDayWithoutHeaderEvidence() {
        val xs = buildList {
            for (day in 2..31) {
                val center = 120 + (day - 1) * 42
                add(center - 2)
                add(center)
                add(center + 2)
            }
        }
        assertNull(ScheduleOcrParser.inferDayCentersFromShiftXs(xs, 31))
    }

    @Test
    fun usesSingleHeaderAnchorToPreserveLeadingBlankDay() {
        val xs = buildList {
            for (day in 2..31) {
                val center = 120 + (day - 1) * 42
                add(center - 2)
                add(center)
                add(center + 2)
            }
        }
        val centers = ScheduleOcrParser.inferDayCentersFromShiftXs(
            rawXs = xs,
            maxDay = 31,
            absoluteAnchors = mapOf(2 to listOf(162))
        )
        requireNotNull(centers)
        assertEquals(120, centers[1])
        assertEquals(162, centers[2])
        assertEquals(1380, centers[31])
    }

    @Test
    fun rejectsSparseShiftColumnsInsteadOfInventingCalendarDays() {
        assertNull(
            ScheduleOcrParser.inferDayCentersFromShiftXs(
                listOf(100, 300, 500, 700, 900),
                31
            )
        )
    }

    @Test
    fun filtersDistantRowNumberNoiseFromDenseRoster() {
        val rows = (1..12).map { number ->
            RecognizedScheduleRow(
                rowNumber = number,
                name = "Osoba Primjer $number",
                dayShifts = mapOf(1 to "D")
            )
        } + RecognizedScheduleRow(
            rowNumber = 80,
            name = "Lažni Rubni Tekst",
            dayShifts = emptyMap()
        )

        val merged = ScheduleOcrParser.mergeRows(rows)

        assertEquals(12, merged.size)
        assertEquals((1..12).toList(), merged.mapNotNull { it.rowNumber })
    }

    @Test
    fun mergesPartialDayBandsByRosterRowNumber() {
        val merged = ScheduleOcrParser.mergeRows(
            listOf(
                RecognizedScheduleRow(
                    rowNumber = 1,
                    name = "ANA HORVAT",
                    dayShifts = mapOf(1 to "D", 2 to "N")
                ),
                RecognizedScheduleRow(
                    rowNumber = 1,
                    name = "ANA HORVAT",
                    dayShifts = mapOf(15 to "GO", 31 to "SD")
                ),
                RecognizedScheduleRow(
                    rowNumber = 2,
                    name = "LUKA BABIĆ",
                    dayShifts = mapOf(1 to "N")
                )
            )
        )

        assertEquals(2, merged.size)
        assertEquals(
            mapOf(1 to "D", 2 to "N", 15 to "GO", 31 to "SD"),
            merged.first { it.rowNumber == 1 }.dayShifts
        )
    }

    @Test
    fun mergesReorderedNamesWhenRowNumberIsMissingInRecoveryPass() {
        val merged = ScheduleOcrParser.mergeRows(
            listOf(
                RecognizedScheduleRow(
                    rowNumber = null,
                    name = "ANA HORVAT",
                    dayShifts = mapOf(1 to "D")
                ),
                RecognizedScheduleRow(
                    rowNumber = null,
                    name = "HORVAT ANA",
                    dayShifts = mapOf(20 to "N")
                )
            )
        )

        assertEquals(1, merged.size)
        assertEquals(mapOf(1 to "D", 20 to "N"), merged.single().dayShifts)
    }

    @Test
    fun detectsCroatianMonthWithoutDiacritics() {
        val result = ScheduleOcrParser.parse("SIJECANJ 2027.\n3 ANA HORVAT D N")
        assertEquals(YearMonth.of(2027, 1), result.month)
    }

}
