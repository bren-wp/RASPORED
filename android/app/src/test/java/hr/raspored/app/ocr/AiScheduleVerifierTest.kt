package hr.raspored.app.ocr

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AiScheduleVerifierTest {
    @Test
    fun rejectsMalformedJsonAndEmptyPeople() {
        assertThrows(IllegalStateException::class.java) {
            AiScheduleVerifier.parseResponse(200, "{nije-json")
        }
        assertThrows(IllegalStateException::class.java) {
            AiScheduleVerifier.parseResponse(
                200,
                """{"ok":true,"result":{"month":{"year":2026,"month":10},"people":[],"expectedRows":0,"notes":""}}"""
            )
        }
    }

    @Test
    fun preservesBackendErrorsForAuthRateLimitAndServerFailure() {
        listOf(
            401 to "Prijava je istekla.",
            429 to "Dosegnut je limit.",
            500 to "Poslužitelj nije dostupan."
        ).forEach { (status, message) ->
            val error = assertThrows(IllegalStateException::class.java) {
                AiScheduleVerifier.parseResponse(
                    status,
                    """{"ok":false,"error":"$message"}"""
                )
            }
            assertEquals(message, error.message)
        }
    }

    @Test
    fun parsesCustomCodesNormalizesZeroSubstitutionsAndKeepsDay31() {
        val result = AiScheduleVerifier.parseResponse(
            200,
            """{"ok":true,"result":{"month":{"year":2026,"month":10},"people":[{"row":1,"name":"Test Osoba","dayShifts":{"1":"G0","17":"P1","31":"B0"}}],"expectedRows":1,"notes":""}}"""
        )

        assertEquals(YearMonth.of(2026, 10), result.month)
        assertEquals("GO", result.rows.single().dayShifts[1])
        assertEquals("P1", result.rows.single().dayShifts[17])
        assertEquals("BO", result.rows.single().dayShifts[31])
        assertFalse(result.rows.single().dayShifts.containsKey(16))
    }

    @Test
    fun parsesThirtyFivePeopleWithoutCompressingSparseDays() {
        val people = (1..35).joinToString(",") { row ->
            """{"row":$row,"name":"Test Osoba $row","dayShifts":{"1":"D","31":"N"}}"""
        }
        val result = AiScheduleVerifier.parseResponse(
            200,
            """{"ok":true,"result":{"month":{"year":2026,"month":10},"people":[$people],"expectedRows":35,"notes":""}}"""
        )

        assertEquals(35, result.rows.size)
        assertEquals(35, result.expectedRowCount)
        result.rows.forEach {
            assertEquals("D", it.dayShifts[1])
            assertEquals("N", it.dayShifts[31])
            assertFalse(it.dayShifts.containsKey(2))
        }
    }
}
