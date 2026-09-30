package hr.raspored.app.data

import android.content.Context

class ProfileStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.profile", Context.MODE_PRIVATE)

    var fullName: String
        get() = preferences.getString(KEY_NAME, "").orEmpty()
        set(value) {
            preferences.edit()
                .putString(KEY_NAME, value.trim().replace(Regex("""\s+"""), " ").take(80))
                .apply()
        }

    private companion object {
        const val KEY_NAME = "full_name"
    }
}
