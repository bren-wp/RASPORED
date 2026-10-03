package hr.raspored.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

private const val MAX_OCR_LONG_EDGE = 5000
private const val MAX_OCR_PIXELS = 14_000_000L

fun createOcrCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "ocr").apply { mkdirs() }
    directory.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
        ?.forEach { it.delete() }
    val file = File.createTempFile("raspored-", ".jpg", directory)
    return FileProvider.getUriForFile(context, context.packageName + ".files", file)
}

@Suppress("DEPRECATION")
fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val width = info.size.width
            val height = info.size.height
            val scale = targetScale(width, height)
            if (scale < 1f) {
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    scaleDown(bitmap)
}.getOrNull()

private fun targetScale(width: Int, height: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val longEdgeScale = MAX_OCR_LONG_EDGE.toFloat() / maxOf(width, height).toFloat()
    val pixels = width.toLong() * height.toLong()
    val pixelScale = if (pixels > MAX_OCR_PIXELS) {
        sqrt(MAX_OCR_PIXELS.toDouble() / pixels.toDouble()).toFloat()
    } else {
        1f
    }
    return min(1f, min(longEdgeScale, pixelScale))
}

private fun scaleDown(bitmap: Bitmap): Bitmap {
    val scale = targetScale(bitmap.width, bitmap.height)
    if (scale >= 1f) return bitmap
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    ).also { scaled ->
        if (scaled !== bitmap) bitmap.recycle()
    }
}

fun rotateOcrBitmap(source: Bitmap, degrees: Int): Bitmap {
    require(!source.isRecycled) { "Bitmap is recycled" }
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return source
    require(normalized == 90 || normalized == 180 || normalized == 270) {
        "Rotation must be 90, 180 or 270 degrees"
    }
    val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
    return Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        matrix,
        true
    )
}



/**
 * Builds a focused OCR image for one selected employee row.
 *
 * The top table header is preserved so day numbers remain available to the
 * parser, while the selected horizontal row band is copied below it. This
 * prevents other employees from polluting OCR without losing day-column
 * geometry.
 */
fun createSinglePersonOcrBitmap(
    source: Bitmap,
    topFraction: Float,
    bottomFraction: Float,
    headerFraction: Float = 0.24f
): Bitmap {
    require(source.width > 0 && source.height > 0)
    val top = topFraction.coerceIn(0f, 0.98f)
    val bottom = bottomFraction.coerceIn(top + 0.01f, 1f)
    val rowTop = (source.height * top).toInt().coerceIn(0, source.height - 1)
    val rowBottom = (source.height * bottom).toInt().coerceIn(rowTop + 1, source.height)

    val targetCenter = (rowTop + rowBottom) / 2.0
    val detectedBand = ScheduleTableDetector
        .detectEmployeeRowBands(source, rowsPerBand = 1)
        .minByOrNull { band ->
            abs(((band.bodyTop + band.bodyBottom) / 2.0) - targetCenter)
        }

    val left = detectedBand?.left?.coerceIn(0, source.width - 1) ?: 0
    val right = detectedBand?.right?.coerceIn(left + 1, source.width) ?: source.width
    val headerTop = detectedBand?.headerTop?.coerceIn(0, source.height - 1) ?: 0
    val fallbackHeaderBottom = min(
        (source.height * headerFraction.coerceIn(0.08f, 0.40f)).toInt(),
        (rowTop - source.height * 0.015f).toInt().coerceAtLeast(1)
    )
    val headerBottom = (
        detectedBand?.headerBottom
            ?: fallbackHeaderBottom
    ).coerceIn(headerTop + 1, source.height)

    val cropWidth = right - left
    val headerHeight = headerBottom - headerTop
    val rowHeight = rowBottom - rowTop
    val gap = (source.height * 0.008f).toInt().coerceAtLeast(4)
    val output = Bitmap.createBitmap(
        cropWidth,
        headerHeight + gap + rowHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(output)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    canvas.drawBitmap(
        source,
        Rect(left, headerTop, right, headerBottom),
        Rect(0, 0, output.width, headerHeight),
        paint
    )
    canvas.drawBitmap(
        source,
        Rect(left, rowTop, right, rowBottom),
        Rect(0, headerHeight + gap, output.width, headerHeight + gap + rowHeight),
        paint
    )
    return output
}
