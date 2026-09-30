package hr.raspored.app.data

import android.content.Context
import hr.raspored.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    val token: String
)

class RemoteAccountStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("raspored.remote_account", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString(KEY_TOKEN, null)
            ?.takeIf { it.matches(Regex("""^[a-f0-9]{64}$""")) }
        set(value) {
            preferences.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    var account: RemoteAccount?
        get() {
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
        set(value) {
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
        token = session.token
        account = session.account
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_FIRST = "first_name"
        const val KEY_LAST = "last_name"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
        const val KEY_TYPE = "account_type"
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
        return RemoteSession(
            account = RemoteAccount(
                firstName = accountJson.optString("firstName"),
                lastName = accountJson.optString("lastName"),
                email = accountJson.optString("email"),
                phone = accountJson.optString("phone"),
                accountType = accountJson.optString("accountType", "individual")
            ),
            token = token
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
            throw IllegalStateException(
                json.optString("error").takeIf { it.isNotBlank() }
                    ?: "Povezivanje s RASPORED računom nije uspjelo."
            )
        }
        return json
    }
}
