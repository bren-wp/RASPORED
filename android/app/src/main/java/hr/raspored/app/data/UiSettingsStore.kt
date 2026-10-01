package hr.raspored.app.data

import android.content.Context

class UiSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.ui_settings", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = preferences.getBoolean(KEY_DARK_MODE, true)
        set(value) { preferences.edit().putBoolean(KEY_DARK_MODE, value).apply() }

    var reducedMotion: Boolean
        get() = preferences.getBoolean(KEY_REDUCED_MOTION, false)
        set(value) { preferences.edit().putBoolean(KEY_REDUCED_MOTION, value).apply() }

    private companion object {
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_REDUCED_MOTION = "reduced_motion"
    }
}
