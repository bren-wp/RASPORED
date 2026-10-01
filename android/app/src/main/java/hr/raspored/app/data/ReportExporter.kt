package hr.raspored.app.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
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
        profileName: String = ""
    ): Uri {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "RASPORED-${month}.pdf")
        val document = PdfDocument()
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

        val summary = EvidenceAnalytics.summarize(month, schedule)
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
                "Sati iz kalendara: ${summary.workedMinutes / 60} h · noćni: ${summary.nightMinutes / 60} h",
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
            canvas.drawText("Automatska evidencija", 300f, y, headingPaint)
            y += 9f
            canvas.drawLine(36f, y, 560f, y, linePaint)
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

        for (day in 1..month.lengthOfMonth()) {
            if (y > PAGE_HEIGHT - 52f) nextPage()
            val date = month.atDay(day)
            val code = schedule[date.toString()].orEmpty()
            val codeLabel = when (code) {
                "D" -> "D · dnevna smjena"
                "N" -> "N · noćna smjena"
                "GO" -> "GO · godišnji odmor"
                "BO" -> "BO · bolovanje"
                "PD" -> "PD · plaćeni dopust"
                "SD" -> "SD · slobodan dan"
                else -> code.takeIf { it.isNotBlank() }?.let { "$it · vlastita oznaka" } ?: "—"
            }
            val evidence = when (code) {
                "D" -> "07:00–19:00 · 12 h"
                "N" -> "19:00–07:00 · 12 h"
                "GO", "BO", "PD", "SD", "" -> "—"
                else -> "satnica nije definirana"
            }
            canvas.drawText(date.format(dateFormatter), 36f, y, bodyPaint)
            canvas.drawText(codeLabel, 112f, y, bodyPaint)
            canvas.drawText(evidence, 300f, y, smallPaint)
            y += 17f
        }

        y += 8f
        if (y > PAGE_HEIGHT - 46f) nextPage()
        canvas.drawLine(36f, y, 560f, y, linePaint)
        y += 15f
        canvas.drawText(
            "Evidencija je izvedena iz kalendara. Za vlastite oznake aplikacija ne izmišlja trajanje.",
            36f,
            y,
            smallPaint
        )

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }
}
