package com.yidplus.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Who is signed in. Starts from the saved copy so the app opens instantly, then checks with the site. */
object Session {
    var me by mutableStateOf<Me?>(null)
    private lateinit var ctx: Context
    private val prefs get() = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun init(c: Context) {
        ctx = c.applicationContext
        if (Api.hasSession()) me = prefs.getString("me", null)?.let { runCatching { Me.from(JSONObject(it)) }.getOrNull() }
    }

    suspend fun refresh() {
        if (!Api.hasSession()) { set(null); return }
        try {
            val j = Api.get("/api/auth/me")
            set(j.optJSONObject("user")?.let { Me.from(it) })
        } catch (e: Api.ApiError) {
            if (e.code == 401) { Api.clearSession(); set(null) }
        } catch (_: Exception) { /* offline: keep the saved copy */ }
    }

    private fun set(m: Me?) {
        val changed = m?.id != me?.id
        me = m
        prefs.edit().apply { if (m == null) remove("me") else putString("me", m.json().toString()) }.apply()
        if (changed) Store.clear()
    }

    sealed class Login { object Ok : Login(); object Need2fa : Login(); data class Fail(val msg: String) : Login() }

    suspend fun login(email: String, password: String, code: String?): Login {
        return try {
            val body = JSONObject().put("email", email.trim()).put("password", password)
            if (!code.isNullOrBlank()) body.put("totp_code", code.trim())
            Api.post("/api/auth/login", body)
            refresh()
            if (me != null) Login.Ok else Login.Fail("Signed in, but the session didn't stick. Try again.")
        } catch (e: Api.ApiError) {
            if (e.body?.optBoolean("need_2fa") == true) Login.Need2fa else Login.Fail(e.message ?: "Couldn't sign in")
        } catch (e: Exception) { Login.Fail(Api.friendly(e)) }
    }

    suspend fun logout() {
        runCatching { Api.post("/api/auth/logout") }
        Api.clearSession(); Disk.clear(); set(null)
    }

    /** After the browser hand-off (Google) or the website's sign-up page. */
    suspend fun adopt(token: String) { Api.setSession(token); refresh() }
}
