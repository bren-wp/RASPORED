package hr.raspored.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class TeamMember(
    val name: String,
    val note: String = "",
    val schedule: Map<String, String> = emptyMap()
)

class TeamStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.team", Context.MODE_PRIVATE)

    fun load(): List<TeamMember> = runCatching {
        val raw = preferences.getString(KEY_MEMBERS, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = sanitizeName(item.optString("name"))
                if (name.length < 2) continue
                val scheduleObject = item.optJSONObject("schedule") ?: JSONObject()
                val schedule = buildMap {
                    val keys = scheduleObject.keys()
                    while (keys.hasNext()) {
                        val date = keys.next()
                        val code = ScheduleStore.normalizeCode(scheduleObject.optString(date))
                        if (DATE.matches(date) && code != null) {
                            put(date, code)
                        }
                    }
                }
                add(
                    TeamMember(
                        name = name,
                        note = item.optString("note").trim().take(120),
                        schedule = schedule
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun replaceAll(members: List<TeamMember>) {
        val array = JSONArray()
        members.take(100).forEach { member ->
            val name = sanitizeName(member.name)
            if (name.length < 2) return@forEach
            val schedule = JSONObject()
            member.schedule.toSortedMap().forEach { (date, rawCode) ->
                val code = ScheduleStore.normalizeCode(rawCode)
                if (DATE.matches(date) && code != null) {
                    schedule.put(date, code)
                }
            }
            array.put(
                JSONObject()
                    .put("name", name)
                    .put("note", member.note.trim().take(120))
                    .put("schedule", schedule)
            )
        }
        preferences.edit().putString(KEY_MEMBERS, array.toString()).apply()
    }

    fun saveRecognizedMonth(
        month: YearMonth,
        rows: List<Pair<String, Map<Int, String>>>
    ) {
        val current = load().associateBy { normalized(it.name) }.toMutableMap()
        rows.forEach { (rawName, dayShifts) ->
            val name = sanitizeName(rawName)
            if (name.length < 2) return@forEach
            val key = normalized(name)
            val existing = current[key] ?: TeamMember(name)
            val merged = existing.schedule.toMutableMap()
            (1..month.lengthOfMonth()).forEach { day ->
                merged.remove(month.atDay(day).toString())
            }
            dayShifts.forEach { (day, rawCode) ->
                val code = ScheduleStore.normalizeCode(rawCode)
                if (day in 1..month.lengthOfMonth() && code != null) {
                    merged[month.atDay(day).toString()] = code
                }
            }
            current[key] = existing.copy(name = name, schedule = merged.toSortedMap())
        }
        replaceAll(current.values.sortedBy { it.name })
    }

    private fun sanitizeName(value: String): String =
        value.trim().replace(Regex("""\s+"""), " ").take(100)

    private fun normalized(value: String): String =
        value.uppercase()
            .replace(Regex("""[^A-ZČĆŽŠĐ0-9]+"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter(String::isNotBlank)
            .sorted()
            .joinToString(" ")

    private companion object {
        const val KEY_MEMBERS = "members"
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
