package hr.raspored.app.ocr

import android.graphics.Bitmap
import hr.raspored.app.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.YearMonth
import java.util.UUID

object AiScheduleVerifier {

    fun verify(
        bitmap: Bitmap,
        token: String,
        monthHint: YearMonth?,
        localOcrText: String
    ): RecognizedSchedule {
        require(token.matches(Regex("""^[a-f0-9]{64}$"""))) {
            "Nedostaje valjana prijava za AI provjeru."
        }

        val jpeg = ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                throw IllegalStateException("Sliku nije moguće pripremiti za AI provjeru.")
            }
            output.toByteArray()
        }
        if (jpeg.isEmpty() || jpeg.size > 10 * 1024 * 1024) {
            throw IllegalStateException("Slika je prevelika za AI provjeru.")
        }

        val boundary = "----Raspored" + UUID.randomUUID().toString().replace("-", "")
        val endpoint = URL(BuildConfig.BACKEND_BASE_URL.trimEnd('/') + "/api/ai-ocr.php")
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 100_000
        connection.useCaches = false
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Raspored-Client", "android")
        connection.setRequestProperty("X-Raspored-Request", "1")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        connection.outputStream.buffered().use { stream ->
            fun write(value: String) {
                stream.write(value.toByteArray(Charsets.UTF_8))
            }
            fun field(name: String, value: String) {
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                write(value)
                write("\r\n")
            }

            monthHint?.let { field("monthHint", it.toString()) }
            if (localOcrText.isNotBlank()) {
                field("localOcr", localOcrText.take(12_000))
            }

            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"image\"; filename=\"raspored.jpg\"\r\n")
            write("Content-Type: image/jpeg\r\n\r\n")
            stream.write(jpeg)
            write("\r\n--$boundary--\r\n")
        }

        val status = connection.responseCode
        val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()

        return parseResponse(status, raw)
    }

    internal fun parseResponse(status: Int, raw: String): RecognizedSchedule {
        val payload = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IllegalStateException("AI odgovor nije valjan.")
        if (status !in 200..299 || !payload.optBoolean("ok", false)) {
            throw IllegalStateException(
                payload.optString("error").takeIf { it.isNotBlank() }
                    ?: "AI provjera nije uspjela."
            )
        }

        val result = payload.optJSONObject("result")
            ?: throw IllegalStateException("AI odgovor nije valjan.")
        val monthObject = result.optJSONObject("month")
        val month = monthObject?.let {
            val year = it.optInt("year", 0)
            val number = it.optInt("month", 0)
            if (year in 2000..2100 && number in 1..12) YearMonth.of(year, number) else null
        }

        val rows = mutableListOf<RecognizedScheduleRow>()
        val people = result.optJSONArray("people")
        if (people != null) {
            for (index in 0 until people.length()) {
                val person = people.optJSONObject(index) ?: continue
                val name = person.optString("name").trim().replace(Regex("""\s+"""), " ").take(100)
                if (name.length < 2) continue
                val rowNumber = person.opt("row")?.let { rawRow ->
                    when (rawRow) {
                        is Number -> rawRow.toInt().takeIf { it in 1..100 }
                        else -> null
                    }
                }
                val shifts = linkedMapOf<Int, String>()
                val dayShifts = person.optJSONObject("dayShifts")
                if (dayShifts != null) {
                    dayShifts.keys().forEach { key ->
                        val day = key.toIntOrNull()
                        val code = ScheduleStoreBridge.normalizeCode(dayShifts.optString(key))
                        if (day != null && day in 1..31 && code != null) shifts[day] = code
                    }
                }
                rows += RecognizedScheduleRow(
                    rowNumber = rowNumber,
                    name = name,
                    dayShifts = shifts
                )
            }
        }
        if (rows.isEmpty()) {
            throw IllegalStateException("AI nije vratio nijednu pouzdano prepoznatu osobu.")
        }

        return RecognizedSchedule(
            month = month,
            rows = rows,
            rawText = "",
            expectedRowCount = result.optInt("expectedRows", 0).takeIf { it > 0 }
        )
    }

    /**
     * Keeps AI transport independent from the Android storage implementation.
     */
    private object ScheduleStoreBridge {
        fun normalizeCode(raw: String?): String? {
            val value = raw
                ?.trim()
                ?.uppercase(java.util.Locale("hr", "HR"))
                .orEmpty()
            if (value.isBlank()) return null
            val normalized = when (value) {
                "G0" -> "GO"
                "B0" -> "BO"
                else -> value
            }
            return normalized.takeIf { Regex("""^[\p{L}\p{N}]{1,8}$""").matches(it) }
        }
    }
}
