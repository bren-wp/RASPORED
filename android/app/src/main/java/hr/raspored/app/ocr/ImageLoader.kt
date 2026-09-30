package hr.raspored.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

private const val MAX_OCR_LONG_EDGE = 4096
private const val MAX_OCR_PIXELS = 10_000_000L

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
