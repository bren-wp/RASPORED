package hr.raspored.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

private const val MAX_OCR_DIMENSION = 2400

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
            val largest = maxOf(width, height)
            if (largest > MAX_OCR_DIMENSION) {
                val scale = MAX_OCR_DIMENSION.toFloat() / largest.toFloat()
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

private fun scaleDown(bitmap: Bitmap): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= MAX_OCR_DIMENSION) return bitmap
    val scale = MAX_OCR_DIMENSION.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    ).also { scaled ->
        if (scaled !== bitmap) bitmap.recycle()
    }
}
