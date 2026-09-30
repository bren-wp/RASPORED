package hr.raspored.app.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object ReportExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun createMonthlyPdf(
        context: Context,
        month: YearMonth,
        schedule: Map<String, String>,
        evidence: List<TimeEvidenceEntry>,
        profileName: String = "",
        zone: ZoneId = ZoneId.systemDefault()
    ): Uri {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "RASPORED-${month}.pdf")
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f }

        val monthEntries = evidence.filter {
            val instant = Instant.ofEpochMilli(it.startedAt).atZone(zone)
            YearMonth.from(instant) == month
        }
        val workedMinutes = monthEntries.filter { it.endedAt != null }
            .sumOf { it.durationMinutes(it.endedAt ?: it.startedAt) }
        val counts = listOf("D", "N", "GO", "BO", "PD", "SD")
            .associateWith { code ->
                (1..month.lengthOfMonth()).count { day ->
                    schedule[month.atDay(day).toString()] == code
                }
            }

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = 44f

        fun drawHeader() {
            canvas.drawText("RASPORED", 36f, y, titlePaint)
            y += 20f
            val monthName = month.month
                .getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                .replaceFirstChar { it.titlecase(Locale("hr", "HR")) }
            canvas.drawText("Mjesečni izvještaj · $monthName ${month.year}.", 36f, y, headingPaint)
            y += 16f
            if (profileName.isNotBlank()) {
                canvas.drawText("Profil: ${profileName.take(80)}", 36f, y, bodyPaint)
                y += 14f
            }
            canvas.drawText(
                "Odrađeno: ${workedMinutes / 60}h ${(workedMinutes % 60).toString().padStart(2, '0')}min",
                36f,
                y,
                bodyPaint
            )
            y += 14f
            canvas.drawText(
                "D ${counts["D"]} · N ${counts["N"]} · GO ${counts["GO"]} · BO ${counts["BO"]} · PD ${counts["PD"]} · SD ${counts["SD"]}",
                36f,
                y,
                bodyPaint
            )
            y += 22f
            canvas.drawText("Datum", 36f, y, headingPaint)
            canvas.drawText("Raspored", 112f, y, headingPaint)
            canvas.drawText("Evidencija rada", 220f, y, headingPaint)
            y += 9f
            canvas.drawLine(36f, y, 560f, y, paint)
            y += 13f
        }

        fun nextPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = 44f
            drawHeader()
        }

        drawHeader()
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.", Locale("hr", "HR"))
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("hr", "HR"))

        for (day in 1..month.lengthOfMonth()) {
            if (y > PAGE_HEIGHT - 52f) {
                nextPage()
            }
            val date = month.atDay(day)
            val code = schedule[date.toString()].orEmpty()
            val codeLabel = when (code) {
                "D" -> "D · dnevna smjena"
                "N" -> "N · noćna smjena"
                "GO" -> "GO · godišnji odmor"
                "BO" -> "BO · bolovanje"
                "PD" -> "PD · plaćeni dopust"
                "SD" -> "SD · slobodan dan"
                else -> "—"
            }
            val entriesForDay = monthEntries.filter {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date
            }
            val evidenceText = if (entriesForDay.isEmpty()) {
                "—"
            } else {
                entriesForDay.joinToString(" | ") { entry ->
                    val start = Instant.ofEpochMilli(entry.startedAt).atZone(zone).toLocalTime().format(timeFormatter)
                    val end = entry.endedAt?.let {
                        Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(timeFormatter)
                    } ?: "u tijeku"
                    "$start–$end (${workTypeLabel(entry.workType)})"
                }.take(58)
            }

            canvas.drawText(date.format(dateFormatter), 36f, y, bodyPaint)
            canvas.drawText(codeLabel, 112f, y, bodyPaint)
            canvas.drawText(evidenceText, 220f, y, smallPaint)
            y += 17f
        }

        y += 8f
        if (y > PAGE_HEIGHT - 46f) {
            nextPage()
        }
        canvas.drawLine(36f, y, 560f, y, paint)
        y += 15f
        canvas.drawText(
            "Izvještaj je informativan i temelji se na podacima spremljenima u aplikaciji RASPORED.",
            36f,
            y,
            smallPaint
        )

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    private fun workTypeLabel(type: String): String = when (WorkType.normalized(type)) {
        WorkType.SHIFT_1 -> "1. smjena"
        WorkType.SHIFT_2 -> "2. smjena"
        WorkType.SHIFT_3 -> "3. smjena"
        WorkType.TURNUS -> "turnus"
        WorkType.DUTY -> "dežurstvo"
        WorkType.STANDBY -> "pripravnost"
        WorkType.CALLOUT -> "rad po pozivu"
        WorkType.OTHER -> "drugo"
        else -> "redovni rad"
    }
}
