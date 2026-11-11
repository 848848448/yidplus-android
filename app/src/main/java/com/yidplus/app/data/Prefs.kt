package com.yidplus.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    private lateinit var ctx: Context
    private val p get() = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    /** 0 = follow the phone, 1 = light, 2 = dark */
    var theme by mutableStateOf(0)
    var muted by mutableStateOf(false)
    var notify by mutableStateOf(true)
    var keypadHintSeen by mutableStateOf(false)

    fun init(c: Context) {
        ctx = c.applicationContext
        theme = p.getInt("theme", 0); muted = p.getBoolean("muted", false); notify = p.getBoolean("notify", true)
        keypadHintSeen = p.getBoolean("keypadHint", false)
    }
    fun setThemeMode(v: Int) { theme = v; p.edit().putInt("theme", v).apply() }
    fun setMute(v: Boolean) { muted = v; p.edit().putBoolean("muted", v).apply() }
    fun enableNotify(v: Boolean) { notify = v; p.edit().putBoolean("notify", v).apply() }
    fun hintSeen() { keypadHintSeen = true; p.edit().putBoolean("keypadHint", true).apply() }

    // Guests' likes on For You stay on the phone (same as the website).
    fun localLikes(): MutableSet<String> = HashSet(p.getStringSet("likes", emptySet()) ?: emptySet())
    fun saveLocalLikes(s: Set<String>) { p.edit().putStringSet("likes", s.toList().takeLast(2000).toSet()).apply() }
    fun saved(): MutableSet<String> = HashSet(p.getStringSet("saved", emptySet()) ?: emptySet())
    fun saveSaved(s: Set<String>) { p.edit().putStringSet("saved", s).apply() }
    fun seen(): MutableList<String> = (p.getString("seen", "") ?: "").split('\n').filter { it.isNotBlank() }.toMutableList()
    fun saveSeen(l: List<String>) { p.edit().putString("seen", l.takeLast(5000).joinToString("\n")).apply() }
}
