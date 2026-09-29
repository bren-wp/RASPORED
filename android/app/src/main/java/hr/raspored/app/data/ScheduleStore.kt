package hr.raspored.app.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * Small, deterministic local schedule store.
 *
 * The persisted format is deliberately simple (date -> semantic shift code) so it
 * can be migrated to Room/API storage later without coupling UI code to a database.
 */
class ScheduleStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.schedule", Context.MODE_PRIVATE)

    fun load(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) ->
                val code = value as? String
                if (DATE.matches(key) && code in VALID_CODES) key to code else null
            }
            .toMap()

    fun saveMonth(month: YearMonth, shifts: Map<Int, String>) {
        val editor = preferences.edit()
        (1..month.lengthOfMonth()).forEach { day ->
            editor.remove(month.atDay(day).toString())
        }
        shifts.forEach { (day, code) ->
            if (day in 1..month.lengthOfMonth() && code in VALID_CODES) {
                editor.putString(month.atDay(day).toString(), code)
            }
        }
        editor.apply()
    }

    fun record(date: LocalDate, code: String?) {
        val editor = preferences.edit()
        if (code in VALID_CODES) editor.putString(date.toString(), code)
        else editor.remove(date.toString())
        editor.apply()
    }

    private companion object {
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        val VALID_CODES = setOf("D", "N", "GO", "BO")
    }
}
