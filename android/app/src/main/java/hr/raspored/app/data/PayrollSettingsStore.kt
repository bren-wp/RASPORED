package hr.raspored.app.data

import android.content.Context

data class PayrollSettings(
    val county: String = "Primorsko-goranska",
    val residence: String = "Rijeka",
    val taxLower: Double = 20.0,
    val taxHigher: Double = 25.0,
    val sector: String = "Zdravstvo",
    val institution: String = "Klinički bolnički centar Rijeka",
    val regimeId: String = "kbc-rijeka-2026",
    val roleId: String = "health-transport-sss",
    val coefficient: Double = 1.25,
    val yearsService: Int = 0,
    val personalAllowance: Double = PublicSectorPayroll.BASIC_PERSONAL_ALLOWANCE,
    val extraPercent: Double = 0.0,
    val secondShift: Boolean = false,
    val turnus: Boolean = false,
    val customBase: Double? = null
)

class PayrollSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.payroll_settings", Context.MODE_PRIVATE)

    fun load(): PayrollSettings {
        val regimeId = preferences.getString(KEY_REGIME, null).orEmpty().ifBlank { "kbc-rijeka-2026" }
        val regime = PublicSectorPayroll.regime(regimeId)
        val roleId = migrateRoleId(preferences.getString(KEY_ROLE, null).orEmpty())
        val role = PublicSectorPayroll.role(roleId, regime.id)
        return PayrollSettings(
            county = preferences.getString(KEY_COUNTY, null).orEmpty().ifBlank { "Primorsko-goranska" },
            residence = preferences.getString(KEY_RESIDENCE, null).orEmpty().ifBlank { "Rijeka" },
            taxLower = preferences.getString(KEY_TAX_LOWER, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 50.0) ?: 20.0,
            taxHigher = preferences.getString(KEY_TAX_HIGHER, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 50.0) ?: 25.0,
            sector = preferences.getString(KEY_SECTOR, null).orEmpty().ifBlank { regime.sector },
            institution = preferences.getString(KEY_INSTITUTION, null).orEmpty()
                .ifBlank { "Klinički bolnički centar Rijeka" },
            regimeId = regime.id,
            roleId = role.id,
            coefficient = preferences.getString(KEY_COEFFICIENT, null)?.toDoubleOrNull()
                ?.coerceIn(0.1, 10.0) ?: role.coefficient ?: 1.0,
            yearsService = preferences.getInt(KEY_YEARS, 0).coerceIn(0, 60),
            personalAllowance = preferences.getString(KEY_PERSONAL_ALLOWANCE, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 10_000.0) ?: PublicSectorPayroll.BASIC_PERSONAL_ALLOWANCE,
            extraPercent = preferences.getString(KEY_EXTRA, null)?.toDoubleOrNull()
                ?.coerceIn(0.0, 100.0) ?: 0.0,
            secondShift = preferences.getBoolean(KEY_SECOND_SHIFT, false),
            turnus = preferences.getBoolean(KEY_TURNUS, false),
            customBase = preferences.getString(KEY_CUSTOM_BASE, null)?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }?.coerceAtMost(10_000.0)
        )
    }

    fun save(settings: PayrollSettings) {
        preferences.edit()
            .putString(KEY_COUNTY, settings.county.take(80))
            .putString(KEY_RESIDENCE, settings.residence.take(100))
            .putString(KEY_TAX_LOWER, settings.taxLower.coerceIn(0.0, 50.0).toString())
            .putString(KEY_TAX_HIGHER, settings.taxHigher.coerceIn(0.0, 50.0).toString())
            .putString(KEY_SECTOR, settings.sector.take(100))
            .putString(KEY_INSTITUTION, settings.institution.take(160))
            .putString(KEY_REGIME, settings.regimeId.take(80))
            .putString(KEY_ROLE, settings.roleId.take(80))
            .putString(KEY_COEFFICIENT, settings.coefficient.coerceIn(0.1, 10.0).toString())
            .putInt(KEY_YEARS, settings.yearsService.coerceIn(0, 60))
            .putString(
                KEY_PERSONAL_ALLOWANCE,
                settings.personalAllowance.coerceIn(0.0, 10_000.0).toString()
            )
            .putString(KEY_EXTRA, settings.extraPercent.coerceIn(0.0, 100.0).toString())
            .putBoolean(KEY_SECOND_SHIFT, settings.secondShift)
            .putBoolean(KEY_TURNUS, settings.turnus)
            .apply {
                settings.customBase?.takeIf { it > 0.0 }?.let {
                    putString(KEY_CUSTOM_BASE, it.coerceAtMost(10_000.0).toString())
                } ?: remove(KEY_CUSTOM_BASE)
            }
            .apply()
    }

    private fun migrateRoleId(raw: String): String = when (raw) {
        "kbc-transport-nss" -> "health-transport-nss"
        "kbc-transport-sss" -> "health-transport-sss"
        "kbc-portir" -> "health-portir"
        "cleaner-special" -> "health-cleaner-special"
        "cleaner" -> "health-cleaner"
        "caregiver" -> "health-caregiver"
        "hospital-attendant" -> "health-bolnicar"
        "nurse-bacc-1" -> "health-nurse-bacc-1"
        "nurse-bacc-2" -> "health-nurse-bacc-2"
        "nurse-sss-1" -> "health-nurse-sss-1"
        "nurse-sss-2" -> "health-nurse-sss-2"
        "nurse-master-special" -> "health-nurse-master"
        "physio-bacc-1" -> "health-physio-1"
        "physio-bacc-2" -> "health-physio-2"
        "doctor-1" -> "health-doctor-1"
        "doctor-2" -> "health-doctor-2"
        "doctor-3" -> "health-doctor-3"
        "doctor-specialization" -> "health-doctor-specialization"
        "doctor-specialist-1" -> "health-doctor-specialist-1"
        "doctor-specialist-2" -> "health-doctor-specialist-2"
        "doctor-specialist-3" -> "health-doctor-specialist-3"
        "" -> "health-transport-sss"
        else -> raw
    }

    private companion object {
        const val KEY_COUNTY = "county"
        const val KEY_RESIDENCE = "residence"
        const val KEY_TAX_LOWER = "tax_lower"
        const val KEY_TAX_HIGHER = "tax_higher"
        const val KEY_SECTOR = "sector"
        const val KEY_INSTITUTION = "institution"
        const val KEY_REGIME = "regime_id"
        const val KEY_ROLE = "role_id"
        const val KEY_COEFFICIENT = "coefficient"
        const val KEY_YEARS = "years_service"
        const val KEY_PERSONAL_ALLOWANCE = "personal_allowance"
        const val KEY_EXTRA = "extra_percent"
        const val KEY_SECOND_SHIFT = "second_shift"
        const val KEY_TURNUS = "turnus"
        const val KEY_CUSTOM_BASE = "custom_base"
    }
}
