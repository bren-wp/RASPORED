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
    private val exactShiftRegex = Regex(
        """^(GO|G0|BO|B0|PD|SD|D|N)[.,;:]?$""",
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

    fun parse(result: Text): RecognizedSchedule {
        val lines = result.textBlocks.flatMap { it.lines }
        val header = findBestDayHeader(lines)
        val geometryRows = if (header != null && header.second.size >= 5) {
            parseGeometryRows(
                lines = lines,
                headerBottom = header.first.boundingBox?.bottom ?: Int.MIN_VALUE,
                dayCenters = header.second
            )
        } else {
            emptyList()
        }

        val textFallback = parse(result.text)
        return RecognizedSchedule(
            month = detectMonth(result.text),
            rows = mergeRows(if (geometryRows.isNotEmpty()) geometryRows else textFallback.rows),
            rawText = result.text.replace('\u00A0', ' ')
        )
    }

    fun parse(text: String): RecognizedSchedule {
        val normalized = text.replace('\u00A0', ' ')
        val rows = normalized.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseRow)
            .filter { it.dayShifts.isNotEmpty() }
            .toList()

        return RecognizedSchedule(
            month = detectMonth(normalized),
            rows = mergeRows(rows),
            rawText = normalized
        )
    }

    private fun findBestDayHeader(
        lines: List<Text.Line>
    ): Pair<Text.Line, Map<Int, Int>>? {
        return lines.mapNotNull { line ->
            val days = line.elements.mapNotNull { element ->
                val token = element.text
                    .trim()
                    .trim('.', ',', ':', ';')
                    .toIntOrNull()
                    ?.takeIf { it in 1..31 }
                val box = element.boundingBox
                if (token != null && box != null) token to box.centerX() else null
            }
            if (days.isEmpty()) null
            else line to days
                .distinctBy { it.first }
                .sortedBy { it.first }
                .toMap()
        }.maxByOrNull { it.second.size }
    }

    private fun parseGeometryRows(
        lines: List<Text.Line>,
        headerBottom: Int,
        dayCenters: Map<Int, Int>
    ): List<RecognizedScheduleRow> {
        if (dayCenters.size < 5) return emptyList()
        val minDayX = dayCenters.values.minOrNull() ?: return emptyList()
        val spacing = medianDaySpacing(dayCenters)
        val maxDistance = max(14.0, spacing * 0.58)

        val lineRows = lines.asSequence()
            .filter { line -> (line.boundingBox?.top ?: Int.MIN_VALUE) > headerBottom }
            .mapNotNull { line ->
                parseGeometryLine(line, minDayX, dayCenters, maxDistance)
            }
            .toList()

        if (lineRows.isNotEmpty()) return mergeRows(lineRows)

        // Some OCR engines split one table row into several visual lines. Cluster
        // all tokens by vertical position as a secondary geometry pass.
        val tokens = lines
            .filter { (it.boundingBox?.top ?: Int.MIN_VALUE) > headerBottom }
            .flatMap { line ->
                line.elements.mapNotNull { element ->
                    element.boundingBox?.let { Token(element.text.trim(), it) }
                }
            }
            .filter { it.text.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val medianHeight = tokens.map { it.box.height().coerceAtLeast(1) }
            .sorted()
            .let { heights -> heights[heights.size / 2].toDouble() }
        val tolerance = max(8.0, medianHeight * 0.70)
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

        return mergeRows(
            clusters.mapNotNull { row ->
                parseTokenRow(row.sortedBy { it.centerX }, minDayX, dayCenters, maxDistance)
            }
        )
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
            val code = if (exactShiftRegex.matches(token.text)) canonicalShift(token.text) else null
            if (code != null) token to code else null
        }
        if (shiftTokens.isEmpty()) return null

        val firstShiftX = shiftTokens.minOf { it.first.centerX }
        val nameBoundary = minOf(firstShiftX, minDayX)
        val leftText = tokens
            .filter { token ->
                token.centerX < nameBoundary && !exactShiftRegex.matches(token.text)
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

        val dayShifts = if (explicitPairs.isNotEmpty()) {
            explicitPairs
        } else {
            shifts.mapIndexed { index, code -> (index + 1) to code }.toMap()
        }
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
        val merged = linkedMapOf<String, RecognizedScheduleRow>()
        rows.forEach { row ->
            val key = normalizeAscii(row.name).replace(spaces, " ").trim()
            val existing = merged[key]
            if (existing == null) {
                merged[key] = row
            } else {
                merged[key] = existing.copy(
                    rowNumber = existing.rowNumber ?: row.rowNumber,
                    dayShifts = (existing.dayShifts + row.dayShifts).toSortedMap()
                )
            }
        }
        return merged.values.toList()
    }

    internal fun detectMonth(text: String): YearMonth? {
        val upper = normalizeAscii(text.replace('\u00A0', ' '))

        val yearFirst = Regex("""\b(20\d{2})[ \\t]*[./-][ \\t]*(0?[1-9]|1[0-2])\b""")
            .find(upper)
        if (yearFirst != null) {
            val year = yearFirst.groupValues[1].toInt()
            val month = yearFirst.groupValues[2].toInt()
            return YearMonth.of(year, month)
        }

        val monthFirst = Regex("""\b(0?[1-9]|1[0-2])[ \\t]*[./-][ \\t]*(20\d{2})\b""")
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
