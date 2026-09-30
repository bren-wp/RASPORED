package hr.raspored.app.ocr

import android.graphics.Bitmap
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
    val dayShifts: Map<Int, String>
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
        """(?<!\d)([1-9]|[12]\d|3[01])\s*[:.)\-]?\s*(GO|G0|BO|B0|PD|SD|D|N)(?![\p{L}])""",
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
        raw.trim().trim('.', ',', ';', ':').uppercase(Locale.ROOT)
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
        val observedDays: Set<Int>
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

    fun parse(result: Text): RecognizedSchedule {
        val rawText = result.text.replace('\u00A0', ' ')
        val month = detectMonth(rawText)
        val lines = result.textBlocks.flatMap { it.lines }
        val header = findDayHeader(lines, month?.lengthOfMonth() ?: 31)
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
            observedDays = observed.keys
        )
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
        val minDayX = dayCenters.values.minOrNull() ?: return emptyList()
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
        val anchoredRows = anchors.map { anchor ->
            val rowTokens = tokensForAnchor(anchor, anchors, tokens, rowTolerance)
            val shiftTokens = rowTokens.mapNotNull { token ->
                canonicalShift(token.text)?.let { token to it }
            }
            val dayShifts = mapShiftTokensToDays(shiftTokens, dayCenters, maxDistance)
            RecognizedScheduleRow(
                rowNumber = anchor.rowNumber,
                name = anchor.name,
                dayShifts = dayShifts
            )
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
                if (!validName(name)) return@mapNotNull null
                RowAnchor(
                    rowNumber = rowNumber,
                    name = name,
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

    private fun mapShiftTokensToDays(
        shiftTokens: List<Pair<Token, String>>,
        dayCenters: Map<Int, Int>,
        maxDistance: Double
    ): Map<Int, String> = buildMap {
        shiftTokens.forEach { (token, code) ->
            val nearest = dayCenters.minByOrNull { (_, center) ->
                abs(center - token.centerX)
            } ?: return@forEach
            if (abs(nearest.value - token.centerX) <= maxDistance) {
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
        if (!validName(name)) return null

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
        else RecognizedScheduleRow(rowNumber, name, dayShifts)
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
        if (name.length < 3 || name.count(Char::isLetter) < 3) return false
        val normalized = normalizeAscii(name)
        if (normalized in setOf("IME PREZIME", "IME I PREZIME", "DJELATNIK", "ZAPOSLENIK")) {
            return false
        }
        return true
    }

    private fun mergeRows(rows: List<RecognizedScheduleRow>): List<RecognizedScheduleRow> {
        val merged = mutableListOf<RecognizedScheduleRow>()
        rows.forEach { row ->
            val normalizedName = normalizeAscii(row.name).replace(spaces, " ").trim()
            val index = merged.indexOfFirst { existing ->
                val sameRow = row.rowNumber != null &&
                    existing.rowNumber != null &&
                    row.rowNumber == existing.rowNumber
                val sameName = normalizeAscii(existing.name)
                    .replace(spaces, " ")
                    .trim() == normalizedName
                sameRow || sameName
            }
            if (index < 0) {
                merged += row
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    rowNumber = existing.rowNumber ?: row.rowNumber,
                    name = if (existing.name.length >= row.name.length) existing.name else row.name,
                    // Ranije geometrijski rezultat ima prednost kod konflikta istog dana.
                    dayShifts = (row.dayShifts + existing.dayShifts).toSortedMap()
                )
            }
        }
        return merged.sortedWith(
            compareBy<RecognizedScheduleRow> { it.rowNumber ?: Int.MAX_VALUE }
                .thenBy { normalizeAscii(it.name) }
        )
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
            .addOnSuccessListener { result ->
                onSuccess(ScheduleOcrParser.parse(result))
            }
            .addOnFailureListener(onError)
    }
}
