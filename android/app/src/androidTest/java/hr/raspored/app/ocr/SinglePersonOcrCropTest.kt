package hr.raspored.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SinglePersonOcrCropTest {

    @Test
    fun keepsHeaderAndOnlySelectedHorizontalRowBand() {
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        try {
            val focused = createSinglePersonOcrBitmap(
                source = source,
                topFraction = 0.40f,
                bottomFraction = 0.50f,
                headerFraction = 0.24f
            )
            try {
                assertEquals(100, focused.width)
                // 48 px header + 4 px minimum separator + 20 px selected row.
                assertEquals(72, focused.height)
                assertFalse(source.isRecycled)
            } finally {
                focused.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun detectedGridRemovesPageMarginsAndKeepsOnlyRealHeaderPlusSelectedRow() {
        val source = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
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

        for (line in 0..28) {
            val y = top + (bottom - top) * line / 28f
            canvas.drawLine(left, y, right, y, paint)
        }
        canvas.drawLine(left, top, left, bottom, paint)
        canvas.drawLine(left + nameWidth, top, left + nameWidth, bottom, paint)
        for (day in 0..31) {
            val x = left + nameWidth + (right - left - nameWidth) * day / 31f
            canvas.drawLine(x, top, x, bottom, paint)
        }

        try {
            val focused = createSinglePersonOcrBitmap(
                source = source,
                topFraction = 0.40f,
                bottomFraction = 0.43f
            )
            try {
                assertTrue("Detected table width should remove outer page margins", focused.width < source.width)
                assertTrue("Detected table width should still keep the complete grid", focused.width > 1300)
                assertTrue("Focused OCR image must stay compact", focused.height < 180)
                assertFalse(source.isRecycled)
            } finally {
                focused.recycle()
            }
        } finally {
            source.recycle()
        }
    }

}
