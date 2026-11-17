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
        Accounts.init(ctx)
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
        if (m != null && changed) runCatching { com.yidplus.app.Push.start(ctx) }
        me = m
        prefs.edit().apply { if (m == null) remove("me") else putString("me", m.json().toString()) }.apply()
        // Keep the multi-account roster current: record this account + its live cookies whenever a sign-in is confirmed.
        if (m != null) runCatching { Accounts.record(m.id, m.nickname, m.photo, Api.cookieSnapshot()) }
        if (changed) Store.clear()
    }

    /** Switch to another saved account, then reload as them. Returns false if that account must sign in again. */
    suspend fun switchTo(id: String): Boolean {
        if (id == me?.id) return true
        if (!Accounts.switchTo(id)) return false
        me = null
        refresh()
        return me != null
    }

    /** Stash the current account and clear the session so the sign-in screen adds a new one. */
    fun beginAddAccount() { Accounts.beginAdd(); set(null) }

    /** Sign out of the current account only; if another saved account exists, switch into it instead of going empty. */
    suspend fun logoutThenMaybeSwitch(): Boolean {
        val leaving = me?.id
        runCatching { com.yidplus.app.Push.unregister(ctx) }
        runCatching { Api.post("/api/auth/logout") }
        if (leaving != null) Accounts.remove(leaving)
        val next = Accounts.list().firstOrNull()?.id
        if (next != null && Accounts.switchTo(next)) { me = null; refresh(); if (me != null) return true }
        Api.clearSession(); Disk.clear(); set(null)
        return false
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
        runCatching { com.yidplus.app.Push.unregister(ctx) }
        runCatching { Api.post("/api/auth/logout") }
        runCatching { me?.id?.let { Accounts.remove(it) } }
        Api.clearSession(); Disk.clear(); set(null)
    }

    /** After the browser hand-off (Google) or the website's sign-up page. */
    suspend fun adopt(token: String) { Api.setSession(token); refresh() }
}
