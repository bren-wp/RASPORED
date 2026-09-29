package hr.raspored.app.data

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

data class TimeEvidenceEntry(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String
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
            decode(key.removePrefix(PREFIX).toLongOrNull() ?: return@mapNotNull null, value as? String ?: return@mapNotNull null)
        }.sortedBy { it.startedAt }

    fun active(): TimeEvidenceEntry? = load().lastOrNull { it.endedAt == null }

    fun clockIn(now: Long): TimeEvidenceEntry? {
        if (active() != null) return null
        val entry = TimeEvidenceEntry(id = now, startedAt = now, endedAt = null, note = "")
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

    private fun save(entry: TimeEvidenceEntry) {
        preferences.edit()
            .putString(PREFIX + entry.id, encode(entry))
            .apply()
    }

    private fun encode(entry: TimeEvidenceEntry): String {
        val note = Base64.getEncoder().encodeToString(entry.note.toByteArray(StandardCharsets.UTF_8))
        return listOf(entry.startedAt.toString(), entry.endedAt?.toString().orEmpty(), note).joinToString("|")
    }

    private fun decode(id: Long, value: String): TimeEvidenceEntry? = runCatching {
        val parts = value.split("|", limit = 3)
        val started = parts[0].toLong()
        val ended = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.toLong()
        val note = parts.getOrNull(2)
            ?.takeIf(String::isNotBlank)
            ?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }
            .orEmpty()
        TimeEvidenceEntry(id, started, ended, note)
    }.getOrNull()

    private companion object {
        const val PREFIX = "entry."
    }
}
