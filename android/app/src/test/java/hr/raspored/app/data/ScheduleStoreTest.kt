package hr.raspored.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleStoreTest {
    @Test
    fun normalizesBuiltInAndCustomScheduleCodes() {
        assertEquals("D", ScheduleStore.normalizeCode(" d "))
        assertEquals("PD", ScheduleStore.normalizeCode("pd"))
        assertEquals("P1", ScheduleStore.normalizeCode(" p1 "))
        assertEquals("EDU", ScheduleStore.normalizeCode("edu"))
        assertEquals("Č1", ScheduleStore.normalizeCode("č1"))
        assertEquals("GO", ScheduleStore.normalizeCode("G0"))
        assertEquals("BO", ScheduleStore.normalizeCode("b0"))
    }

    @Test
    fun rejectsUnsafeOrOverlongCustomCodes() {
        assertNull(ScheduleStore.normalizeCode(""))
        assertNull(ScheduleStore.normalizeCode("A B"))
        assertNull(ScheduleStore.normalizeCode("A/B"))
        assertNull(ScheduleStore.normalizeCode("<D>"))
        assertNull(ScheduleStore.normalizeCode("PREdugacka"))
    }
}
