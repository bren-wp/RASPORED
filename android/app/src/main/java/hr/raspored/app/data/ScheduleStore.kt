package hr.raspored.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Deterministic local schedule store backed by SQLite.
 *
 * Values are short user-visible schedule labels. Built-in semantic codes
 * (D/N/J/GO/BO/PD/SD) keep their special meaning, while users may also save
 * their own short labels for workplace-specific roster notation.
 *
 * Existing SharedPreferences data is migrated transactionally on first use.
 * A write-through legacy mirror is intentionally retained so an interrupted
 * upgrade or downgrade cannot strand a user's schedule history.
 *
 * The store never auto-prunes old months. A monthly import only replaces the
 * selected month, so schedules from previous months/years remain available.
 * The product guarantee is at least 10 years of local calendar history unless
 * the user explicitly clears app data or uninstalls the application.
 */
class ScheduleStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences =
        appContext.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
    private val database = ScheduleDatabaseHelper(appContext)

    init {
        migrateLegacyPreferences()
    }

    fun load(): Map<String, String> =
        runCatching { loadFromDatabase() }
            .getOrElse { loadFromLegacyPreferences() }

    fun saveMonth(month: YearMonth, shifts: Map<Int, String>) {
        val normalized = shifts.mapNotNull { (day, rawCode) ->
            val code = normalizeCode(rawCode)
            if (day in 1..month.lengthOfMonth() && code != null) day to code else null
        }.toMap()

        runCatching {
            val db = database.writableDatabase
            db.beginTransaction()
            try {
                db.delete(
                    TABLE_ENTRIES,
                    "$COLUMN_DATE >= ? AND $COLUMN_DATE <= ?",
                    arrayOf(month.atDay(1).toString(), month.atEndOfMonth().toString())
                )
                normalized.forEach { (day, code) ->
                    putEntry(db, month.atDay(day).toString(), code)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        mirrorMonthToLegacyPreferences(month, normalized)
    }

    fun record(date: LocalDate, rawCode: String?) {
        val code = normalizeCode(rawCode)
        val db = database.writableDatabase
        if (code == null) {
            db.delete(TABLE_ENTRIES, "$COLUMN_DATE = ?", arrayOf(date.toString()))
        } else {
            putEntry(db, date.toString(), code)
        }

        val editor = legacyPreferences.edit()
        if (code != null) editor.putString(date.toString(), code)
        else editor.remove(date.toString())
        check(editor.commit()) { "Unable to persist schedule compatibility mirror" }
    }

    private fun loadFromDatabase(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        database.readableDatabase.query(
            TABLE_ENTRIES,
            arrayOf(COLUMN_DATE, COLUMN_CODE),
            null,
            null,
            null,
            null,
            "$COLUMN_DATE ASC"
        ).use { cursor ->
            val dateIndex = cursor.getColumnIndexOrThrow(COLUMN_DATE)
            val codeIndex = cursor.getColumnIndexOrThrow(COLUMN_CODE)
            while (cursor.moveToNext()) {
                val date = cursor.getString(dateIndex)
                val code = normalizeCode(cursor.getString(codeIndex))
                if (DATE.matches(date) && code != null) {
                    result[date] = code
                }
            }
        }
        return result
    }

    private fun loadFromLegacyPreferences(): Map<String, String> =
        legacyPreferences.all
            .mapNotNull { (key, value) ->
                val code = normalizeCode(value as? String) ?: return@mapNotNull null
                if (DATE.matches(key)) key to code else null
            }
            .toMap()

    private fun migrateLegacyPreferences() {
        val legacy = loadFromLegacyPreferences()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val migrated = db.rawQuery(
                "SELECT $COLUMN_META_VALUE FROM $TABLE_META WHERE $COLUMN_META_KEY = ? LIMIT 1",
                arrayOf(META_LEGACY_MIGRATED)
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "1"
            }

            if (!migrated) {
                legacy.forEach { (date, code) ->
                    val values = ContentValues().apply {
                        put(COLUMN_DATE, date)
                        put(COLUMN_CODE, code)
                    }
                    val inserted = db.insertWithOnConflict(
                        TABLE_ENTRIES,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                    check(inserted != -1L) { "Unable to migrate legacy schedule entry $date" }
                }
                val meta = ContentValues().apply {
                    put(COLUMN_META_KEY, META_LEGACY_MIGRATED)
                    put(COLUMN_META_VALUE, "1")
                }
                val marker = db.insertWithOnConflict(
                    TABLE_META,
                    null,
                    meta,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                check(marker != -1L) { "Unable to persist schedule migration marker" }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun mirrorMonthToLegacyPreferences(
        month: YearMonth,
        shifts: Map<Int, String>
    ) {
        val editor = legacyPreferences.edit()
        (1..month.lengthOfMonth()).forEach { day ->
            editor.remove(month.atDay(day).toString())
        }
        shifts.forEach { (day, code) ->
            editor.putString(month.atDay(day).toString(), code)
        }
        editor.apply()
    }

    private fun putEntry(db: SQLiteDatabase, date: String, code: String) {
        val values = ContentValues().apply {
            put(COLUMN_DATE, date)
            put(COLUMN_CODE, code)
        }
        val result = db.insertWithOnConflict(
            TABLE_ENTRIES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        check(result != -1L) { "Unable to persist schedule entry $date" }
    }

    private class ScheduleDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_ENTRIES (
                    $COLUMN_DATE TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_CODE TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE $TABLE_META (
                    $COLUMN_META_KEY TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_META_VALUE TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Database version 1 is the first SQLite-backed schedule schema.
            // Future schema changes must use explicit, lossless migrations here.
        }
    }

    companion object {
        const val ARCHIVE_GUARANTEE_YEARS = 10
        const val DATABASE_NAME = "raspored_schedule.db"
        val BUILT_IN_CODES = linkedSetOf("D", "N", "J", "GO", "BO", "PD", "SD")

        private const val DATABASE_VERSION = 1
        private const val LEGACY_PREFERENCES = "raspored.schedule"
        private const val TABLE_ENTRIES = "schedule_entries"
        private const val TABLE_META = "schedule_meta"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_CODE = "code"
        private const val COLUMN_META_KEY = "meta_key"
        private const val COLUMN_META_VALUE = "meta_value"
        private const val META_LEGACY_MIGRATED = "legacy_shared_preferences_migrated"

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
