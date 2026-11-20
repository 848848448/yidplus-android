package com.yidplus.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multiple signed-in accounts, like WhatsApp. Each account keeps its own cookie set (the yp_session sign-in) stashed
 * here, plus its name and photo for the switcher. Switching swaps the live cookie jar and clears the caches, so the
 * app reloads as the other person. Adding an account stashes the current one and opens a clean sign-in.
 */
object Accounts {
    data class Acct(val id: String, val nick: String, val photo: String?)

    private lateinit var p: android.content.SharedPreferences
    /** Bumped on any change so Compose switchers recompose. */
    var version by mutableStateOf(0); private set

    fun init(ctx: Context) { if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE) }

    private fun load(): JSONObject = runCatching { JSONObject(p.getString("data", null) ?: "{}") }.getOrDefault(JSONObject())
    private fun save(o: JSONObject) { p.edit().putString("data", o.toString()).apply(); version++ }

    fun activeId(): String? = load().optString("active", "").ifBlank { null }

    /** All saved accounts (active first), for the switcher. */
    fun list(): List<Acct> {
        val o = load(); val arr = o.optJSONArray("list") ?: return emptyList()
        val active = o.optString("active", "")
        val out = ArrayList<Acct>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            out.add(Acct(a.optString("id"), a.optString("nick").ifBlank { "Account" }, a.optString("photo").ifBlank { null }))
        }
        return out.sortedByDescending { it.id == active }
    }

    fun count(): Int = load().optJSONArray("list")?.length() ?: 0

    /** Record/refresh the signed-in account (metadata + its current cookies). Called on every confirmed sign-in. */
    fun record(id: String, nick: String, photo: String?, cookies: Map<String, String>) {
        if (id.isBlank() || cookies.isEmpty()) return
        val o = load(); val arr = o.optJSONArray("list") ?: JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            if (a.optString("id") == id) { a.put("nick", nick).put("photo", photo ?: "").put("cookies", JSONObject(cookies)); found = true; break }
        }
        if (!found) arr.put(JSONObject().put("id", id).put("nick", nick).put("photo", photo ?: "").put("cookies", JSONObject(cookies)))
        save(o.put("list", arr).put("active", id))
    }

    /** Stash the live cookies into whichever account is active (call right before switching away). */
    fun stashCurrent(cookies: Map<String, String>) {
        val id = activeId() ?: return
        if (cookies.isEmpty()) return
        val o = load(); val arr = o.optJSONArray("list") ?: return
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            if (a.optString("id") == id) { a.put("cookies", JSONObject(cookies)); break }
        }
        save(o.put("list", arr))
    }

    fun cookiesFor(id: String): Map<String, String>? {
        val arr = load().optJSONArray("list") ?: return null
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            if (a.optString("id") == id) {
                val c = a.optJSONObject("cookies") ?: return null
                return c.keys().asSequence().mapNotNull { k -> c.optString(k).takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
            }
        }
        return null
    }

    fun remove(id: String) {
        val o = load(); val arr = o.optJSONArray("list") ?: return
        val keep = JSONArray()
        for (i in 0 until arr.length()) { val a = arr.optJSONObject(i) ?: continue; if (a.optString("id") != id) keep.put(a) }
        o.put("list", keep)
        if (o.optString("active") == id) o.remove("active")
        save(o)
    }

    /** Switch the live session to a saved account. Returns false if its cookies are gone (caller should re-sign-in). */
    fun switchTo(id: String): Boolean {
        val target = cookiesFor(id) ?: return false
        stashCurrent(Api.cookieSnapshot())            // keep the account we're leaving fresh
        Api.restoreCookies(target)
        val o = load(); save(o.put("active", id))
        Disk.clear(); Store.clear()                    // drop the previous account's cached screens
        return true
    }

    /** Begin adding another account: keep the current one saved, clear the live session for a fresh sign-in. */
    fun beginAdd() {
        stashCurrent(Api.cookieSnapshot())
        Api.clearSession(); Disk.clear(); Store.clear()
    }
}
