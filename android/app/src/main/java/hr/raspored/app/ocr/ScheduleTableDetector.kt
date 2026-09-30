package hr.raspored.app.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Locates the dense monthly schedule grid before recovery OCR passes.
 *
 * Whole-page photos often contain large margins, monitor chrome or desk area.
 * Cropping those areas out gives each employee row and each day cell many more
 * effective pixels without changing the source image shown to the user.
 */
internal object ScheduleTableDetector {
    private const val DETECTION_LONG_EDGE = 1200
    private const val MIN_GRID_LINES = 10

    fun cropForRecovery(source: Bitmap): Bitmap? {
        val bounds = detectBounds(source) ?: return null
        val widthRatio = bounds.width().toDouble() / source.width.toDouble()
        val heightRatio = bounds.height().toDouble() / source.height.toDouble()
        val areaRatio = widthRatio * heightRatio

        // Completeness has priority over aggressive cropping. A partial grid
        // detection caused by glare or weak lines must never cut off the lower
        // employees or the last day columns. If the detected rectangle is too
        // small to plausibly be the whole monthly table, recovery uses the
        // original full-resolution image instead.
        if (widthRatio < 0.60 || heightRatio < 0.45 || areaRatio >= 0.92) return null
        return runCatching {
            Bitmap.createBitmap(
                source,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
            )
        }.getOrNull()
    }

    internal fun detectBounds(source: Bitmap): Rect? {
        if (source.width < 700 || source.height < 500) return null

        val scale = min(
            1.0,
            DETECTION_LONG_EDGE.toDouble() / max(source.width, source.height).toDouble()
        )
        val sampleWidth = max(1, (source.width * scale).roundToInt())
        val sampleHeight = max(1, (source.height * scale).roundToInt())
        val sample = if (sampleWidth == source.width && sampleHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, sampleWidth, sampleHeight, true)
        }

        try {
            val pixels = IntArray(sampleWidth * sampleHeight)
            sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            val threshold = adaptiveDarkThreshold(pixels, sampleWidth, sampleHeight)
            val dark = BooleanArray(pixels.size)
            pixels.indices.forEach { index ->
                dark[index] = luminance(pixels[index]) <= threshold
            }

            val xStart = (sampleWidth * 0.04).roundToInt().coerceIn(0, sampleWidth - 1)
            val xEnd = (sampleWidth * 0.96).roundToInt().coerceIn(xStart + 1, sampleWidth)
            val candidateRows = mutableListOf<Int>()
            for (y in 0 until sampleHeight) {
                var count = 0
                val rowOffset = y * sampleWidth
                for (x in xStart until xEnd) {
                    if (dark[rowOffset + x]) count++
                }
                if (count.toDouble() / (xEnd - xStart).toDouble() >= 0.16) {
                    candidateRows += y
                }
            }

            val centers = groupCenters(candidateRows)
                .filter { it in 2 until sampleHeight - 2 }
            val grid = fitHorizontalGrid(centers) ?: return null
            if (grid.matches.size < MIN_GRID_LINES) return null

            val spacing = grid.spacing
            val matchedRows = grid.matches.map { it.second }.distinct().sorted()
            val tableTop = (
                (matchedRows.first() - spacing * 3.0).roundToInt()
            ).coerceAtLeast(0)
            val tableBottom = (
                (matchedRows.last() + spacing * 2.2).roundToInt()
            ).coerceAtMost(sampleHeight)

            val horizontalSupport = DoubleArray(sampleWidth)
            for (x in 0 until sampleWidth) {
                var supported = 0
                matchedRows.forEach { y ->
                    val top = max(0, y - 1)
                    val bottom = min(sampleHeight - 1, y + 1)
                    var hit = false
                    for (yy in top..bottom) {
                        if (dark[yy * sampleWidth + x]) {
                            hit = true
                            break
                        }
                    }
                    if (hit) supported++
                }
                horizontalSupport[x] =
                    supported.toDouble() / matchedRows.size.toDouble()
            }

            val supportedXs = BooleanArray(sampleWidth) { x ->
                horizontalSupport[x] >= 0.22
            }
            bridgeSmallGaps(
                supportedXs,
                maxGap = max(6, (spacing * 0.55).roundToInt())
            )
            val runs = trueRuns(supportedXs)
            val widest = runs.maxByOrNull { it.last - it.first } ?: return null
            if (widest.last - widest.first < sampleWidth * 0.45) return null

            val sideMargin = (spacing * 1.4).roundToInt()
            val tableLeft = (widest.first - sideMargin).coerceAtLeast(0)
            val tableRight = (widest.last + sideMargin + 1).coerceAtMost(sampleWidth)

            if (tableRight - tableLeft < sampleWidth * 0.45) return null
            if (tableBottom - tableTop < sampleHeight * 0.25) return null

            val scaleX = source.width.toDouble() / sampleWidth.toDouble()
            val scaleY = source.height.toDouble() / sampleHeight.toDouble()
            val left = (tableLeft * scaleX).roundToInt().coerceIn(0, source.width - 1)
            val top = (tableTop * scaleY).roundToInt().coerceIn(0, source.height - 1)
            val right = (tableRight * scaleX).roundToInt().coerceIn(left + 1, source.width)
            val bottom = (tableBottom * scaleY).roundToInt().coerceIn(top + 1, source.height)

            val result = Rect(left, top, right, bottom)
            val widthRatio = result.width().toDouble() / source.width.toDouble()
            val heightRatio = result.height().toDouble() / source.height.toDouble()
            return if (widthRatio >= 0.45 && heightRatio >= 0.25) result else null
        } finally {
            if (sample !== source && !sample.isRecycled) sample.recycle()
        }
    }

    private data class GridFit(
        val spacing: Double,
        val matches: List<Pair<Int, Int>>,
        val score: Double
    )

    private fun fitHorizontalGrid(centers: List<Int>): GridFit? {
        if (centers.size < MIN_GRID_LINES) return null
        val sorted = centers.distinct().sorted()
        val gaps = sorted.zipWithNext()
            .map { (a, b) -> b - a }
            .filter { it in 7..80 }
        if (gaps.size < 6) return null

        val frequencies = gaps.groupingBy { it }.eachCount()
        val dominantGap = frequencies.keys
            .filter { it >= 10 }
            .maxWithOrNull(
                compareBy<Int> { candidate ->
                    frequencies.entries.sumOf { (gap, count) ->
                        if (abs(gap - candidate) <= 2) count else 0
                    }
                }.thenBy { frequencies[it] ?: 0 }
            )
            ?: return null
        val spacingSeeds = (dominantGap - 2..dominantGap + 2)
            .map(Int::toDouble)
            .filter { it in 8.0..80.0 }

        var best: GridFit? = null
        spacingSeeds.forEach { spacing ->
            val tolerance = max(3.0, spacing * 0.30)
            sorted.forEach { start ->
                val matches = mutableListOf<Pair<Int, Int>>()
                var index = 0
                var predicted = start.toDouble()
                val lastCenter = sorted.last().toDouble()
                while (predicted <= lastCenter + tolerance && index < 64) {
                    val nearest = sorted.minByOrNull { center ->
                        abs(center.toDouble() - predicted)
                    }
                    if (nearest != null && abs(nearest - predicted) <= tolerance) {
                        if (matches.none { it.second == nearest }) {
                            matches += index to nearest
                        }
                    }
                    predicted += spacing
                    index++
                }
                if (matches.size < MIN_GRID_LINES) return@forEach

                val span = matches.last().first - matches.first().first + 1
                if (span < MIN_GRID_LINES) return@forEach
                val coverage = matches.size.toDouble() / span.toDouble()
                val residual = matches.map { (gridIndex, actual) ->
                    abs(actual - (start + gridIndex * spacing))
                }.average() / spacing
                val score =
                    matches.size * 1.0 +
                        coverage * 5.0 +
                        span * 0.08 -
                        residual * 2.0
                val candidate = GridFit(spacing, matches, score)
                if (best == null || candidate.score > best!!.score) {
                    best = candidate
                }
            }
        }

        val fit = best ?: return null
        val span = fit.matches.last().first - fit.matches.first().first + 1
        val coverage = fit.matches.size.toDouble() / span.toDouble()
        return if (fit.matches.size >= MIN_GRID_LINES && coverage >= 0.58) fit else null
    }

    private fun adaptiveDarkThreshold(
        pixels: IntArray,
        width: Int,
        height: Int
    ): Int {
        val histogram = IntArray(256)
        val step = if (width * height > 700_000) 4 else 2
        var samples = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                histogram[luminance(pixels[y * width + x])]++
                samples++
                x += step
            }
            y += step
        }
        val target = max(1, (samples * 0.15).roundToInt())
        var cumulative = 0
        var percentile = 80
        for (value in histogram.indices) {
            cumulative += histogram[value]
            if (cumulative >= target) {
                percentile = value
                break
            }
        }
        return (percentile + 20).coerceIn(65, 125)
    }

    private fun luminance(color: Int): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return ((red * 299 + green * 587 + blue * 114) / 1000)
            .coerceIn(0, 255)
    }

    private fun groupCenters(values: List<Int>): List<Int> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val groups = mutableListOf<MutableList<Int>>()
        sorted.forEach { value ->
            val current = groups.lastOrNull()
            if (current != null && value <= current.last() + 1) {
                current += value
            } else {
                groups += mutableListOf(value)
            }
        }
        return groups
            .filter { it.size <= 14 }
            .map { group -> group[group.size / 2] }
    }

    private fun bridgeSmallGaps(mask: BooleanArray, maxGap: Int) {
        var index = 0
        while (index < mask.size) {
            if (mask[index]) {
                index++
                continue
            }
            val start = index
            while (index < mask.size && !mask[index]) index++
            val end = index - 1
            val bounded = start > 0 && index < mask.size && mask[start - 1] && mask[index]
            if (bounded && end - start + 1 <= maxGap) {
                for (fill in start..end) mask[fill] = true
            }
        }
    }

    private fun trueRuns(mask: BooleanArray): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var index = 0
        while (index < mask.size) {
            while (index < mask.size && !mask[index]) index++
            if (index >= mask.size) break
            val start = index
            while (index < mask.size && mask[index]) index++
            runs += start until index
        }
        return runs
    }
}
