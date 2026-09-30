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
    private val shiftRegex = Regex("""(?<![\p{L}])(GO|BO|D|N)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val exactShiftRegex = Regex("""^(GO|BO|D|N)$""", RegexOption.IGNORE_CASE)
    private val rowNumberRegex = Regex("""^\s*(\d{1,3})[.)]?\s*""")
    private val spaces = Regex("""\s+""")
    private val monthNames = mapOf(
        "SIJEČANJ" to Month.JANUARY,
        "VELJAČA" to Month.FEBRUARY,
        "OŽUJAK" to Month.MARCH,
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

    internal fun parseRow(line: String): RecognizedScheduleRow? {
        val shifts = shiftRegex.findAll(line)
            .map { it.value.uppercase(Locale.ROOT) }
            .filter { it in setOf("D", "N", "GO", "BO") }
            .toList()
        if (shifts.isEmpty()) return null

        val withoutNumber = line.replaceFirst(rowNumberRegex, "")
        val name = withoutNumber
            .replace(shiftRegex, " ")
            .replace(Regex("""\b\d{1,2}([./-]\d{1,2})?\b"""), " ")
            .replace(spaces, " ")
            .trim(' ', '-', '|', ':', ';')

        if (name.length < 3 || name.count(Char::isLetter) < 3) return null
        return RecognizedScheduleRow(name = name, shifts = shifts)
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
            .addOnSuccessListener { result -> onSuccess(ScheduleOcrParser.parse(result.text)) }
            .addOnFailureListener(onError)
    }
}
