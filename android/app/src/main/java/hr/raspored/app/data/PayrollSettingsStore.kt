package hr.raspored.app.data

import android.content.Context

data class PayrollSettings(
    val institutionId: String = "kbc-rijeka",
    val rateProfileId: String = "kbc-rijeka-observed-2026",
    val roleId: String = "kbc-transport-nss",
    val coefficient: Double = 1.15,
    val yearsService: Int = 0,
    val extraPercent: Double = 0.0,
    val customBase: Double? = null,
    val overtimeHours: Double? = null,
    val turnusHours: Double = 0.0,
    val secondShiftHours: Double = 0.0,
    val grossAdjustment: Double = 0.0
)

class PayrollSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.payroll_settings", Context.MODE_PRIVATE)

    fun load(): PayrollSettings {
        val institutionId = preferences.getString(KEY_INSTITUTION, null)
            .orEmpty().ifBlank { "kbc-rijeka" }
        val institution = PublicHealthPayroll.institution(institutionId)
        val roleId = preferences.getString(KEY_ROLE, null).orEmpty().ifBlank { "kbc-transport-nss" }
        val role = PublicHealthPayroll.role(roleId)
        val profileId = preferences.getString(KEY_RATE_PROFILE, null)
            .orEmpty().ifBlank { institution.defaultRateProfileId }

        return PayrollSettings(
            institutionId = institution.id,
            rateProfileId = PublicHealthPayroll.rateProfile(profileId).id,
            roleId = role.id,
            coefficient = preferences.getString(KEY_COEFFICIENT, null)?.toDoubleOrNull()
                ?.coerceIn(1.0, 8.0) ?: role.coefficient,
            yearsService = preferences.getInt(KEY_YEARS, 0).coerceIn(0, 60),
            extraPercent = preferences.getString(KEY_EXTRA, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 100.0) ?: 0.0,
            customBase = preferences.getString(KEY_CUSTOM_BASE, null)?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }?.coerceAtMost(10_000.0),
            overtimeHours = preferences.getString(KEY_OVERTIME_HOURS, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 250.0),
            turnusHours = preferences.getString(KEY_TURNUS_HOURS, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 300.0) ?: 0.0,
            secondShiftHours = preferences.getString(KEY_SECOND_SHIFT_HOURS, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 300.0) ?: 0.0,
            grossAdjustment = preferences.getString(KEY_GROSS_ADJUSTMENT, null)?.toDoubleOrNull()
                ?.coerceIn(-10_000.0, 10_000.0) ?: 0.0
        )
    }

    fun save(settings: PayrollSettings) {
        preferences.edit()
            .putString(KEY_INSTITUTION, settings.institutionId)
            .putString(KEY_RATE_PROFILE, settings.rateProfileId)
            .putString(KEY_ROLE, settings.roleId)
            .putString(KEY_COEFFICIENT, settings.coefficient.coerceIn(1.0, 8.0).toString())
            .putInt(KEY_YEARS, settings.yearsService.coerceIn(0, 60))
            .putString(KEY_EXTRA, settings.extraPercent.coerceIn(0.0, 100.0).toString())
            .putString(KEY_TURNUS_HOURS, settings.turnusHours.coerceIn(0.0, 300.0).toString())
            .putString(KEY_SECOND_SHIFT_HOURS, settings.secondShiftHours.coerceIn(0.0, 300.0).toString())
            .putString(KEY_GROSS_ADJUSTMENT, settings.grossAdjustment.coerceIn(-10_000.0, 10_000.0).toString())
            .apply {
                settings.customBase?.takeIf { it > 0.0 }?.let {
                    putString(KEY_CUSTOM_BASE, it.coerceAtMost(10_000.0).toString())
                } ?: remove(KEY_CUSTOM_BASE)
                settings.overtimeHours?.let {
                    putString(KEY_OVERTIME_HOURS, it.coerceIn(0.0, 250.0).toString())
                } ?: remove(KEY_OVERTIME_HOURS)
                remove(KEY_LEGACY_SECOND_SHIFT)
            }
            .apply()
    }

    private companion object {
        const val KEY_INSTITUTION = "institution_id"
        const val KEY_RATE_PROFILE = "rate_profile_id"
        const val KEY_ROLE = "role_id"
        const val KEY_COEFFICIENT = "coefficient"
        const val KEY_YEARS = "years_service"
        const val KEY_EXTRA = "extra_percent"
        const val KEY_CUSTOM_BASE = "custom_base"
        const val KEY_OVERTIME_HOURS = "overtime_hours"
        const val KEY_TURNUS_HOURS = "turnus_hours"
        const val KEY_SECOND_SHIFT_HOURS = "second_shift_hours"
        const val KEY_GROSS_ADJUSTMENT = "gross_adjustment"
        const val KEY_LEGACY_SECOND_SHIFT = "second_shift"
    }
}
