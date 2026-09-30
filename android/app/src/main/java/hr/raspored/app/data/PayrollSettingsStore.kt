package hr.raspored.app.data

import android.content.Context

data class PayrollSettings(
    val roleId: String = "kbc-transport-nss",
    val coefficient: Double = 1.15,
    val yearsService: Int = 0,
    val extraPercent: Double = 0.0,
    val secondShift: Boolean = false,
    val customBase: Double? = null
)

class PayrollSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.payroll_settings", Context.MODE_PRIVATE)

    fun load(): PayrollSettings {
        val roleId = preferences.getString(KEY_ROLE, null).orEmpty().ifBlank { "kbc-transport-nss" }
        val role = PublicHealthPayroll.role(roleId)
        return PayrollSettings(
            roleId = role.id,
            coefficient = preferences.getString(KEY_COEFFICIENT, null)?.toDoubleOrNull()
                ?.coerceIn(1.0, 8.0) ?: role.coefficient,
            yearsService = preferences.getInt(KEY_YEARS, 0).coerceIn(0, 60),
            extraPercent = preferences.getString(KEY_EXTRA, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 100.0) ?: 0.0,
            secondShift = preferences.getBoolean(KEY_SECOND_SHIFT, false),
            customBase = preferences.getString(KEY_CUSTOM_BASE, null)?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }?.coerceAtMost(10_000.0)
        )
    }

    fun save(settings: PayrollSettings) {
        preferences.edit()
            .putString(KEY_ROLE, settings.roleId)
            .putString(KEY_COEFFICIENT, settings.coefficient.coerceIn(1.0, 8.0).toString())
            .putInt(KEY_YEARS, settings.yearsService.coerceIn(0, 60))
            .putString(KEY_EXTRA, settings.extraPercent.coerceIn(0.0, 100.0).toString())
            .putBoolean(KEY_SECOND_SHIFT, settings.secondShift)
            .apply {
                settings.customBase?.takeIf { it > 0.0 }?.let {
                    putString(KEY_CUSTOM_BASE, it.coerceAtMost(10_000.0).toString())
                } ?: remove(KEY_CUSTOM_BASE)
            }
            .apply()
    }

    private companion object {
        const val KEY_ROLE = "role_id"
        const val KEY_COEFFICIENT = "coefficient"
        const val KEY_YEARS = "years_service"
        const val KEY_EXTRA = "extra_percent"
        const val KEY_SECOND_SHIFT = "second_shift"
        const val KEY_CUSTOM_BASE = "custom_base"
    }
}
