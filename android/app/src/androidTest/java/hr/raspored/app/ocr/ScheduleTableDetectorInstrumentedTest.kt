package hr.raspored.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleTableDetectorInstrumentedTest {


    @Test
    fun keepsFullWidthWhenPhotographedHorizontalLinesAreFragmented() {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(242, 242, 242))

        val left = 145f
        val top = 185f
        val right = 1515f
        val bottom = 835f
        val nameWidth = 235f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 38, 38)
            strokeWidth = 2f
        }

        val verticals = buildList {
            add(left)
            add(left + 38f)
            add(left + nameWidth)
            for (day in 0..31) {
                add(left + nameWidth + (right - left - nameWidth) * day / 31f)
            }
        }.distinct()

        // Real monitor photos can make horizontal rules look like separated
        // dark intersection segments after adaptive thresholding. Keep enough
        // support to find every row, but leave cell-sized gaps between segments.
        for (row in 0..27) {
            val y = top + (bottom - top) * row / 27f
            verticals.forEach { x ->
                canvas.drawLine(x - 5f, y, x + 5f, y, paint)
            }
        }
        verticals.forEach { x -> canvas.drawLine(x, top, x, bottom, paint) }

        val bounds = ScheduleTableDetector.detectBounds(bitmap)
        assertNotNull(bounds)
        val detected = requireNotNull(bounds)
        assertTrue(detected.left <= 190)
        assertTrue(detected.right >= 1460)
        assertTrue(detected.top <= 220)
        assertTrue(detected.bottom >= 800)
        assertTrue(detected.width().toDouble() / bitmap.width >= 0.78)

        val crop = ScheduleTableDetector.cropForRecovery(bitmap)
        assertNotNull(crop)
        crop?.recycle()
        bitmap.recycle()
    }


    @Test
    fun createsEmployeeBandsForFullTwentySevenRowRoster() {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 247, 247))

        val left = 135f
        val top = 180f
        val right = 1510f
        val bottom = 820f
        val nameWidth = 245f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(36, 36, 36)
            strokeWidth = 2f
        }

        // Header + 27 employee rows = 29 horizontal rules.
        for (line in 0..28) {
            val y = top + (bottom - top) * line / 28f
            canvas.drawLine(left, y, right, y, paint)
        }
        canvas.drawLine(left, top, left, bottom, paint)
        canvas.drawLine(left + 38f, top, left + 38f, bottom, paint)
        canvas.drawLine(left + nameWidth, top, left + nameWidth, bottom, paint)
        for (day in 0..31) {
            val x = left + nameWidth + (right - left - nameWidth) * day / 31f
            canvas.drawLine(x, top, x, bottom, paint)
        }

        val singleRows = ScheduleTableDetector.detectEmployeeRowBands(bitmap, rowsPerBand = 1)
        assertTrue("Expected nearly all 27 single-employee rows", singleRows.size >= 25)
        assertTrue(singleRows.all { band ->
            band.bodyBottom > band.bodyTop &&
                band.bodyBottom - band.bodyTop < (bottom - top) / 10f
        })

        val bands = ScheduleTableDetector.detectEmployeeRowBands(bitmap, rowsPerBand = 4)
        assertTrue("Expected several exact employee bands", bands.size >= 6)
        assertTrue(bands.first().headerTop <= top + 35f)
        assertTrue(bands.first().headerBottom > bands.first().headerTop)
        assertTrue(bands.first().bodyTop >= bands.first().headerBottom)
        assertTrue(bands.last().bodyBottom >= bottom - 35f)
        assertTrue(bands.all { it.left <= 190 && it.right >= 1450 })

        bitmap.recycle()
    }

    @Test
    fun detectsDenseScheduleTableInsideWholePhoto() {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 247, 247))

        val left = 180f
        val top = 210f
        val right = 1510f
        val bottom = 820f
        val nameWidth = 230f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 32, 32)
            strokeWidth = 2f
        }

        for (row in 0..28) {
            val y = top + (bottom - top) * row / 28f
            canvas.drawLine(left, y, right, y, paint)
        }
        canvas.drawLine(left, top, left, bottom, paint)
        canvas.drawLine(left + nameWidth, top, left + nameWidth, bottom, paint)
        for (day in 0..31) {
            val x = left + nameWidth + (right - left - nameWidth) * day / 31f
            canvas.drawLine(x, top, x, bottom, paint)
        }

        val bounds = ScheduleTableDetector.detectBounds(bitmap)
        assertNotNull(bounds)
        val detected = requireNotNull(bounds)
        assertTrue(detected.left <= 230)
        assertTrue(detected.top <= 230)
        assertTrue(detected.right >= 1450)
        assertTrue(detected.bottom >= 790)
        assertTrue(detected.width() < 1500)
        assertTrue(detected.height() < 900)

        val crop = ScheduleTableDetector.cropForRecovery(bitmap)
        assertNotNull(crop)
        crop?.recycle()
        bitmap.recycle()
    }
}
