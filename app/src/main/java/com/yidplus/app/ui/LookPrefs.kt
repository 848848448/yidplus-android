package com.yidplus.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The website's device-only display settings (Settings → Reading and help, Color Theme, Chat wallpaper, Silent calls).
 * The site keeps these in localStorage (yp_theme, yp_high_contrast, yp_reduced_motion, yp_chat_wallpaper, yp_silent_calls),
 * so the app keeps them on the phone too. Theme.kt and the chat screen read them from here.
 */
data class ColorPreset(val name: String, val accent: Color, val accentLight: Color, val bg: Color, val bg2: Color, val text: Color, val dark: Boolean = false)

/** The site's 7 colour themes (js/theme.js THEME_PRESETS), in the same order. */
val ColorPresets = listOf(
    ColorPreset("Emerald Teal", Color(0xFF1F6F5C), Color(0xFF2B8A73), Color(0xFFFAFAF8), Color(0xFFF1F0ED), Color(0xFF14151A)),
    ColorPreset("Ocean Blue", Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFFFAFAFB), Color(0xFFF0F4FA), Color(0xFF14151A)),
    ColorPreset("Forest", Color(0xFF2E7D32), Color(0xFF66BB6A), Color(0xFFFAFBFA), Color(0xFFF0F8F0), Color(0xFF14151A)),
    ColorPreset("Crimson", Color(0xFFC62828), Color(0xFFEF5350), Color(0xFFFBFAFA), Color(0xFFFAF0F0), Color(0xFF14151A)),
    ColorPreset("Violet", Color(0xFF6A1B9A), Color(0xFFAB47BC), Color(0xFFFBFAFB), Color(0xFFF6F0FA), Color(0xFF14151A)),
    ColorPreset("Amber", Color(0xFFB8860B), Color(0xFFDAA520), Color(0xFFFBFAF7), Color(0xFFF7F4EC), Color(0xFF14151A)),
    ColorPreset("Midnight", Color(0xFF2B8A73), Color(0xFF3EBFA1), Color(0xFF0A0A0F), Color(0xFF13131A), Color(0xFFF0EDE6), dark = true),
)

/** The site's chat wallpapers (js/theme.js CHAT_WALLPAPERS): id, name, gradient start/end (null = the default background). */
data class Wallpaper(val id: String, val name: String, val from: Color?, val to: Color?)
val ChatWallpapers = listOf(
    Wallpaper("default", "Default", null, null),
    Wallpaper("mint", "Mint", Color(0xFFE7F5EE), Color(0xFFD3EBDF)),
    Wallpaper("sky", "Sky", Color(0xFFE3F0FB), Color(0xFFCFE3F5)),
    Wallpaper("sand", "Sand", Color(0xFFF6EFE2), Color(0xFFEADFC8)),
    Wallpaper("rose", "Rose", Color(0xFFFBE9EE), Color(0xFFF3D2DD)),
    Wallpaper("dusk", "Dusk", Color(0xFF2A2F45), Color(0xFF1C2033)),
    Wallpaper("charcoal", "Charcoal", Color(0xFF23262B), Color(0xFF15171A)),
)

object LookPrefs {
    private var ctx: Context? = null
    private fun p() = ctx?.getSharedPreferences("look", Context.MODE_PRIVATE)
    /** Index into ColorPresets; 0 = Emerald Teal (the site's default). */
    var colorTheme by mutableStateOf(0); private set
    var highContrast by mutableStateOf(false); private set
    var reduceMotion by mutableStateOf(false); private set
    var wallpaper by mutableStateOf("default"); private set
    var silentCalls by mutableStateOf(false); private set

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        val s = p() ?: return
        colorTheme = s.getInt("colorTheme", 0).coerceIn(0, ColorPresets.size - 1)
        highContrast = s.getBoolean("highContrast", false)
        reduceMotion = s.getBoolean("reduceMotion", false)
        wallpaper = s.getString("wallpaper", "default") ?: "default"
        silentCalls = s.getBoolean("silentCalls", false)
    }
    fun putColor(i: Int) { colorTheme = i.coerceIn(0, ColorPresets.size - 1); p()?.edit()?.putInt("colorTheme", colorTheme)?.apply() }
    fun putHighContrast(v: Boolean) { highContrast = v; p()?.edit()?.putBoolean("highContrast", v)?.apply() }
    fun putReduceMotion(v: Boolean) { reduceMotion = v; p()?.edit()?.putBoolean("reduceMotion", v)?.apply() }
    fun putWallpaper(id: String) { wallpaper = id; p()?.edit()?.putString("wallpaper", id)?.apply() }
    fun putSilentCalls(v: Boolean) { silentCalls = v; p()?.edit()?.putBoolean("silentCalls", v)?.apply() }

    /** The chosen colour theme. Theme.kt: primary = preset.accent (light) / preset.accentLight (dark); Midnight forces dark. */
    val preset: ColorPreset get() = ColorPresets[colorTheme]
    /** The chosen chat wallpaper, or null for the default background. */
    val chatWallpaper: Wallpaper? get() = ChatWallpapers.firstOrNull { it.id == wallpaper && it.from != null }
}
