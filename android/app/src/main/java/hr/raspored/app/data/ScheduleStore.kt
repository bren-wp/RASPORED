package hr.raspored.app.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Deterministic local schedule store.
 *
 * Values are short user-visible schedule labels. Built-in semantic codes
 * (D/N/GO/BO/PD/SD) keep their special meaning, while users may also save
 * their own short labels for workplace-specific roster notation.
 *
 * The store never auto-prunes old months. A monthly import only replaces the
 * selected month, so schedules from previous months/years remain available.
 * The product guarantee is at least 10 years of local calendar history unless
 * the user explicitly clears app data or uninstalls the application.
 */
class ScheduleStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.schedule", Context.MODE_PRIVATE)

    fun load(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) ->
                val code = normalizeCode(value as? String) ?: return@mapNotNull null
                if (DATE.matches(key)) key to code else null
            }
            .toMap()

    fun saveMonth(month: YearMonth, shifts: Map<Int, String>) {
        val editor = preferences.edit()
        (1..month.lengthOfMonth()).forEach { day ->
            editor.remove(month.atDay(day).toString())
        }
        shifts.forEach { (day, rawCode) ->
            val code = normalizeCode(rawCode)
            if (day in 1..month.lengthOfMonth() && code != null) {
                editor.putString(month.atDay(day).toString(), code)
            }
        }
        editor.apply()
    }

    fun record(date: LocalDate, rawCode: String?) {
        val editor = preferences.edit()
        val code = normalizeCode(rawCode)
        if (code != null) editor.putString(date.toString(), code)
        else editor.remove(date.toString())
        editor.apply()
    }

    companion object {
        const val ARCHIVE_GUARANTEE_YEARS = 10
        val BUILT_IN_CODES = setOf("D", "N", "GO", "BO", "PD", "SD")
        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        private val CODE = Regex("""^[\p{L}\p{N}]{1,8}$""")

        fun normalizeCode(raw: String?): String? {
            val value = raw
                ?.trim()
                ?.uppercase(Locale("hr", "HR"))
                .orEmpty()
            val normalized = when (value) {
                "G0" -> "GO"
                "B0" -> "BO"
                else -> value
            }
            return normalized.takeIf { CODE.matches(it) }
        }
    }
}
