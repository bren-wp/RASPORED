package hr.raspored.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import hr.raspored.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class RemoteAccount(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val accountType: String
) {
    val fullName: String
        get() = listOf(firstName, lastName).joinToString(" ").trim()
}

data class RemoteSession(
    val account: RemoteAccount,
    val token: String,
    val expiresAtEpochSeconds: Long
)

class RemoteSessionInvalidException(message: String) : IllegalStateException(message)

class RemoteAccountStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("raspored.remote_account", Context.MODE_PRIVATE)
    private val securePreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "raspored.remote_account.secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val token: String?
        get() {
            val expiresAt = securePreferences.getLong(KEY_EXPIRES_AT, 0L)
            if (expiresAt > 0L && expiresAt <= Instant.now().epochSecond) {
                clearCredentials()
                return null
            }

            securePreferences.getString(KEY_TOKEN, null)
                ?.takeIf { TOKEN.matches(it) }
                ?.let { return it }

            val legacy = preferences.getString(KEY_TOKEN, null)
                ?.takeIf { TOKEN.matches(it) }
                ?: return null

            return runCatching {
                securePreferences.edit().putString(KEY_TOKEN, legacy).commit()
                preferences.edit().remove(KEY_TOKEN).commit()
                legacy
            }.getOrElse {
                clearCredentials()
                null
            }
        }

    var account: RemoteAccount?
        get() {
            if (token == null) return null
            val email = preferences.getString(KEY_EMAIL, null).orEmpty()
            if (email.isBlank()) return null
            return RemoteAccount(
                firstName = preferences.getString(KEY_FIRST, "").orEmpty(),
                lastName = preferences.getString(KEY_LAST, "").orEmpty(),
                email = email,
                phone = preferences.getString(KEY_PHONE, "").orEmpty(),
                accountType = preferences.getString(KEY_TYPE, "individual").orEmpty()
            )
        }
        private set(value) {
            preferences.edit().apply {
                if (value == null) {
                    remove(KEY_FIRST)
                    remove(KEY_LAST)
                    remove(KEY_EMAIL)
                    remove(KEY_PHONE)
                    remove(KEY_TYPE)
                } else {
                    putString(KEY_FIRST, value.firstName.take(60))
                    putString(KEY_LAST, value.lastName.take(60))
                    putString(KEY_EMAIL, value.email.take(160))
                    putString(KEY_PHONE, value.phone.take(40))
                    putString(KEY_TYPE, if (value.accountType == "manager") "manager" else "individual")
                }
            }.apply()
        }

    fun save(session: RemoteSession) {
        require(TOKEN.matches(session.token)) { "Pristupni token nije valjan." }
        require(session.expiresAtEpochSeconds > Instant.now().epochSecond) { "Pristupna sesija je već istekla." }
        securePreferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            .commit()
        preferences.edit().remove(KEY_TOKEN).apply()
        account = session.account
    }

    fun updateAccount(account: RemoteAccount) {
        this.account = account
    }

    fun clearCredentials() {
        runCatching { securePreferences.edit().clear().commit() }
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    fun clear() {
        clearCredentials()
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_FIRST = "first_name"
        const val KEY_LAST = "last_name"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
        const val KEY_TYPE = "account_type"
        val TOKEN = Regex("""^[a-f0-9]{64}$""")
    }
}

object RemoteAccountClient {
    private fun endpoint(): URL =
        URL(BuildConfig.BACKEND_BASE_URL.trimEnd('/') + "/api/auth.php")

    fun login(email: String, password: String): RemoteSession =
        post(
            JSONObject()
                .put("action", "login")
                .put("email", email.trim())
                .put("password", password)
        )

    fun register(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        manager: Boolean
    ): RemoteSession =
        post(
            JSONObject()
                .put("action", "register")
                .put("firstName", firstName.trim())
                .put("lastName", lastName.trim())
                .put("email", email.trim())
                .put("phone", phone.trim())
                .put("password", password)
                .put("accountType", if (manager) "manager" else "individual")
        )

    fun current(token: String): RemoteAccount {
        val json = request(method = "GET", token = token)
        val accountJson = json.optJSONObject("account")
            ?: throw RemoteSessionInvalidException("Prijava više nije valjana.")
        return RemoteAccount(
            firstName = accountJson.optString("firstName"),
            lastName = accountJson.optString("lastName"),
            email = accountJson.optString("email"),
            phone = accountJson.optString("phone"),
            accountType = accountJson.optString("accountType", "individual")
        )
    }

    fun logout(token: String) {
        request(
            method = "POST",
            token = token,
            body = JSONObject().put("action", "logout").toString()
        )
    }

    private fun post(payload: JSONObject): RemoteSession {
        val json = request(method = "POST", body = payload.toString())
        val token = json.optString("token")
        val accountJson = json.optJSONObject("account")
            ?: throw IllegalStateException("Poslužitelj nije vratio korisnički račun.")
        if (!token.matches(Regex("""^[a-f0-9]{64}$"""))) {
            throw IllegalStateException("Poslužitelj nije vratio valjan pristupni token.")
        }
        val expiresAt = json.optString("expiresAt")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
            ?.takeIf { it > Instant.now().epochSecond }
            ?: throw IllegalStateException("Poslužitelj nije vratio valjan istek pristupne sesije.")
        return RemoteSession(
            account = RemoteAccount(
                firstName = accountJson.optString("firstName"),
                lastName = accountJson.optString("lastName"),
                email = accountJson.optString("email"),
                phone = accountJson.optString("phone"),
                accountType = accountJson.optString("accountType", "individual")
            ),
            token = token,
            expiresAtEpochSeconds = expiresAt
        )
    }

    private fun request(
        method: String,
        token: String? = null,
        body: String? = null
    ): JSONObject {
        val connection = endpoint().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Raspored-Client", "android")
        connection.setRequestProperty("X-Raspored-Request", "1")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
        }

        val status = connection.responseCode
        val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()

        val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        if (status !in 200..299 || json.optBoolean("ok", false) != true) {
            val message = json.optString("error").takeIf { it.isNotBlank() }
                ?: "Povezivanje s RASPORED računom nije uspjelo."
            if (token != null && status in setOf(401, 403)) {
                throw RemoteSessionInvalidException(message)
            }
            throw IllegalStateException(message)
        }
        return json
    }
}
