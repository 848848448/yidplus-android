package com.yidplus.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.ui.Tab
import org.json.JSONObject

/**
 * The site's own admin settings (Admin -> Features, read from yidplus.com/api/settings, the same call the website
 * makes): a feature the owner turns off there is off in the app too, for everyone except the people on that
 * feature's allow-list. The last copy is kept so the app opens instantly; it refreshes on every start and resume.
 * Only the feature/nav keys are kept on the phone.
 */
object SiteConfig {
    var s by mutableStateOf<Map<String, String>>(emptyMap()); private set
    /** Emulator QA only (an adb intent): show every section so each screen can be photographed. Never saved. */
    var qaShowAll by mutableStateOf(false)
    private lateinit var ctx: Context
    private val prefs get() = ctx.getSharedPreferences("site-config", Context.MODE_PRIVATE)
    private val KEEP = Regex("^(feat_[a-z]+(_allowed_me)?|registration_open|maintenance_mode|maintenance_message|app_title)$")

    fun init(c: Context) {
        ctx = c.applicationContext
        s = runCatching { JSONObject(prefs.getString("s", "{}")!!).let { j -> j.keys().asSequence().associateWith { j.optString(it) } } }.getOrDefault(emptyMap())
    }

    suspend fun refresh() {
        runCatching {
            val j = Api.get("/api/settings").optJSONObject("settings") ?: return
            val m = j.keys().asSequence().filter { KEEP.matches(it) }.associateWith { j.optString(it) }
            s = m
            prefs.edit().putString("s", JSONObject(m).toString()).apply()
        }
    }

    /** Same rule as the website's _applyFeatureFlags: off = hidden, unless this person is on the allow-list. */
    fun hidden(feature: String) = !qaShowAll && s["feat_$feature"] == "off" && s["feat_${feature}_allowed_me"] != "true"

    /** The website's bottom bar, in its order: Chats, Music, Shorts, Channels, Settings. */
    val tabs: List<Tab> get() = listOf(Tab.Chats, Tab.Music, Tab.Shorts, Tab.Home, Tab.Profile).filter { visible(it) }

    fun visible(t: Tab) = when (t) {
        Tab.Music -> !hidden("music")
        Tab.Shorts -> !hidden("shorts")
        Tab.Home -> !hidden("channels")
        else -> true
    }

    val guestOff get() = s["feat_guest"] == "off"
    val registrationOpen get() = s["registration_open"] != "false"
    val maintenance get() = s["maintenance_mode"] == "true"
    val maintenanceMessage get() = s["maintenance_message"]?.takeIf { it.isNotBlank() } ?: "We'll be back shortly."
}
