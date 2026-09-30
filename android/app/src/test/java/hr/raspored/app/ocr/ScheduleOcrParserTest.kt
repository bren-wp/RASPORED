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
    fun acceptsTableBorderSeparatorsAroundExplicitCodes() {
        val result = ScheduleOcrParser.parse(
            "6 ANA HORVAT 1 | D 2 | N 4 | GO 7 | PD 9 | SD"
        )
        assertEquals(
            mapOf(1 to "D", 2 to "N", 4 to "GO", 7 to "PD", 9 to "SD"),
            result.rows.single().dayShifts
        )
    }

    @Test
    fun gridCodePreservesWorkplaceSpecificShortLabelsWithoutGuessingMeaning() {
        assertEquals("J", ScheduleOcrParser.canonicalGridCode("J"))
        assertEquals("S", ScheduleOcrParser.canonicalGridCode("s"))
        assertEquals("P1", ScheduleOcrParser.canonicalGridCode("P1"))
        assertEquals("1", ScheduleOcrParser.canonicalGridCode("1"))
        assertEquals("GO", ScheduleOcrParser.canonicalGridCode("G0"))
        assertNull(ScheduleOcrParser.canonicalGridCode("27"))
        assertNull(ScheduleOcrParser.canonicalGridCode("RB"))
        assertNull(ScheduleOcrParser.canonicalGridCode("PREVISE"))
    }


    @Test
    fun mergesShiftOnlyNumberedRowWithRecoveredRosterName() {
        val merged = ScheduleOcrParser.mergeRows(
            listOf(
                RecognizedScheduleRow(
                    rowNumber = 6,
                    name = "",
                    dayShifts = mapOf(1 to "D", 15 to "N", 31 to "GO")
                ),
                RecognizedScheduleRow(
                    rowNumber = 6,
                    name = "ANA HORVAT",
                    dayShifts = emptyMap()
                )
            )
        )

        assertEquals(1, merged.size)
        assertEquals("ANA HORVAT", merged.single().name)
        assertEquals(
            mapOf(1 to "D", 15 to "N", 31 to "GO"),
            merged.single().dayShifts
        )
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
    fun mergesDenseThirtyPersonRosterWithoutDroppingBlankCalendarDays() {
        val rows = buildList {
            for (number in 1..30) {
                add(
                    RecognizedScheduleRow(
                        rowNumber = number,
                        name = "TESTOSOBA$number",
                        dayShifts = mapOf(
                            1 to if (number % 2 == 0) "N" else "D",
                            5 to "GO",
                            16 to if (number % 3 == 0) "PD" else "D"
                        )
                    )
                )
                add(
                    RecognizedScheduleRow(
                        rowNumber = number,
                        name = "TESTOSOBA$number",
                        dayShifts = mapOf(
                            24 to "BO",
                            31 to if (number % 4 == 0) "SD" else "N"
                        )
                    )
                )
            }
        }

        val merged = ScheduleOcrParser.mergeRows(rows)

        assertEquals(30, merged.size)
        assertEquals((1..30).toList(), merged.mapNotNull { it.rowNumber })
        merged.forEach { row ->
            assertEquals(null, row.dayShifts[2])
            assertEquals("GO", row.dayShifts[5])
            assertEquals("BO", row.dayShifts[24])
        }
    }

    @Test
    fun finalizeRowsRejectsOneOffGhostNamesFromDenseThirtyPersonRoster() {
        val realRows = (1..30).map { number ->
            RecognizedScheduleRow(
                rowNumber = number,
                name = "TEST OSOBA $number".replace(number.toString(), ""),
                dayShifts = mapOf(1 to if (number % 2 == 0) "N" else "D", 16 to "GO", 31 to "D"),
                supportCount = 3
            )
        }
        val ghosts = (1..25).map { index ->
            RecognizedScheduleRow(
                rowNumber = null,
                name = "SLUCAJNI TEKST $index".replace(index.toString(), ""),
                dayShifts = mapOf(2 to "D"),
                supportCount = 1
            )
        }

        val finalized = ScheduleOcrParser.finalizeRows(realRows + ghosts)

        assertEquals(30, finalized.size)
        assertEquals((1..30).toList(), finalized.mapNotNull { it.rowNumber })
    }

    @Test
    fun mergeRowsUsesRowNumberToConsolidateOcrNameVariants() {
        val merged = ScheduleOcrParser.mergeRows(
            listOf(
                RecognizedScheduleRow(
                    rowNumber = 4,
                    name = "ANA HORVAT",
                    dayShifts = mapOf(1 to "D"),
                    supportCount = 1
                ),
                RecognizedScheduleRow(
                    rowNumber = 4,
                    name = "ANA HORV4T",
                    dayShifts = mapOf(18 to "N"),
                    supportCount = 1
                ),
                RecognizedScheduleRow(
                    rowNumber = 4,
                    name = "ANA HORVAT",
                    dayShifts = mapOf(31 to "GO"),
                    supportCount = 1
                )
            )
        )

        assertEquals(1, merged.size)
        assertEquals("ANA HORVAT", merged.single().name)
        assertEquals(mapOf(1 to "D", 18 to "N", 31 to "GO"), merged.single().dayShifts)
        assertEquals(3, merged.single().supportCount)
    }

    @Test
    fun doesNotMergeDifferentEmployeesWithVerySimilarNames() {
        val merged = ScheduleOcrParser.mergeRows(
            listOf(
                RecognizedScheduleRow(
                    rowNumber = null,
                    name = "IVAN HORVAT",
                    dayShifts = mapOf(1 to "D"),
                    supportCount = 3
                ),
                RecognizedScheduleRow(
                    rowNumber = null,
                    name = "IVANA HORVAT",
                    dayShifts = mapOf(2 to "N"),
                    supportCount = 3
                )
            )
        )

        assertEquals(2, merged.size)
        assertEquals(setOf("IVAN HORVAT", "IVANA HORVAT"), merged.map { it.name }.toSet())
    }

    @Test
    fun denseRosterKeepsRepeatedUnnumberedEmployeeOnlyForMissingSlot() {
        val numbered = (1..30)
            .filter { it != 15 }
            .map { number ->
                RecognizedScheduleRow(
                    rowNumber = number,
                    name = "OSOBA BROJ $number",
                    dayShifts = mapOf(1 to "D", 31 to "N"),
                    supportCount = 3
                )
            }
        val missingEmployee = RecognizedScheduleRow(
            rowNumber = null,
            name = "MAJA PERIĆ",
            dayShifts = mapOf(1 to "N", 16 to "GO", 31 to "D"),
            supportCount = 4
        )
        val repeatedGhost = RecognizedScheduleRow(
            rowNumber = null,
            name = "NAPOMENA GODISNJI",
            dayShifts = mapOf(12 to "GO"),
            supportCount = 8
        )

        val finalized = ScheduleOcrParser.finalizeRows(
            numbered + missingEmployee + repeatedGhost
        )

        assertEquals(30, finalized.size)
        assertEquals(1, finalized.count { it.rowNumber == null })
        assertEquals("MAJA PERIĆ", finalized.single { it.rowNumber == null }.name)
    }

    @Test
    fun detectsCroatianMonthWithoutDiacritics() {
        val result = ScheduleOcrParser.parse("SIJECANJ 2027.\n3 ANA HORVAT D N")
        assertEquals(YearMonth.of(2027, 1), result.month)
    }

}
