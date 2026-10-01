package hr.raspored.app.data

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

object WorkType {
    const val REGULAR = "regular"
    const val SHIFT_1 = "shift1"
    const val SHIFT_2 = "shift2"
    const val SHIFT_3 = "shift3"
    const val TURNUS = "turnus"
    const val DUTY = "duty"
    const val STANDBY = "standby"
    const val CALLOUT = "callout"
    const val OTHER = "other"

    val valid = setOf(
        REGULAR, SHIFT_1, SHIFT_2, SHIFT_3, TURNUS, DUTY, STANDBY, CALLOUT, OTHER
    )

    fun normalized(value: String?): String =
        value?.takeIf { it in valid } ?: REGULAR
}

data class TimeEvidenceEntry(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String,
    val workType: String = WorkType.REGULAR
) {
    fun durationMinutes(now: Long): Long =
        ((endedAt ?: now) - startedAt).coerceAtLeast(0L) / 60_000L
}

class TimeEvidenceStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.time_evidence", Context.MODE_PRIVATE)

    fun load(): List<TimeEvidenceEntry> =
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(PREFIX)) return@mapNotNull null
            decode(
                key.removePrefix(PREFIX).toLongOrNull() ?: return@mapNotNull null,
                value as? String ?: return@mapNotNull null
            )
        }.sortedBy { it.startedAt }

    fun replaceAll(entries: List<TimeEvidenceEntry>) {
        val editor = preferences.edit().clear()
        entries
            .distinctBy { it.startedAt }
            .sortedBy { it.startedAt }
            .forEach { entry ->
                val safe = entry.copy(
                    id = entry.startedAt,
                    endedAt = entry.endedAt?.coerceAtLeast(entry.startedAt),
                    note = entry.note.take(500),
                    workType = WorkType.normalized(entry.workType)
                )
                editor.putString(PREFIX + safe.id, encode(safe))
            }
        editor.apply()
    }

    fun active(): TimeEvidenceEntry? = load().lastOrNull { it.endedAt == null }

    fun clockIn(now: Long, workType: String = WorkType.REGULAR): TimeEvidenceEntry? {
        if (active() != null) return null
        val entry = TimeEvidenceEntry(
            id = now,
            startedAt = now,
            endedAt = null,
            note = "",
            workType = WorkType.normalized(workType)
        )
        save(entry)
        return entry
    }

    fun clockOut(now: Long, note: String): TimeEvidenceEntry? {
        val active = active() ?: return null
        val finished = active.copy(
            endedAt = maxOf(now, active.startedAt),
            note = note.take(500)
        )
        save(finished)
        return finished
    }

    fun updateNote(id: Long, note: String) {
        val entry = load().firstOrNull { it.id == id } ?: return
        save(entry.copy(note = note.take(500)))
    }

    fun updateWorkType(id: Long, workType: String) {
        val entry = load().firstOrNull { it.id == id } ?: return
        save(entry.copy(workType = WorkType.normalized(workType)))
    }

    private fun save(entry: TimeEvidenceEntry) {
        preferences.edit()
            .putString(PREFIX + entry.id, encode(entry))
            .apply()
    }

    private fun encode(entry: TimeEvidenceEntry): String {
        val note = Base64.getEncoder()
            .encodeToString(entry.note.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            entry.startedAt.toString(),
            entry.endedAt?.toString().orEmpty(),
            WorkType.normalized(entry.workType),
            note
        ).joinToString("|")
    }

    private fun decode(id: Long, value: String): TimeEvidenceEntry? = runCatching {
        val parts = value.split("|", limit = 4)
        val started = parts[0].toLong()
        val ended = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.toLong()

        if (parts.size >= 4 && parts[2] in WorkType.valid) {
            val note = parts.getOrNull(3)
                ?.takeIf(String::isNotBlank)
                ?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }
                .orEmpty()
            TimeEvidenceEntry(
                id = id,
                startedAt = started,
                endedAt = ended,
                note = note,
                workType = WorkType.normalized(parts[2])
            )
        } else {
            // v1-v1.0.4 legacy format: startedAt|endedAt|base64(note)
            val note = parts.getOrNull(2)
                ?.takeIf(String::isNotBlank)
                ?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }
                .orEmpty()
            TimeEvidenceEntry(id, started, ended, note, WorkType.REGULAR)
        }
    }.getOrNull()

    private companion object {
        const val PREFIX = "entry."
    }
}
