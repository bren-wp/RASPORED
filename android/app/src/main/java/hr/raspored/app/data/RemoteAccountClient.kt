package hr.raspored.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import hr.raspored.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

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
        val TOKEN = Regex("""^[a-f0-9]{64}$""")
    }
}

object RemoteAccountClient {
    private fun endpoint(): URL =
        URL(BuildConfig.BACKEND_BASE_URL.trimEnd('/') + "/api/auth.php")

    fun current(token: String) {
        request(method = "GET", token = token)
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
