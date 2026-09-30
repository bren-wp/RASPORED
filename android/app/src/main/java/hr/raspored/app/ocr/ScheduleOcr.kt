package hr.raspored.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

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
    private val shiftRegex = Regex("""(?<![\p{L}])(GO|G0|BO|B0|PD|SD|D|N)[.,;:]?(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val exactShiftRegex = Regex("""^(GO|G0|BO|B0|PD|SD|D|N)[.,;:]?$""", RegexOption.IGNORE_CASE)
    private val rowNumberRegex = Regex("""^\s*(\d{1,3})[.)]?\s*""")
    private val spaces = Regex("""\s+""")
    private val monthNames = mapOf(
        "SIJEČANJ" to Month.JANUARY,
        "SIJECANJ" to Month.JANUARY,
        "VELJAČA" to Month.FEBRUARY,
        "VELJACA" to Month.FEBRUARY,
        "OŽUJAK" to Month.MARCH,
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
        val header = lines
            .map { line ->
                val days = line.elements.mapNotNull { element ->
                    val day = element.text.trim().toIntOrNull()?.takeIf { it in 1..31 }
                    val box = element.boundingBox
                    if (day != null && box != null) day to box.centerX() else null
                }
                line to days
            }
            .maxByOrNull { it.second.distinctBy { point -> point.first }.size }

        val geometryRows = if (header != null && header.second.distinctBy { it.first }.size >= 5) {
            parseGeometryRows(
                lines = lines,
                headerBottom = header.first.boundingBox?.bottom ?: Int.MIN_VALUE,
                dayCenters = header.second.toMap()
            )
        } else emptyList()

        return RecognizedSchedule(
            month = detectMonth(result.text),
            rows = if (geometryRows.isNotEmpty()) geometryRows else parse(result.text).rows,
            rawText = result.text.replace('\u00A0', ' ')
        )
    }

    fun parse(text: String): RecognizedSchedule {
        val normalized = text.replace('\u00A0', ' ')
        val rows = normalized.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseRow)
            .filter { it.shifts.isNotEmpty() }
            .distinctBy { it.name.uppercase(Locale("hr", "HR")) }
            .toList()

        return RecognizedSchedule(
            month = detectMonth(normalized),
            rows = rows,
            rawText = normalized
        )
    }

    private fun parseGeometryRows(
        lines: List<Text.Line>,
        headerBottom: Int,
        dayCenters: Map<Int, Int>
    ): List<RecognizedScheduleRow> {
        val minDayX = dayCenters.values.minOrNull() ?: return emptyList()
        return lines.asSequence()
            .filter { (it.boundingBox?.top ?: Int.MIN_VALUE) > headerBottom }
            .mapNotNull { line ->
                val shiftElements = line.elements.mapNotNull { element ->
                    val raw = element.text.trim()
                    val code = if (exactShiftRegex.matches(raw)) canonicalShift(raw) else null
                    val box = element.boundingBox
                    if (box != null && code != null) Triple(element.text, code, box.centerX()) else null
                }
                if (shiftElements.isEmpty()) return@mapNotNull null

                val firstShiftX = shiftElements.minOf { it.third }
                val leftText = line.elements
                    .filter { element ->
                        val box = element.boundingBox
                        box != null && box.centerX() < minOf(firstShiftX, minDayX) && !exactShiftRegex.matches(element.text.trim())
                    }
                    .joinToString(" ") { it.text.trim() }
                    .replace(spaces, " ")
                    .trim()

                val rowNumber = rowNumberRegex.find(leftText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val name = leftText
                    .replaceFirst(rowNumberRegex, "")
                    .replace(Regex("""\b\d{1,2}([./-]\d{1,2})?\b"""), " ")
                    .replace(spaces, " ")
                    .trim(' ', '-', '|', ':', ';')

                if (name.length < 3 || name.count(Char::isLetter) < 3) return@mapNotNull null

                val dayShifts = buildMap<Int, String> {
                    shiftElements.forEach { (_, code, x) ->
                        val day = dayCenters.minByOrNull { (_, center) -> abs(center - x) }?.key
                        if (day != null) put(day, code)
                    }
                }
                if (dayShifts.isEmpty()) null else RecognizedScheduleRow(rowNumber, name, dayShifts)
            }
            .distinctBy { it.name.uppercase(Locale("hr", "HR")) }
            .toList()
    }

    internal fun parseRow(line: String): RecognizedScheduleRow? {
        val shifts = shiftRegex.findAll(line)
            .mapNotNull { canonicalShift(it.value) }
            .toList()
        if (shifts.isEmpty()) return null

        val rowNumber = rowNumberRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val withoutNumber = line.replaceFirst(rowNumberRegex, "")
        val name = withoutNumber
            .replace(shiftRegex, " ")
            .replace(Regex("""\b\d{1,2}([./-]\d{1,2})?\b"""), " ")
            .replace(spaces, " ")
            .trim(' ', '-', '|', ':', ';')

        if (name.length < 3 || name.count(Char::isLetter) < 3) return null
        return RecognizedScheduleRow(rowNumber = rowNumber, name = name, dayShifts = shifts.mapIndexed { index, code -> (index + 1) to code }.toMap())
    }

    internal fun detectMonth(text: String): YearMonth? {
        val upper = text.uppercase(Locale("hr", "HR"))
        val year = Regex("""\b(20\d{2})\b""").find(upper)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val month = monthNames.entries.firstOrNull { upper.contains(it.key) }?.value ?: return null
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
            .addOnSuccessListener { result -> onSuccess(ScheduleOcrParser.parse(result)) }
            .addOnFailureListener(onError)
    }
}
