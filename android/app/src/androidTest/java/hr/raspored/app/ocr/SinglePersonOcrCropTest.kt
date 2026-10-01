package hr.raspored.app.ocr

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
