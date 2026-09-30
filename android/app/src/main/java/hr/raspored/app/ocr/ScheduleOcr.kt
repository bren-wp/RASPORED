package hr.raspored.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.Normalizer
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class RecognizedScheduleRow(
    val rowNumber: Int?,
    val name: String,
    val dayShifts: Map<Int, String>,
    val supportCount: Int = 1
) {
    val shifts: List<String>
        get() = dayShifts.toSortedMap().values.toList()
}

data class RecognizedSchedule(
    val month: YearMonth?,
    val rows: List<RecognizedScheduleRow>,
    val rawText: String
)

object ScheduleOcrParser {
    private val shiftRegex = Regex(
        """(?<![\p{L}])(GO|G0|BO|B0|PD|SD|D|N)[.,;:]?(?![\p{L}])""",
        RegexOption.IGNORE_CASE
    )
    private val rowNumberRegex = Regex("""^\s*(\d{1,3})[.)]?\s*""")
    private val explicitDayShiftRegex = Regex(
        """(?<!\d)([1-9]|[12]\d|3[01])\s*[:.)|\-]?\s*(GO|G0|BO|B0|PD|SD|D|N)(?![\p{L}])""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val spaces = Regex("""\s+""")
    private val monthNames = mapOf(
        "SIJECANJ" to Month.JANUARY,
        "VELJACA" to Month.FEBRUARY,
        "OZUJAK" to Month.MARCH,
        "TRAVANJ" to Month.APRIL,
        "SVIBANJ" to Month.MAY,
        "LIPANJ" to Month.JUNE,
        "SRPANJ" to Month.JULY,
        "KOLOVOZ" to Month.AUGUST,
        "RUJAN" to Month.SEPTEMBER,
        "LISTOPAD" to Month.OCTOBER,
        "STUDENI" to Month.NOVEMBER,
        "PROSINAC" to Month.DECEMBER
    )

    private data class Token(
        val text: String,
        val box: Rect,
        val centerX: Int = box.centerX(),
        val centerY: Int = box.centerY()
    )

    private fun normalizeAscii(value: String): String =
        Normalizer.normalize(value.uppercase(Locale("hr", "HR")), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace('Đ', 'D')

    private fun canonicalShift(raw: String): String? = when (
        raw.trim().trim('.', ',', ';', ':', '|', '[', ']', '(', ')', '{', '}', '_', '-').uppercase(Locale.ROOT)
    ) {
        "D" -> "D"
        "N" -> "N"
        "GO", "G0" -> "GO"
        "BO", "B0" -> "BO"
        "PD" -> "PD"
        "SD" -> "SD"
        else -> null
    }

    private data class HeaderGeometry(
        val bottom: Int,
        val dayCenters: Map<Int, Int>,
        val observedDays: Set<Int>,
        val gridStartX: Int
    )

    private data class DayToken(
        val day: Int,
        val token: Token
    )

    private data class RowAnchor(
        val rowNumber: Int?,
        val name: String,
        val centerY: Double
    )

    fun parse(
        result: Text,
        monthHint: YearMonth? = null
    ): RecognizedSchedule {
        val rawText = result.text.replace('\u00A0', ' ')
        val month = detectMonth(rawText) ?: monthHint
        val lines = result.textBlocks.flatMap { it.lines }
        val maxDay = month?.lengthOfMonth() ?: 31
        val header = findDayHeader(lines, maxDay)
            ?: inferDayHeaderFromShiftColumns(lines, maxDay)
        val geometryRows = header?.let { parseGeometryRows(lines, it) }.orEmpty()
        val textFallback = parse(rawText)

        return RecognizedSchedule(
            month = month,
            rows = mergeRows(geometryRows + textFallback.rows),
            rawText = rawText
        )
    }

    fun parse(text: String): RecognizedSchedule {
        val normalized = text.replace('\u00A0', ' ')
        val rows = normalized.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseRow)
            .toList()

        return RecognizedSchedule(
            month = detectMonth(normalized),
            rows = mergeRows(rows),
            rawText = normalized
        )
    }

    internal fun parseRosterRows(result: Text): List<RecognizedScheduleRow> {
        val lines = result.textBlocks.flatMap { it.lines }
        val tokens = allTokens(lines)
        if (tokens.isEmpty()) return emptyList()
        val medianHeight = medianInt(tokens.map { it.box.height().coerceAtLeast(1) }).toDouble()
        val tolerance = max(8.0, medianHeight * 0.90)

        val rows = clusterTokensByY(tokens, tolerance).mapNotNull { cluster ->
            val ordered = cluster.sortedBy { it.centerX }
            val text = ordered.joinToString(" ") { it.text }
                .replace(spaces, " ")
                .trim()
            val rowNumber = rowNumberRegex.find(text)
                ?.groupValues?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it in 1..100 }
                ?: return@mapNotNull null
            val name = cleanName(text)
            val hasYearLikeNoise = Regex("""\b20\d{2}\b|\d{4,}""").containsMatchIn(name)
            // A numbered roster row is a strong structural anchor. Keep it even
            // when OCR only recovers one of the two name tokens; later passes can
            // merge a better reading by row number instead of dropping the person.
            if (!validName(name) || hasYearLikeNoise) {
                return@mapNotNull null
            }
            RecognizedScheduleRow(
                rowNumber = rowNumber,
                name = name,
                dayShifts = emptyMap()
            )
        }
        return mergeRows(rows)
    }

    private fun allTokens(lines: List<Text.Line>): List<Token> =
        lines.flatMap { line ->
            line.elements.mapNotNull { element ->
                element.boundingBox
                    ?.let { Token(element.text.trim(), it) }
                    ?.takeIf { it.text.isNotBlank() }
            }
        }

    private fun dayNumber(raw: String, maxDay: Int): Int? =
        raw.trim()
            .trim('.', ',', ':', ';', '|')
            .toIntOrNull()
            ?.takeIf { it in 1..maxDay }

    private fun findDayHeader(
        lines: List<Text.Line>,
        maxDay: Int
    ): HeaderGeometry? {
        val tokens = allTokens(lines)
        val dayTokens = tokens.mapNotNull { token ->
            dayNumber(token.text, maxDay)?.let { DayToken(it, token) }
        }
        if (dayTokens.size < 2) return null

        val medianHeight = medianInt(dayTokens.map { it.token.box.height().coerceAtLeast(1) })
            .toDouble()
        val bandTolerance = max(12.0, medianHeight * 2.2)

        val best = dayTokens.asSequence()
            .map { anchor ->
                longestIncreasingDayChain(
                    dayTokens.filter {
                        abs(it.token.centerY - anchor.token.centerY) <= bandTolerance
                    }
                )
            }
            .filter { chain ->
                chain.size >= 2 &&
                    (chain.last().token.centerX - chain.first().token.centerX) >=
                    max(18.0, medianHeight * 1.6)
            }
            .maxWithOrNull(
                compareBy<List<DayToken>> { it.size }
                    .thenBy {
                        it.last().token.centerX - it.first().token.centerX
                    }
                    .thenBy {
                        -it.map { day -> day.token.centerY }.average().roundToInt()
                    }
            )
            ?: return null

        val observed = best
            .groupBy { it.day }
            .mapValues { (_, values) ->
                medianInt(values.map { it.token.centerX })
            }
            .toSortedMap()
        if (observed.size < 2) return null

        val centers = inferAllDayCenters(observed, maxDay) ?: return null
        val bottom = best.maxOf { it.token.box.bottom } +
            max(2.0, medianHeight * 0.35).roundToInt()

        return HeaderGeometry(
            bottom = bottom,
            dayCenters = centers,
            observedDays = observed.keys,
            // For partial recovery bands this must be the first ACTUALLY
            // observed day column, not extrapolated day 1. Otherwise late-month
            // bands can extrapolate day 1 left of the image and hide the roster
            // column from name parsing.
            gridStartX = observed.values.minOrNull() ?: centers.values.minOrNull() ?: 0
        )
    }

    private fun inferDayHeaderFromShiftColumns(
        lines: List<Text.Line>,
        maxDay: Int
    ): HeaderGeometry? {
        val tokens = allTokens(lines)
        val shiftTokens = tokens.filter { canonicalShift(it.text) != null }
        if (shiftTokens.size < 12) return null

        val minShiftY = shiftTokens.minOf { it.centerY }
        val medianHeight = medianInt(
            shiftTokens.map { it.box.height().coerceAtLeast(1) }
        ).toDouble()
        val shiftMinX = shiftTokens.minOf { it.centerX }
        val shiftMaxX = shiftTokens.maxOf { it.centerX }
        val roughSpacing = if (maxDay > 1) {
            (shiftMaxX - shiftMinX).toDouble() / (maxDay - 1).toDouble()
        } else {
            0.0
        }
        val weakAnchors = tokens.mapNotNull { token ->
            val day = dayNumber(token.text, maxDay) ?: return@mapNotNull null
            val inHeaderBand = token.centerY <= minShiftY + max(10.0, medianHeight * 1.25)
            val inGridBand = token.centerX >= shiftMinX - roughSpacing * 3.0 &&
                token.centerX <= shiftMaxX + roughSpacing * 3.0
            if (inHeaderBand && inGridBand) day to token.centerX else null
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )

        val centers = inferDayCentersFromShiftXs(
            rawXs = shiftTokens.map { it.centerX },
            maxDay = maxDay,
            absoluteAnchors = weakAnchors
        ) ?: return null

        return HeaderGeometry(
            // Nema stvarnog zaglavlja, pa zadržavamo cijelu tablicu i
            // kasnije redove određujemo prema imenima i Y geometriji.
            bottom = Int.MIN_VALUE,
            dayCenters = centers,
            observedDays = emptySet(),
            gridStartX = shiftMinX
        )
    }

    internal fun inferDayCentersFromShiftXs(
        rawXs: List<Int>,
        maxDay: Int,
        absoluteAnchors: Map<Int, List<Int>> = emptyMap()
    ): Map<Int, Int>? {
        if (maxDay !in 28..31 || rawXs.size < 12) return null
        val sorted = rawXs.sorted()
        val span = (sorted.last() - sorted.first()).toDouble()
        if (span <= 0.0) return null

        val clusterTolerance = max(3.0, span / (maxDay * 5.5))
        val clusters = mutableListOf<MutableList<Int>>()
        sorted.forEach { x ->
            val current = clusters.lastOrNull()
            val mean = current?.average()
            if (current != null && mean != null && abs(x - mean) <= clusterTolerance) {
                current += x
            } else {
                clusters += mutableListOf(x)
            }
        }

        val supportedClusters = clusters.filter { it.size >= 2 }
        val gridClusters = if (supportedClusters.size >= maxOf(10, maxDay / 3)) {
            supportedClusters
        } else {
            clusters
        }
        val observedCenters = gridClusters
            .map { cluster -> cluster.average() }
            .sorted()
        if (observedCenters.size < maxOf(12, maxDay / 2)) return null

        data class Candidate(
            val firstDay: Int,
            val lastDay: Int,
            val slope: Double,
            val intercept: Double,
            val residual: Double,
            val anchorResidual: Double,
            val assigned: List<Int>
        )

        val candidates = mutableListOf<Candidate>()
        for (leadingMissing in 0..3) {
            for (trailingMissing in 0..3) {
                val firstDay = 1 + leadingMissing
                val lastDay = maxDay - trailingMissing
                val daySpan = lastDay - firstDay
                if (daySpan <= 0) continue
                val slope = (observedCenters.last() - observedCenters.first()) / daySpan
                if (slope < 3.0) continue
                val intercept = observedCenters.first() - (firstDay - 1) * slope
                val assigned = observedCenters.map { x ->
                    (((x - intercept) / slope).roundToInt() + 1)
                        .coerceIn(1, maxDay)
                }
                if (assigned.zipWithNext().any { (a, b) -> b <= a }) continue
                val residual = observedCenters.indices
                    .map { index ->
                        abs(
                            observedCenters[index] -
                                (intercept + (assigned[index] - 1) * slope)
                        )
                    }
                    .average() / slope
                val anchorErrors = absoluteAnchors.flatMap { (day, xs) ->
                    if (day !in 1..maxDay) emptyList()
                    else xs.map { x ->
                        abs(x - (intercept + (day - 1) * slope)) / slope
                    }
                }
                val anchorResidual = if (anchorErrors.isEmpty()) {
                    0.0
                } else {
                    anchorErrors.average()
                }
                val edgePenalty = (leadingMissing + trailingMissing) * 0.025
                candidates += Candidate(
                    firstDay = firstDay,
                    lastDay = lastDay,
                    slope = slope,
                    intercept = intercept,
                    residual = residual + edgePenalty + anchorResidual * 2.5,
                    anchorResidual = anchorResidual,
                    assigned = assigned
                )
            }
        }

        val orderedCandidates = candidates.sortedBy { it.residual }
        val best = orderedCandidates.firstOrNull() ?: return null
        if (best.residual > 0.30) return null
        if (absoluteAnchors.isNotEmpty() && best.anchorResidual > 0.35) return null
        val second = orderedCandidates.getOrNull(1)
        val ambiguousWithoutAnchor = absoluteAnchors.isEmpty() &&
            second != null &&
            abs(second.residual - best.residual) < 0.012 &&
            (second.firstDay != best.firstDay || second.lastDay != best.lastDay)
        if (ambiguousWithoutAnchor) return null
        val coveredDays = best.assigned.toSet()
        if (coveredDays.size < maxOf(12, maxDay / 2)) return null
        if ((coveredDays.maxOrNull() ?: 0) - (coveredDays.minOrNull() ?: maxDay) < maxDay - 7) {
            return null
        }

        return (1..maxDay).associateWith { day ->
            (best.intercept + (day - 1) * best.slope).roundToInt()
        }
    }

    private fun longestIncreasingDayChain(items: List<DayToken>): List<DayToken> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedWith(
            compareBy<DayToken> { it.token.centerX }
                .thenBy { it.day }
        )
        val lengths = IntArray(sorted.size) { 1 }
        val previous = IntArray(sorted.size) { -1 }

        for (right in sorted.indices) {
            for (left in 0 until right) {
                if (sorted[left].day < sorted[right].day &&
                    sorted[left].token.centerX < sorted[right].token.centerX &&
                    lengths[left] + 1 > lengths[right]
                ) {
                    lengths[right] = lengths[left] + 1
                    previous[right] = left
                }
            }
        }

        var index = lengths.indices.maxByOrNull { lengths[it] } ?: return emptyList()
        val result = mutableListOf<DayToken>()
        while (index >= 0) {
            result += sorted[index]
            index = previous[index]
        }
        return result.asReversed()
    }

    internal fun inferAllDayCenters(
        observed: Map<Int, Int>,
        maxDay: Int
    ): Map<Int, Int>? {
        val points = observed
            .filterKeys { it in 1..maxDay }
            .toSortedMap()
        if (points.size < 2) return null

        val slopes = points.entries.zipWithNext().mapNotNull { (left, right) ->
            val dayDelta = right.key - left.key
            val xDelta = right.value - left.value
            if (dayDelta > 0 && xDelta > 0) {
                xDelta.toDouble() / dayDelta.toDouble()
            } else {
                null
            }
        }.sorted()
        if (slopes.isEmpty()) return null
        val medianSlope = slopes[slopes.size / 2]
        if (medianSlope < 3.0) return null

        fun centerFor(day: Int): Int {
            points[day]?.let { return it }
            val lower = points.entries.lastOrNull { it.key < day }
            val upper = points.entries.firstOrNull { it.key > day }
            return when {
                lower != null && upper != null -> {
                    val ratio = (day - lower.key).toDouble() / (upper.key - lower.key).toDouble()
                    (lower.value + (upper.value - lower.value) * ratio).roundToInt()
                }
                lower != null -> (lower.value + (day - lower.key) * medianSlope).roundToInt()
                upper != null -> (upper.value - (upper.key - day) * medianSlope).roundToInt()
                else -> 0
            }
        }

        return (1..maxDay).associateWith(::centerFor)
    }

    private fun parseGeometryRows(
        lines: List<Text.Line>,
        header: HeaderGeometry
    ): List<RecognizedScheduleRow> {
        val dayCenters = header.dayCenters
        if (dayCenters.size < 2) return emptyList()
        val minDayX = header.gridStartX
        val spacing = medianDaySpacing(dayCenters)
        val maxDistance = max(12.0, spacing * 0.52)

        val linesBelowHeader = lines.filter { line ->
            (line.boundingBox?.centerY() ?: Int.MIN_VALUE) > header.bottom
        }
        val tokens = allTokens(linesBelowHeader)
        if (tokens.isEmpty()) return emptyList()

        val medianHeight = medianInt(tokens.map { it.box.height().coerceAtLeast(1) }).toDouble()
        val rowTolerance = max(8.0, medianHeight * 0.85)

        val lineRows = linesBelowHeader.mapNotNull { line ->
            parseGeometryLine(line, minDayX, dayCenters, maxDistance)
        }

        val visualClusters = clusterTokensByY(tokens, rowTolerance)
        val clusterRows = visualClusters.mapNotNull { row ->
            parseTokenRow(row.sortedBy { it.centerX }, minDayX, dayCenters, maxDistance)
        }

        val anchors = findRowAnchors(
            tokens = tokens,
            minDayX = minDayX,
            spacing = spacing,
            tolerance = rowTolerance
        )
        val anchoredRows = anchors.mapNotNull { anchor ->
            val rowTokens = tokensForAnchor(anchor, anchors, tokens, rowTolerance)
            val shiftTokens = rowTokens.mapNotNull { token ->
                canonicalShift(token.text)?.let { token to it }
            }
            val dayShifts = mapShiftTokensToDays(shiftTokens, dayCenters, maxDistance)
            // Ako je redak numeriran, zadržavamo osobu i kada OCR nije
            // pročitao nijednu oznaku smjene. Korisnik tada vidi da redak
            // postoji i može ga ručno provjeriti umjesto da osoba nestane.
            if (dayShifts.isEmpty() && anchor.rowNumber == null) {
                null
            } else {
                RecognizedScheduleRow(
                    rowNumber = anchor.rowNumber,
                    name = anchor.name,
                    dayShifts = dayShifts
                )
            }
        }

        return mergeRows(anchoredRows + clusterRows + lineRows)
    }

    private fun findRowAnchors(
        tokens: List<Token>,
        minDayX: Int,
        spacing: Double,
        tolerance: Double
    ): List<RowAnchor> {
        val nameBoundary = (minDayX - spacing * 0.30).roundToInt()
        val leftTokens = tokens.filter { token ->
            token.centerX < nameBoundary && canonicalShift(token.text) == null
        }
        if (leftTokens.isEmpty()) return emptyList()

        return clusterTokensByY(leftTokens, max(7.0, tolerance * 0.9))
            .mapNotNull { cluster ->
                val leftText = cluster
                    .sortedBy { it.centerX }
                    .joinToString(" ") { it.text }
                    .replace(spaces, " ")
                    .trim()
                val rowNumber = rowNumberRegex.find(leftText)
                    ?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
                val name = cleanName(leftText)
                val valid = validName(name)
                if (!valid && rowNumber == null) return@mapNotNull null
                RowAnchor(
                    rowNumber = rowNumber,
                    name = if (valid) name else "",
                    centerY = cluster.map { it.centerY }.average()
                )
            }
            .sortedBy { it.centerY }
    }

    private fun tokensForAnchor(
        anchor: RowAnchor,
        anchors: List<RowAnchor>,
        tokens: List<Token>,
        fallbackTolerance: Double
    ): List<Token> {
        val index = anchors.indexOf(anchor)
        val previous = anchors.getOrNull(index - 1)
        val next = anchors.getOrNull(index + 1)
        val lower = previous?.let { (it.centerY + anchor.centerY) / 2.0 }
            ?: (anchor.centerY - fallbackTolerance * 1.6)
        val upper = next?.let { (it.centerY + anchor.centerY) / 2.0 }
            ?: (anchor.centerY + fallbackTolerance * 1.6)
        return tokens.filter { it.centerY >= lower && it.centerY < upper }
    }

    private fun clusterTokensByY(
        tokens: List<Token>,
        tolerance: Double
    ): List<List<Token>> {
        val clusters = mutableListOf<MutableList<Token>>()
        tokens.sortedBy { it.centerY }.forEach { token ->
            val cluster = clusters.minByOrNull { row ->
                abs(row.map { it.centerY }.average() - token.centerY)
            }
            if (cluster != null &&
                abs(cluster.map { it.centerY }.average() - token.centerY) <= tolerance
            ) {
                cluster += token
            } else {
                clusters += mutableListOf(token)
            }
        }
        return clusters
    }

    private data class RowAlignment(
        val scale: Double,
        val offset: Double,
        val pivot: Double
    ) {
        fun adjust(x: Int): Double =
            pivot + (x - pivot) * scale + offset
    }

    private fun bestRowAlignment(
        xs: List<Int>,
        dayCenters: Map<Int, Int>
    ): RowAlignment {
        val pivot = dayCenters.values.average()
        if (xs.size < 4) return RowAlignment(1.0, 0.0, pivot)
        val spacing = medianDaySpacing(dayCenters)
        if (spacing <= 0.0) return RowAlignment(1.0, 0.0, pivot)

        var best = RowAlignment(1.0, 0.0, pivot)
        var bestScore = Double.POSITIVE_INFINITY
        for (scaleStep in -6..6) {
            val scale = 1.0 + scaleStep * 0.01
            for (offsetStep in -6..6) {
                val offset = spacing * offsetStep * 0.05
                val candidate = RowAlignment(scale, offset, pivot)
                val residual = xs.sumOf { x ->
                    val adjusted = candidate.adjust(x)
                    dayCenters.values.minOf { center -> abs(center - adjusted) }
                } / xs.size.toDouble() / spacing
                val penalty =
                    abs(scale - 1.0) * 0.10 +
                    abs(offset) / spacing * 0.015
                val score = residual + penalty
                if (score < bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }
        return if (bestScore <= 0.30) best else RowAlignment(1.0, 0.0, pivot)
    }

    private fun mapShiftTokensToDays(
        shiftTokens: List<Pair<Token, String>>,
        dayCenters: Map<Int, Int>,
        maxDistance: Double
    ): Map<Int, String> = buildMap {
        val alignment = bestRowAlignment(
            shiftTokens.map { it.first.centerX },
            dayCenters
        )
        shiftTokens.forEach { (token, code) ->
            val adjustedX = alignment.adjust(token.centerX)
            val nearest = dayCenters.minByOrNull { (_, center) ->
                abs(center - adjustedX)
            } ?: return@forEach
            if (abs(nearest.value - adjustedX) <= maxDistance) {
                put(nearest.key, code)
            }
        }
    }

    private fun medianInt(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun parseGeometryLine(
        line: Text.Line,
        minDayX: Int,
        dayCenters: Map<Int, Int>,
        maxDistance: Double
    ): RecognizedScheduleRow? {
        val tokens = line.elements.mapNotNull { element ->
            element.boundingBox?.let { Token(element.text.trim(), it) }
        }
        return parseTokenRow(tokens.sortedBy { it.centerX }, minDayX, dayCenters, maxDistance)
    }

    private fun parseTokenRow(
        tokens: List<Token>,
        minDayX: Int,
        dayCenters: Map<Int, Int>,
        maxDistance: Double
    ): RecognizedScheduleRow? {
        val shiftTokens = tokens.mapNotNull { token ->
            canonicalShift(token.text)?.let { token to it }
        }
        if (shiftTokens.isEmpty()) return null

        val firstShiftX = shiftTokens.minOf { it.first.centerX }
        val nameBoundary = minOf(firstShiftX, minDayX)
        val leftText = tokens
            .filter { token ->
                token.centerX < nameBoundary && canonicalShift(token.text) == null
            }
            .joinToString(" ") { it.text }
            .replace(spaces, " ")
            .trim()

        val rowNumber = rowNumberRegex.find(leftText)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
        val name = cleanName(leftText)
        val valid = validName(name)
        if (!valid && rowNumber == null) return null

        val dayShifts = buildMap<Int, String> {
            shiftTokens.forEach { (token, code) ->
                val nearest = dayCenters.minByOrNull { (_, center) ->
                    abs(center - token.centerX)
                } ?: return@forEach
                if (abs(nearest.value - token.centerX) <= maxDistance) {
                    put(nearest.key, code)
                }
            }
        }
        return if (dayShifts.isEmpty()) null
        else RecognizedScheduleRow(rowNumber, if (valid) name else "", dayShifts)
    }

    private fun medianDaySpacing(dayCenters: Map<Int, Int>): Double {
        val diffs = dayCenters.entries
            .sortedBy { it.key }
            .zipWithNext()
            .map { (a, b) -> abs(b.value - a.value).toDouble() }
            .filter { it > 0.0 }
            .sorted()
        return if (diffs.isEmpty()) 32.0 else diffs[diffs.size / 2]
    }

    internal fun parseRow(line: String): RecognizedScheduleRow? {
        val explicitPairs = explicitDayShiftRegex.findAll(line)
            .mapNotNull { match ->
                val day = match.groupValues[1].toIntOrNull()
                val code = canonicalShift(match.groupValues[2])
                if (day != null && code != null) day to code else null
            }
            .toMap()

        val shifts = shiftRegex.findAll(line)
            .mapNotNull { canonicalShift(it.value) }
            .toList()
        if (shifts.isEmpty()) return null

        val rowNumber = rowNumberRegex.find(line)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
        val name = cleanName(line)
        if (!validName(name)) return null

        // Bez geometrije tablice ne komprimiramo prepoznate oznake ulijevo.
        // Time izbjegavamo pogrešno spremanje npr. dana 4 kao dana 3 kada je dan 3 prazan.
        val dayShifts = explicitPairs
        return RecognizedScheduleRow(
            rowNumber = rowNumber,
            name = name,
            dayShifts = dayShifts
        )
    }

    private fun cleanName(raw: String): String {
        val withoutNumber = raw.replaceFirst(rowNumberRegex, "")
        return withoutNumber
            .replace(explicitDayShiftRegex, " ")
            .replace(shiftRegex, " ")
            .replace(Regex("""\b\d{1,2}([./-]\d{1,2})?\b"""), " ")
            .replace(spaces, " ")
            .trim(' ', '-', '|', ':', ';', '.', ',')
    }

    private fun validName(name: String): Boolean {
        if (name.length !in 3..64 || name.count(Char::isLetter) < 3) return false
        val normalized = normalizeAscii(name)
        if (normalized in setOf(
                "IME PREZIME",
                "IME I PREZIME",
                "DJELATNIK",
                "ZAPOSLENIK",
                "RADNIK",
                "RB",
                "NOSAC BOLESNIKA",
                "NOSAC BOLESNIKA PREZIME IME"
            )
        ) {
            return false
        }
        val letters = name.count(Char::isLetter)
        val digits = name.count(Char::isDigit)
        if (digits > 2 || letters.toDouble() / name.length.coerceAtLeast(1) < 0.52) return false
        val words = normalizeAscii(name)
            .split(Regex("""[^A-Z]+"""))
            .filter { it.length >= 2 }
        return words.isNotEmpty()
    }

    private fun nameFingerprint(value: String): String =
        normalizeAscii(value)
            .split(Regex("""[^A-Z0-9]+"""))
            .filter(String::isNotBlank)
            .sorted()
            .joinToString("")

    private fun nameWords(value: String): Set<String> =
        normalizeAscii(value)
            .split(Regex("""[^A-Z]+"""))
            .filter { it.length >= 2 }
            .toSet()

    private fun editSimilarity(left: String, right: String): Double {
        val a = normalizeAscii(left).filter(Char::isLetterOrDigit)
        val b = normalizeAscii(right).filter(Char::isLetterOrDigit)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        val distance = previous[b.length]
        return 1.0 - distance.toDouble() / maxOf(a.length, b.length).toDouble()
    }

    private fun namesProbablySame(left: String, right: String): Boolean {
        if (!validName(left) || !validName(right)) return false
        if (nameFingerprint(left) == nameFingerprint(right)) return true

        val leftWords = nameWords(left)
        val rightWords = nameWords(right)
        if (leftWords.size != rightWords.size || leftWords.isEmpty()) return false

        val overlap = leftWords.intersect(rightWords).size
        val edit = editSimilarity(left, right)

        // Without a trusted row number, identity matching must be conservative.
        // Similar real names such as IVAN HORVAT / IVANA HORVAT must never be
        // collapsed into one employee merely because their edit distance is low.
        return edit >= 0.94 && overlap >= maxOf(1, leftWords.size - 1)
    }

    private fun nameQuality(value: String): Int {
        if (!validName(value)) return Int.MIN_VALUE / 4
        val words = nameWords(value)
        val letters = value.count(Char::isLetter)
        val punctuation = value.count { !it.isLetter() && !it.isWhitespace() && it != '-' && it != '\'' }
        return letters +
            when (words.size) {
                2 -> 28
                3 -> 24
                4 -> 16
                1 -> 2
                else -> -8
            } -
            punctuation * 8 -
            value.count(Char::isDigit) * 10 -
            maxOf(0, value.length - 42)
    }

    private fun betterName(left: String, right: String): String = when {
        !validName(left) -> right
        !validName(right) -> left
        nameQuality(right) > nameQuality(left) -> right
        nameQuality(right) < nameQuality(left) -> left
        right.length < left.length -> right
        else -> left
    }

    internal fun mergeRows(rows: List<RecognizedScheduleRow>): List<RecognizedScheduleRow> {
        val merged = mutableListOf<RecognizedScheduleRow>()
        rows.forEach { row ->
            val valid = validName(row.name)
            val numbered = row.rowNumber?.let { it in 1..100 } == true
            if (!valid && !numbered) return@forEach

            val index = merged.indexOfFirst { existing ->
                val sameRow = row.rowNumber != null &&
                    existing.rowNumber != null &&
                    row.rowNumber == existing.rowNumber
                val conflictingRows = row.rowNumber != null &&
                    existing.rowNumber != null &&
                    row.rowNumber != existing.rowNumber
                val sameName = !conflictingRows && namesProbablySame(existing.name, row.name)
                sameRow || sameName
            }
            if (index < 0) {
                merged += row.copy(
                    name = if (valid) row.name.trim() else "",
                    supportCount = row.supportCount.coerceAtLeast(1)
                )
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    rowNumber = existing.rowNumber ?: row.rowNumber,
                    name = betterName(existing.name, row.name),
                    // Ranije geometrijski rezultat ima prednost kod konflikta istog dana.
                    dayShifts = (row.dayShifts + existing.dayShifts).toSortedMap(),
                    supportCount = existing.supportCount + row.supportCount.coerceAtLeast(1)
                )
            }
        }
        val filtered = filterRowNumberOutliers(merged)
        return filtered.sortedWith(
            compareBy<RecognizedScheduleRow> { it.rowNumber ?: Int.MAX_VALUE }
                .thenBy { normalizeAscii(it.name) }
        )
    }

    internal fun finalizeRows(rows: List<RecognizedScheduleRow>): List<RecognizedScheduleRow> {
        val merged = mergeRows(rows)
        val numbered = merged.filter { it.rowNumber != null }
        if (numbered.size < 5) {
            return merged.filter { row ->
                row.rowNumber != null || row.supportCount >= 2 || merged.size < 12
            }
        }

        val numbers = numbered.mapNotNull { it.rowNumber }.distinct().sorted()
        val first = numbers.firstOrNull() ?: return merged
        val last = numbers.lastOrNull() ?: return merged
        val span = (last - first + 1).coerceAtLeast(1)
        val denseRoster = first <= 3 && numbers.size.toDouble() / span.toDouble() >= 0.72
        val missingSlots = (span - numbers.size).coerceAtLeast(0)

        val authoritative = numbered.toMutableList()
        val unmatched = mutableListOf<RecognizedScheduleRow>()

        merged.filter { it.rowNumber == null }.forEach { row ->
            val targetIndex = authoritative.indices
                .filter { index -> namesProbablySame(authoritative[index].name, row.name) }
                .maxByOrNull { index -> editSimilarity(authoritative[index].name, row.name) }

            if (targetIndex != null) {
                val target = authoritative[targetIndex]
                authoritative[targetIndex] = target.copy(
                    name = betterName(target.name, row.name),
                    dayShifts = (row.dayShifts + target.dayShifts).toSortedMap(),
                    supportCount = target.supportCount + row.supportCount
                )
            } else if (row.supportCount >= 3 && validName(row.name)) {
                unmatched += row
            }
        }

        val retainedUnnumbered = if (denseRoster) {
            // A dense numbered roster gives us an expected employee count. Keep
            // only repeated unnumbered names that can fill actually missing row
            // numbers; this preserves a real employee whose number cell failed
            // OCR without reintroducing dozens of one-off ghost names.
            unmatched
                .sortedWith(
                    compareByDescending<RecognizedScheduleRow> { it.supportCount }
                        .thenByDescending { nameQuality(it.name) }
                        .thenByDescending { it.dayShifts.size }
                )
                .take(missingSlots)
        } else {
            unmatched
        }
        authoritative += retainedUnnumbered

        return authoritative.sortedWith(
            compareBy<RecognizedScheduleRow> { it.rowNumber ?: Int.MAX_VALUE }
                .thenBy { normalizeAscii(it.name) }
        )
    }

    private fun filterRowNumberOutliers(
        rows: List<RecognizedScheduleRow>
    ): List<RecognizedScheduleRow> {
        val numbered = rows
            .mapNotNull { it.rowNumber }
            .distinct()
            .sorted()
        if (numbered.size < 6) return rows

        val positiveGaps = numbered.zipWithNext()
            .map { (left, right) -> right - left }
            .filter { it > 0 }
            .sorted()
        val medianGap = positiveGaps.getOrNull(positiveGaps.size / 2) ?: return rows
        val splitGap = maxOf(12, medianGap * 6)

        val clusters = mutableListOf<MutableList<Int>>()
        numbered.forEach { number ->
            val current = clusters.lastOrNull()
            if (current != null && number - current.last() <= splitGap) {
                current += number
            } else {
                clusters += mutableListOf(number)
            }
        }
        val best = clusters.maxByOrNull { it.size } ?: return rows
        if (best.size * 10 < numbered.size * 6) return rows

        val accepted = best.toSet()
        return rows.filter { row ->
            row.rowNumber == null || row.rowNumber in accepted
        }
    }

    internal fun detectMonth(text: String): YearMonth? {
        val upper = normalizeAscii(text.replace('\u00A0', ' '))

        val yearFirst = Regex("""\b(20\d{2}) *[./-] *(0?[1-9]|1[0-2])\b""")
            .find(upper)
        if (yearFirst != null) {
            val year = yearFirst.groupValues[1].toInt()
            val month = yearFirst.groupValues[2].toInt()
            return YearMonth.of(year, month)
        }

        val monthFirst = Regex("""\b(0?[1-9]|1[0-2]) *[./-] *(20\d{2})\b""")
            .find(upper)
        if (monthFirst != null) {
            val month = monthFirst.groupValues[1].toInt()
            val year = monthFirst.groupValues[2].toInt()
            return YearMonth.of(year, month)
        }

        val labelled = Regex(
            """\b(?:MJESEC|MJESECA|ZA)\s*[:.-]?\s*(0?[1-9]|1[0-2])\s+(20\d{2})\b"""
        ).find(upper)
        if (labelled != null) {
            return YearMonth.of(
                labelled.groupValues[2].toInt(),
                labelled.groupValues[1].toInt()
            )
        }

        val month = monthNames.entries
            .firstOrNull { (name, _) ->
                Regex("""(?:^|\s)$name(?:\s|[.,;:/-]|$)""").containsMatchIn(upper)
            }
            ?.value
            ?: return null
        val year = Regex("""20\d{2}""")
            .find(upper)
            ?.value
            ?.toIntOrNull()
            ?: return null
        return YearMonth.of(year, month)
    }
}

object ScheduleOcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun recognize(
        bitmap: Bitmap,
        onSuccess: (RecognizedSchedule) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { firstResult ->
                val first = ScheduleOcrParser.parse(firstResult)
                // Pixel-grid detection is intentionally independent from OCR.
                // A detected dense table forces the full recovery pipeline even
                // when the first pass already found many rows, because a 20–30
                // person schedule must not be accepted after only a partial read.
                val detectedTable = ScheduleTableDetector.cropForRecovery(bitmap)
                if (!needsDeepRecovery(first, bitmap) && detectedTable == null) {
                    onSuccess(first)
                    return@addOnSuccessListener
                }

                // Recovery OCR works on the detected table instead of spending
                // pixels on monitor chrome, desk area and page margins.
                val recoverySource = detectedTable ?: bitmap
                val forceDenseRecovery = detectedTable != null
                fun finish(schedule: RecognizedSchedule) {
                    recycleTemporary(recoverySource, bitmap)
                    onSuccess(
                        schedule.copy(
                            rows = ScheduleOcrParser.finalizeRows(schedule.rows)
                                .filter { row ->
                                    row.name.count(Char::isLetter) >= 3
                                }
                        )
                    )
                }

                val enhanced = enhanceForOcr(recoverySource)
                recognizer.process(InputImage.fromBitmap(enhanced, 0))
                    .addOnSuccessListener { secondResult ->
                        val second = ScheduleOcrParser.parse(
                            secondResult,
                            monthHint = first.month
                        )
                        val merged = mergeSchedules(first, second)
                        if (forceDenseRecovery || needsStripeRecovery(merged, recoverySource)) {
                            recognizeStripes(
                                source = recoverySource,
                                baseline = merged,
                                onSuccess = ::finish
                            )
                        } else {
                            finish(merged)
                        }
                    }
                    .addOnFailureListener {
                        if (forceDenseRecovery || needsStripeRecovery(first, recoverySource)) {
                            recognizeStripes(
                                source = recoverySource,
                                baseline = first,
                                onSuccess = ::finish
                            )
                        } else {
                            finish(first)
                        }
                    }
                    .addOnCompleteListener {
                        recycleTemporary(enhanced, recoverySource)
                    }
            }
            .addOnFailureListener(onError)
    }

    private fun recycleTemporary(candidate: Bitmap, source: Bitmap) {
        if (candidate !== source && !candidate.isRecycled) {
            candidate.recycle()
        }
    }

    private fun needsRecoveryPass(schedule: RecognizedSchedule): Boolean {
        val rows = schedule.rows
        if (rows.isEmpty()) return true
        val mapped = rows.sumOf { it.dayShifts.size }
        val expectedPerRow = minOf(schedule.month?.lengthOfMonth() ?: 31, 12)
        return mapped < maxOf(18, rows.size * expectedPerRow)
    }

    private fun hasMissingNumberedRows(schedule: RecognizedSchedule): Boolean {
        val numbers = schedule.rows
            .mapNotNull { it.rowNumber }
            .filter { it in 1..100 }
            .distinct()
            .sorted()
        if (numbers.size < 5) return false
        val first = numbers.first()
        val last = numbers.last()
        if (first > 3 || last - first + 1 < 8) return false
        val expected = last - first + 1
        return numbers.size * 100 < expected * 88
    }

    private fun needsDeepRecovery(
        schedule: RecognizedSchedule,
        source: Bitmap
    ): Boolean {
        if (schedule.rows.any { row -> row.name.count(Char::isLetter) < 3 }) return true
        if (needsRecoveryPass(schedule) || hasMissingNumberedRows(schedule)) return true
        return source.width >= 1600 &&
            source.height >= 1000 &&
            schedule.rows.size in 1..15
    }

    private fun needsStripeRecovery(
        schedule: RecognizedSchedule,
        source: Bitmap
    ): Boolean = source.width >= 900 &&
        source.height >= 450 &&
        (needsRecoveryPass(schedule) || hasMissingNumberedRows(schedule) || schedule.rows.size in 1..15)

    private fun recognizeStripes(
        source: Bitmap,
        baseline: RecognizedSchedule,
        onSuccess: (RecognizedSchedule) -> Unit
    ) {
        val ranges = stripeRanges(source.height)
        if (ranges.isEmpty()) {
            recognizeDayBands(source, baseline, onSuccess)
            return
        }

        fun processStripe(
            index: Int,
            accumulated: RecognizedSchedule
        ) {
            if (index >= ranges.size) {
                recognizeDayBands(
                    source = source,
                    baseline = accumulated,
                    onSuccess = onSuccess
                )
                return
            }

            val range = ranges[index]
            val stripe = createEnhancedStripe(source, range.first, range.last + 1)
            recognizer.process(InputImage.fromBitmap(stripe, 0))
                .addOnSuccessListener { stripeResult ->
                    val parsed = ScheduleOcrParser.parse(
                        stripeResult,
                        monthHint = accumulated.month
                    )
                    processStripe(
                        index + 1,
                        mergeSchedules(accumulated, parsed)
                    )
                }
                .addOnFailureListener {
                    processStripe(index + 1, accumulated)
                }
                .addOnCompleteListener {
                    if (!stripe.isRecycled) stripe.recycle()
                }
        }

        processStripe(0, baseline)
    }

    private data class DayBand(
        val startRatio: Float,
        val endRatio: Float
    )

    private fun recognizeDayBands(
        source: Bitmap,
        baseline: RecognizedSchedule,
        onSuccess: (RecognizedSchedule) -> Unit
    ) {
        val mapped = baseline.rows.sumOf { it.dayShifts.size }
        val maxDay = baseline.month?.lengthOfMonth() ?: 31
        val target = maxOf(24, baseline.rows.size * minOf(maxDay, 18))
        val sparseNumberedRow = baseline.rows.any { row ->
            row.rowNumber != null && row.dayShifts.size < 3
        }
        if (mapped >= target &&
            !hasMissingNumberedRows(baseline) &&
            !sparseNumberedRow
        ) {
            recognizeRosterColumn(source, baseline, onSuccess)
            return
        }

        val bands = dayBands()
        fun processBand(index: Int, accumulated: RecognizedSchedule) {
            if (index >= bands.size) {
                if (needsFocusedRecovery(accumulated)) {
                    recognizeFocusedTiles(
                        source = source,
                        baseline = accumulated,
                        onSuccess = { focused ->
                            recognizeRosterColumn(source, focused, onSuccess)
                        }
                    )
                } else {
                    recognizeRosterColumn(source, accumulated, onSuccess)
                }
                return
            }

            val band = bands[index]
            val composite = createEnhancedDayBandComposite(
                source = source,
                startRatio = band.startRatio,
                endRatio = band.endRatio
            )
            recognizer.process(InputImage.fromBitmap(composite, 0))
                .addOnSuccessListener { result ->
                    val parsed = ScheduleOcrParser.parse(
                        result,
                        monthHint = accumulated.month
                    )
                    processBand(
                        index + 1,
                        mergeSchedules(accumulated, parsed)
                    )
                }
                .addOnFailureListener {
                    processBand(index + 1, accumulated)
                }
                .addOnCompleteListener {
                    if (!composite.isRecycled) composite.recycle()
                }
        }
        processBand(0, baseline)
    }

    private fun dayBands(): List<DayBand> = listOf(
        // Cijeli mjesečni raspored ima vrlo uske ćelije. Umjesto četiri široka
        // pojasa koristimo osam preklapajućih mikro-pojaseva. Svaki prolaz
        // povećava samo 4–7 stupaca dana pa ML Kit dobiva znatno više piksela
        // po oznaci D/N/GO/BO/PD/SD. Preklapanje štiti rubne stupce i
        // perspektivno snimljene tablice.
        DayBand(0.12f, 0.28f),
        DayBand(0.22f, 0.38f),
        DayBand(0.32f, 0.48f),
        DayBand(0.42f, 0.58f),
        DayBand(0.52f, 0.68f),
        DayBand(0.62f, 0.78f),
        DayBand(0.72f, 0.88f),
        DayBand(0.82f, 1.00f)
    )

    private fun createEnhancedDayBandComposite(
        source: Bitmap,
        startRatio: Float,
        endRatio: Float
    ): Bitmap {
        val rosterWidth = (source.width * 0.34f).roundToInt()
            .coerceIn(1, source.width)
        val gridStart = (source.width * startRatio).roundToInt()
            .coerceIn(0, source.width - 1)
        val gridEnd = (source.width * endRatio).roundToInt()
            .coerceIn(gridStart + 1, source.width)
        val gridWidth = gridEnd - gridStart
        val rawWidth = rosterWidth + gridWidth

        val targetPixels = 8_500_000.0
        val pixelScale = kotlin.math.sqrt(
            targetPixels / (rawWidth.toDouble() * source.height.toDouble())
        )
        val edgeScale = 4600.0 / rawWidth.toDouble()
        val scale = minOf(2.85, pixelScale, edgeScale).coerceAtLeast(0.75)
        val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val rosterOutWidth = (rosterWidth * scale).roundToInt()
            .coerceIn(1, width)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.50f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        grayscale.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscale)
            isFilterBitmap = true
        }
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(
            source,
            Rect(0, 0, rosterWidth, source.height),
            Rect(0, 0, rosterOutWidth, height),
            paint
        )
        canvas.drawBitmap(
            source,
            Rect(gridStart, 0, gridEnd, source.height),
            Rect(rosterOutWidth, 0, width, height),
            paint
        )
        return output
    }

    private fun needsFocusedRecovery(schedule: RecognizedSchedule): Boolean {
        if (schedule.rows.size < 4) return false
        val mapped = schedule.rows.sumOf { it.dayShifts.size }
        return mapped < maxOf(24, schedule.rows.size * 8)
    }

    private data class FocusedTile(
        val rowStart: Float,
        val rowEnd: Float,
        val dayStart: Float,
        val dayEnd: Float
    )

    private fun recognizeFocusedTiles(
        source: Bitmap,
        baseline: RecognizedSchedule,
        onSuccess: (RecognizedSchedule) -> Unit
    ) {
        val rowBands = listOf(
            0.10f to 0.36f,
            0.30f to 0.58f,
            0.52f to 0.80f,
            0.74f to 1.00f
        )
        val dayBands = listOf(
            0.16f to 0.48f,
            0.42f to 0.74f,
            0.68f to 1.00f
        )
        val tiles = rowBands.flatMap { row ->
            dayBands.map { day ->
                FocusedTile(row.first, row.second, day.first, day.second)
            }
        }

        fun process(index: Int, accumulated: RecognizedSchedule) {
            if (index >= tiles.size) {
                onSuccess(accumulated)
                return
            }
            val tile = tiles[index]
            val bitmap = createEnhancedFocusedTile(source, tile)
            if (bitmap == null) {
                process(index + 1, accumulated)
                return
            }

            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val parsed = ScheduleOcrParser.parse(
                        result,
                        monthHint = accumulated.month
                    )
                    process(index + 1, mergeSchedules(accumulated, parsed))
                }
                .addOnFailureListener {
                    process(index + 1, accumulated)
                }
                .addOnCompleteListener {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
        }

        process(0, baseline)
    }

    private fun createEnhancedFocusedTile(
        source: Bitmap,
        tile: FocusedTile
    ): Bitmap? = runCatching {
        val rosterWidth = (source.width * 0.34f).roundToInt()
            .coerceIn(1, source.width)
        val gridStart = (source.width * tile.dayStart).roundToInt()
            .coerceIn(0, source.width - 1)
        val gridEnd = (source.width * tile.dayEnd).roundToInt()
            .coerceIn(gridStart + 1, source.width)
        val gridWidth = gridEnd - gridStart

        // Keep the real day-number header and append only a subset of employee
        // rows. This increases the effective pixels per name and per one-letter
        // shift cell without losing exact day-column geometry.
        val headerHeight = (source.height * 0.16f).roundToInt()
            .coerceIn(1, source.height)
        val bodyTop = maxOf(
            headerHeight,
            (source.height * tile.rowStart).roundToInt()
                .coerceIn(0, source.height - 1)
        )
        val bodyBottom = (source.height * tile.rowEnd).roundToInt()
            .coerceIn(bodyTop + 1, source.height)
        val bodyHeight = bodyBottom - bodyTop
        val rawWidth = rosterWidth + gridWidth
        val rawHeight = headerHeight + bodyHeight

        val targetPixels = 6_500_000.0
        val pixelScale = kotlin.math.sqrt(
            targetPixels / (rawWidth.toDouble() * rawHeight.toDouble())
        )
        val edgeScale = 4200.0 / rawWidth.toDouble()
        val scale = minOf(3.25, pixelScale, edgeScale).coerceAtLeast(1.05)

        val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (rawHeight * scale).roundToInt().coerceAtLeast(1)
        val rosterOut = (rosterWidth * scale).roundToInt()
            .coerceIn(1, maxOf(1, width - 1))
        val headerOut = (headerHeight * scale).roundToInt()
            .coerceIn(1, maxOf(1, height - 1))

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.62f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        grayscale.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscale)
            isFilterBitmap = true
        }
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Header.
        canvas.drawBitmap(
            source,
            Rect(0, 0, rosterWidth, headerHeight),
            Rect(0, 0, rosterOut, headerOut),
            paint
        )
        canvas.drawBitmap(
            source,
            Rect(gridStart, 0, gridEnd, headerHeight),
            Rect(rosterOut, 0, width, headerOut),
            paint
        )

        // Selected employee rows.
        canvas.drawBitmap(
            source,
            Rect(0, bodyTop, rosterWidth, bodyBottom),
            Rect(0, headerOut, rosterOut, height),
            paint
        )
        canvas.drawBitmap(
            source,
            Rect(gridStart, bodyTop, gridEnd, bodyBottom),
            Rect(rosterOut, headerOut, width, height),
            paint
        )
        output
    }.getOrNull()

    private fun recognizeRosterColumn(
        source: Bitmap,
        baseline: RecognizedSchedule,
        onSuccess: (RecognizedSchedule) -> Unit
    ) {
        val crop = createEnhancedRosterColumn(source)
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { rosterResult ->
                val rosterRows = ScheduleOcrParser.parseRosterRows(rosterResult)
                val merged = baseline.copy(
                    rows = ScheduleOcrParser.mergeRows(baseline.rows + rosterRows),
                    rawText = if (rosterResult.text.length > baseline.rawText.length) {
                        rosterResult.text
                    } else {
                        baseline.rawText
                    }
                )
                recognizeRosterBands(source, merged, onSuccess)
            }
            .addOnFailureListener {
                recognizeRosterBands(source, baseline, onSuccess)
            }
            .addOnCompleteListener {
                if (!crop.isRecycled) crop.recycle()
            }
    }

    private fun recognizeRosterBands(
        source: Bitmap,
        baseline: RecognizedSchedule,
        onSuccess: (RecognizedSchedule) -> Unit
    ) {
        val ranges = listOf(
            0f to 0.28f,
            0.18f to 0.46f,
            0.36f to 0.64f,
            0.54f to 0.82f,
            0.72f to 1.00f
        )

        fun processBand(index: Int, accumulated: RecognizedSchedule) {
            if (index >= ranges.size) {
                onSuccess(accumulated)
                return
            }
            val (startRatio, endRatio) = ranges[index]
            val crop = createEnhancedRosterBand(source, startRatio, endRatio)
            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { rosterResult ->
                    val rows = ScheduleOcrParser.parseRosterRows(rosterResult)
                    processBand(
                        index + 1,
                        accumulated.copy(
                            rows = ScheduleOcrParser.mergeRows(accumulated.rows + rows),
                            rawText = if (rosterResult.text.length > accumulated.rawText.length) {
                                rosterResult.text
                            } else {
                                accumulated.rawText
                            }
                        )
                    )
                }
                .addOnFailureListener {
                    processBand(index + 1, accumulated)
                }
                .addOnCompleteListener {
                    if (!crop.isRecycled) crop.recycle()
                }
        }
        processBand(0, baseline)
    }

    private fun createEnhancedRosterBand(
        source: Bitmap,
        startRatio: Float,
        endRatio: Float
    ): Bitmap {
        val cropWidth = (source.width * 0.44f).roundToInt().coerceIn(1, source.width)
        val top = (source.height * startRatio).roundToInt().coerceIn(0, source.height - 1)
        val bottom = (source.height * endRatio).roundToInt().coerceIn(top + 1, source.height)
        val cropHeight = bottom - top
        val targetPixels = 4_500_000.0
        val pixelScale = kotlin.math.sqrt(
            targetPixels / (cropWidth.toDouble() * cropHeight.toDouble())
        )
        val edgeScale = 3200.0 / cropWidth.toDouble()
        val scale = minOf(2.80, pixelScale, edgeScale).coerceAtLeast(1.0)
        val width = (cropWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (cropHeight * scale).roundToInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.48f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        grayscale.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        Canvas(output).drawBitmap(
            source,
            Rect(0, top, cropWidth, bottom),
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(grayscale)
                isFilterBitmap = true
            }
        )
        return output
    }

    private fun createEnhancedRosterColumn(source: Bitmap): Bitmap {
        val cropWidth = (source.width * 0.44f).roundToInt().coerceIn(1, source.width)
        val targetPixels = 5_000_000.0
        val pixelScale = kotlin.math.sqrt(
            targetPixels / (cropWidth.toDouble() * source.height.toDouble())
        )
        val edgeScale = 3000.0 / cropWidth.toDouble()
        val scale = minOf(2.20, pixelScale, edgeScale).coerceAtLeast(1.0)
        val width = (cropWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.42f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        grayscale.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        Canvas(output).drawBitmap(
            source,
            Rect(0, 0, cropWidth, source.height),
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(grayscale)
                isFilterBitmap = true
            }
        )
        return output
    }

    private fun stripeRanges(height: Int): List<IntRange> {
        if (height < 620) return emptyList()
        // Pet užih, preklapajućih pojasa povećava efektivnu visinu svakog
        // retka. Kod fotografije cijelog rasporeda s 20–40 djelatnika ovo je
        // preciznije od nekoliko velikih polu-okvira i još uvijek se obrađuje
        // sekvencijalno kako ne bismo držali više velikih bitmapa u memoriji.
        fun range(start: Float, end: Float): IntRange {
            val top = (height * start).roundToInt().coerceIn(0, height - 1)
            val bottom = (height * end).roundToInt().coerceIn(top + 1, height)
            return top until bottom
        }
        return listOf(
            range(0.00f, 0.30f),
            range(0.18f, 0.48f),
            range(0.36f, 0.66f),
            range(0.54f, 0.84f),
            range(0.72f, 1.00f)
        )
    }

    private fun createEnhancedStripe(
        source: Bitmap,
        top: Int,
        bottom: Int
    ): Bitmap {
        val safeTop = top.coerceIn(0, source.height - 1)
        val safeBottom = bottom.coerceIn(safeTop + 1, source.height)
        val cropHeight = safeBottom - safeTop
        val targetPixels = 7_000_000.0
        val pixelScale = kotlin.math.sqrt(
            targetPixels / (source.width.toDouble() * cropHeight.toDouble())
        )
        val edgeScale = 6000.0 / source.width.toDouble()
        val scale = minOf(1.65, pixelScale, edgeScale).coerceAtLeast(0.25)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (cropHeight * scale).roundToInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.40f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        grayscale.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        Canvas(output).drawBitmap(
            source,
            Rect(0, safeTop, source.width, safeBottom),
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(grayscale)
                isFilterBitmap = true
            }
        )
        return output
    }

    private fun mergeSchedules(
        first: RecognizedSchedule,
        second: RecognizedSchedule
    ): RecognizedSchedule {
        val rows = ScheduleOcrParser.mergeRows(first.rows + second.rows)
        val rawText = if (second.rawText.length > first.rawText.length) {
            second.rawText
        } else {
            first.rawText
        }
        return RecognizedSchedule(
            month = first.month ?: second.month,
            rows = rows,
            rawText = rawText
        )
    }

    private fun enhanceForOcr(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.28f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayscale.postConcat(contrastMatrix)
        Canvas(output).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(grayscale)
                isFilterBitmap = true
            }
        )
        return output
    }
}
