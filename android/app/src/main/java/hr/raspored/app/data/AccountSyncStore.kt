package hr.raspored.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudAccount(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val accountType: String
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
    val isManager: Boolean
        get() = accountType == "manager"
}

data class CloudSnapshot(
    val revision: Int,
    val schedule: Map<String, String>,
    val evidence: List<TimeEvidenceEntry>,
    val payroll: PayrollSettings?,
    val teamMembers: List<TeamMember>,
    val account: CloudAccount?
)

sealed class CloudResult<out T> {
    data class Success<T>(val value: T) : CloudResult<T>()
    data class Error(val message: String) : CloudResult<Nothing>()
}

class AccountSyncStore(private val context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.cloud_account", Context.MODE_PRIVATE)
    private val secureToken = SecureTokenStore(context)

    val account: CloudAccount?
        get() = preferences.getString(KEY_ACCOUNT, null)?.let(::parseAccount)

    val isAuthenticated: Boolean
        get() = account != null && secureToken.read().isNotBlank()

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        manager: Boolean
    ): CloudResult<CloudAccount> = authenticate(
        JSONObject()
            .put("action", "register")
            .put("firstName", firstName.trim())
            .put("lastName", lastName.trim())
            .put("email", email.trim())
            .put("phone", phone.trim())
            .put("password", password)
            .put("accountType", if (manager) "manager" else "individual")
    )

    suspend fun login(email: String, password: String): CloudResult<CloudAccount> =
        authenticate(
            JSONObject()
                .put("action", "login")
                .put("email", email.trim())
                .put("password", password)
        )

    suspend fun logout(): CloudResult<Unit> = withContext(Dispatchers.IO) {
        val token = secureToken.read()
        if (token.isBlank()) {
            clearSession()
            return@withContext CloudResult.Success(Unit)
        }
        val response = request(
            path = "/api/auth.php",
            method = "POST",
            token = token,
            body = JSONObject().put("action", "logout")
        )
        clearSession()
        if (response.first in 200..299) CloudResult.Success(Unit)
        else CloudResult.Error(response.second.optString("error", "Odjava sa servera nije uspjela."))
    }

    suspend fun refreshAccount(): CloudResult<CloudAccount?> = withContext(Dispatchers.IO) {
        val token = secureToken.read()
        if (token.isBlank()) return@withContext CloudResult.Success(null)
        val response = request("/api/auth.php", "GET", token, null)
        if (response.first !in 200..299) {
            if (response.first == 401) clearSession()
            return@withContext CloudResult.Error(
                response.second.optString("error", "Račun trenutačno nije dostupan.")
            )
        }
        val accountJson = response.second.optJSONObject("account")
        val parsed = accountJson?.let(::accountFromJson)
        if (parsed != null) saveAccount(parsed)
        CloudResult.Success(parsed)
    }

    suspend fun pullState(): CloudResult<CloudSnapshot> = withContext(Dispatchers.IO) {
        val token = secureToken.read()
        if (token.isBlank()) {
            return@withContext CloudResult.Error("Prijavi se za preuzimanje podataka s računa.")
        }
        val response = request("/api/state.php", "GET", token, null)
        if (response.first !in 200..299) {
            if (response.first == 401) clearSession()
            return@withContext CloudResult.Error(
                response.second.optString("error", "Podatke nije moguće preuzeti.")
            )
        }
        val snapshot = parseSnapshot(response.second)
        snapshot.account?.let(::saveAccount)
        CloudResult.Success(snapshot)
    }

    suspend fun pushState(
        schedule: Map<String, String>,
        evidence: List<TimeEvidenceEntry>,
        payroll: PayrollSettings,
        teamMembers: List<TeamMember>
    ): CloudResult<CloudSnapshot> = withContext(Dispatchers.IO) {
        val token = secureToken.read()
        val currentAccount = account
        if (token.isBlank() || currentAccount == null) {
            return@withContext CloudResult.Error("Prijavi se za spremanje podataka na račun.")
        }
        val state = JSONObject()
            .put("schedule", scheduleJson(schedule))
            .put("evidence", evidenceJson(evidence))
            .put("payroll", payrollJson(payroll))
        if (currentAccount.isManager) {
            state.put("teamMembers", teamJson(teamMembers))
        }
        val payload = JSONObject().put("patch", true).put("state", state)
        val response = request("/api/state.php", "PUT", token, payload)
        if (response.first !in 200..299) {
            if (response.first == 401) clearSession()
            return@withContext CloudResult.Error(
                response.second.optString("error", "Podatke nije moguće spremiti na račun.")
            )
        }
        val snapshot = parseSnapshot(response.second)
        snapshot.account?.let(::saveAccount)
        CloudResult.Success(snapshot)
    }

    private suspend fun authenticate(payload: JSONObject): CloudResult<CloudAccount> =
        withContext(Dispatchers.IO) {
            val response = request("/api/auth.php", "POST", null, payload)
            if (response.first !in 200..299) {
                return@withContext CloudResult.Error(
                    response.second.optString("error", "Prijava nije uspjela.")
                )
            }
            val token = response.second.optString("token")
            val accountJson = response.second.optJSONObject("account")
            val parsed = accountJson?.let(::accountFromJson)
            if (token.length != 64 || parsed == null) {
                return@withContext CloudResult.Error("Server nije vratio valjanu prijavu.")
            }
            secureToken.write(token)
            saveAccount(parsed)
            CloudResult.Success(parsed)
        }

    private fun request(
        path: String,
        method: String,
        token: String?,
        body: JSONObject?
    ): Pair<Int, JSONObject> {
        val connection = (URL(SERVER_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Raspored-Client", "android")
            setRequestProperty("X-Raspored-Request", "1")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            status to json
        } catch (error: Exception) {
            599 to JSONObject().put(
                "error",
                "Nije moguće povezati se s RASPORED računom. Provjeri internetsku vezu."
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSnapshot(payload: JSONObject): CloudSnapshot {
        val state = payload.optJSONObject("state") ?: JSONObject()
        val scheduleObject = state.optJSONObject("schedule") ?: JSONObject()
        val schedule = buildMap {
            val keys = scheduleObject.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                val code = scheduleObject.optString(date)
                if (DATE.matches(date) && code in VALID_CODES) put(date, code)
            }
        }
        val evidence = parseEvidence(state.optJSONArray("evidence") ?: JSONArray())
        val payroll = state.optJSONObject("payroll")?.let(::parsePayroll)
        val team = parseTeam(state.optJSONArray("teamMembers") ?: JSONArray())
        val parsedAccount = payload.optJSONObject("account")?.let(::accountFromJson) ?: account
        return CloudSnapshot(
            revision = state.optInt("revision", 0),
            schedule = schedule,
            evidence = evidence,
            payroll = payroll,
            teamMembers = team,
            account = parsedAccount
        )
    }

    private fun parseEvidence(array: JSONArray): List<TimeEvidenceEntry> {
        val zone = ZoneId.systemDefault()
        val entries = mutableListOf<TimeEvidenceEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val storedStart = item.optLong("startedAt", Long.MIN_VALUE)
            val started: Long = if (storedStart > 0L) {
                storedStart
            } else {
                parseDateTime(item.optString("date"), item.optString("in"), zone) ?: continue
            }
            val storedEnd = item.optLong("endedAt", Long.MIN_VALUE)
            val ended: Long? = if (storedEnd > 0L) {
                storedEnd.coerceAtLeast(started)
            } else {
                val out = item.optString("out")
                if (out.isBlank()) {
                    null
                } else {
                    parseDateTime(item.optString("date"), out, zone)?.let { candidate: Long ->
                        if (candidate <= started) candidate + DAY_MILLIS else candidate
                    }
                }
            }
            entries += TimeEvidenceEntry(
                id = started,
                startedAt = started,
                endedAt = ended,
                note = item.optString("note").take(500),
                workType = WorkType.normalized(item.optString("workType"))
            )
        }
        return entries.distinctBy { entry -> entry.startedAt }
            .sortedBy { entry -> entry.startedAt }
    }

    private fun parseDateTime(date: String, time: String, zone: ZoneId): Long? = runCatching {
        java.time.LocalDateTime.parse(
            "$date" + "T" + "$time",
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        ).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parsePayroll(json: JSONObject): PayrollSettings = PayrollSettings(
        county = json.optString("county", "Primorsko-goranska").take(80),
        residence = json.optString("residence", "Rijeka").take(100),
        taxLower = json.optDouble("taxLower", 20.0).coerceIn(0.0, 50.0),
        taxHigher = json.optDouble("taxHigher", 25.0).coerceIn(0.0, 50.0),
        sector = json.optString("sector", "Zdravstvo").take(100),
        institution = json.optString("institution", "Klinički bolnički centar Rijeka").take(160),
        regimeId = json.optString("regimeId", "kbc-rijeka-2026").take(80),
        roleId = json.optString("roleId", "health-transport-sss").take(80),
        coefficient = json.optDouble("coefficient", 1.25).coerceIn(0.1, 10.0),
        yearsService = json.optInt("yearsService", 0).coerceIn(0, 60),
        personalAllowance = json.optDouble("personalAllowance", 600.0).coerceIn(0.0, 10_000.0),
        extraPercent = json.optDouble("extraPercent", 0.0).coerceIn(0.0, 100.0),
        secondShift = json.optBoolean("secondShift", false),
        turnus = json.optBoolean("turnus", false),
        customBase = if (json.isNull("customBase")) null
        else json.optDouble("customBase").takeIf { it > 0.0 }?.coerceAtMost(10_000.0)
    )

    private fun parseTeam(array: JSONArray): List<TeamMember> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim().replace(Regex("""\s+"""), " ").take(100)
            if (name.length < 2) continue
            val rawSchedule = item.optJSONObject("schedule") ?: JSONObject()
            val schedule = buildMap {
                val keys = rawSchedule.keys()
                while (keys.hasNext()) {
                    val date = keys.next()
                    val code = rawSchedule.optString(date)
                    if (DATE.matches(date) && code in VALID_CODES) put(date, code)
                }
            }
            add(TeamMember(name, item.optString("note").take(120), schedule))
        }
    }

    private fun scheduleJson(schedule: Map<String, String>): JSONObject = JSONObject().apply {
        schedule.toSortedMap().forEach { (date, code) ->
            if (DATE.matches(date) && code in VALID_CODES) put(date, code)
        }
    }

    private fun evidenceJson(entries: List<TimeEvidenceEntry>): JSONArray {
        val zone = ZoneId.systemDefault()
        return JSONArray().apply {
            entries.takeLast(3000).forEach { entry ->
                val start = Instant.ofEpochMilli(entry.startedAt).atZone(zone)
                val end = entry.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
                put(
                    JSONObject()
                        .put("id", entry.id.toString())
                        .put("date", start.toLocalDate().toString())
                        .put("in", start.toLocalTime().format(TIME_FORMAT))
                        .put("out", end?.toLocalTime()?.format(TIME_FORMAT) ?: JSONObject.NULL)
                        .put("note", entry.note.take(500))
                        .put("workType", WorkType.normalized(entry.workType))
                        .put("startedAt", entry.startedAt)
                        .put("endedAt", entry.endedAt ?: JSONObject.NULL)
                )
            }
        }
    }

    private fun payrollJson(value: PayrollSettings): JSONObject = JSONObject()
        .put("county", value.county)
        .put("residence", value.residence)
        .put("taxLower", value.taxLower)
        .put("taxHigher", value.taxHigher)
        .put("sector", value.sector)
        .put("institution", value.institution)
        .put("regimeId", value.regimeId)
        .put("roleId", value.roleId)
        .put("coefficient", value.coefficient)
        .put("yearsService", value.yearsService)
        .put("personalAllowance", value.personalAllowance)
        .put("extraPercent", value.extraPercent)
        .put("secondShift", value.secondShift)
        .put("turnus", value.turnus)
        .put("customBase", value.customBase ?: JSONObject.NULL)

    private fun teamJson(values: List<TeamMember>): JSONArray = JSONArray().apply {
        values.take(100).forEach { member ->
            put(
                JSONObject()
                    .put("name", member.name.take(100))
                    .put("note", member.note.take(120))
                    .put("schedule", scheduleJson(member.schedule))
            )
        }
    }

    private fun accountFromJson(json: JSONObject): CloudAccount? {
        val id = json.optString("id")
        val email = json.optString("email")
        if (id.isBlank() || email.isBlank()) return null
        return CloudAccount(
            id = id,
            firstName = json.optString("firstName").take(60),
            lastName = json.optString("lastName").take(60),
            email = email.take(160),
            phone = json.optString("phone").take(30),
            accountType = if (json.optString("accountType") == "manager") "manager" else "individual"
        )
    }

    private fun saveAccount(account: CloudAccount) {
        val json = JSONObject()
            .put("id", account.id)
            .put("firstName", account.firstName)
            .put("lastName", account.lastName)
            .put("email", account.email)
            .put("phone", account.phone)
            .put("accountType", account.accountType)
        preferences.edit().putString(KEY_ACCOUNT, json.toString()).apply()
    }

    private fun parseAccount(raw: String): CloudAccount? =
        runCatching { accountFromJson(JSONObject(raw)) }.getOrNull()

    private fun clearSession() {
        secureToken.clear()
        preferences.edit().remove(KEY_ACCOUNT).apply()
    }

    private companion object {
        const val SERVER_BASE = "https://raspored.eu"
        const val KEY_ACCOUNT = "account"
        const val DAY_MILLIS = 86_400_000L
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        val VALID_CODES = setOf("D", "N", "GO", "BO", "PD", "SD")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private class SecureTokenStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.cloud_secure", Context.MODE_PRIVATE)

    fun write(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String {
        val iv = preferences.getString(KEY_IV, null) ?: return ""
        val encrypted = preferences.getString(KEY_TOKEN, null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            )
        }.getOrElse {
            clear()
            ""
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ALIAS = "raspored.cloud.token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_IV = "token_iv"
        const val KEY_TOKEN = "token_ciphertext"
    }
}
