package com.yidplus.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The website's device-only display settings (Settings → Reading and help, Color Theme, Chat wallpaper, Silent calls).
 * The site keeps these in localStorage (yp_theme, yp_high_contrast, yp_reduced_motion, yp_chat_wallpaper, yp_silent_calls),
 * so the app keeps them on the phone too. Theme.kt and the chat screen read them from here.
 */
data class ColorPreset(val name: String, val accent: Color, val accentLight: Color, val bg: Color, val bg2: Color, val text: Color, val dark: Boolean = false)

/** The site's 7 colour themes (js/theme.js THEME_PRESETS), in the same order. */
val ColorPresets = listOf(
    ColorPreset("Emerald Teal", Color(0xFF008069), Color(0xFF00A884), Color(0xFFFAFAF8), Color(0xFFF1F0ED), Color(0xFF14151A)),
    ColorPreset("Ocean Blue", Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFFFAFAFB), Color(0xFFF0F4FA), Color(0xFF14151A)),
    ColorPreset("Forest", Color(0xFF2E7D32), Color(0xFF66BB6A), Color(0xFFFAFBFA), Color(0xFFF0F8F0), Color(0xFF14151A)),
    ColorPreset("Crimson", Color(0xFFC62828), Color(0xFFEF5350), Color(0xFFFBFAFA), Color(0xFFFAF0F0), Color(0xFF14151A)),
    ColorPreset("Violet", Color(0xFF6A1B9A), Color(0xFFAB47BC), Color(0xFFFBFAFB), Color(0xFFF6F0FA), Color(0xFF14151A)),
    ColorPreset("Amber", Color(0xFFB8860B), Color(0xFFDAA520), Color(0xFFFBFAF7), Color(0xFFF7F4EC), Color(0xFF14151A)),
    ColorPreset("Midnight", Color(0xFF2B8A73), Color(0xFF3EBFA1), Color(0xFF0A0A0F), Color(0xFF13131A), Color(0xFFF0EDE6), dark = true),
)

/** The site's chat wallpapers (js/theme.js CHAT_WALLPAPERS): id, name, light gradient start/end and dark gradient start/end (null = the default background). The dark gradient is a deep, desaturated version of the same hue, like WhatsApp's dark wallpapers; Dusk and Charcoal are already dark, so their dark gradient matches their light one. */
data class Wallpaper(val id: String, val name: String, val from: Color?, val to: Color?, val darkFrom: Color? = null, val darkTo: Color? = null)
val ChatWallpapers = listOf(
    Wallpaper("default", "Default", null, null),
    Wallpaper("mint", "Mint", Color(0xFFE7F5EE), Color(0xFFD3EBDF), Color(0xFF1C2E26), Color(0xFF121F18)),
    Wallpaper("sky", "Sky", Color(0xFFE3F0FB), Color(0xFFCFE3F5), Color(0xFF1B2A3A), Color(0xFF121E2B)),
    Wallpaper("sand", "Sand", Color(0xFFF6EFE2), Color(0xFFEADFC8), Color(0xFF2B2620), Color(0xFF1D1913)),
    Wallpaper("rose", "Rose", Color(0xFFFBE9EE), Color(0xFFF3D2DD), Color(0xFF2E1F26), Color(0xFF20141B)),
    Wallpaper("dusk", "Dusk", Color(0xFF2A2F45), Color(0xFF1C2033), Color(0xFF2A2F45), Color(0xFF1C2033)),
    Wallpaper("charcoal", "Charcoal", Color(0xFF23262B), Color(0xFF15171A), Color(0xFF23262B), Color(0xFF15171A)),
)
// The gradient for the current theme: the dark gradient in dark mode, the light one otherwise (falling back to the light gradient when no dark one is set).
fun Wallpaper.gradient(dark: Boolean): Pair<Color?, Color?> = if (dark) Pair(darkFrom ?: from, darkTo ?: to) else Pair(from, to)

object LookPrefs {
    private var ctx: Context? = null
    private fun p() = ctx?.getSharedPreferences("look", Context.MODE_PRIVATE)
    /** Index into ColorPresets; 0 = Emerald Teal (the site's default). */
    var colorTheme by mutableStateOf(0); private set
    var highContrast by mutableStateOf(false); private set
    var reduceMotion by mutableStateOf(false); private set
    var wallpaper by mutableStateOf("default"); private set
    var silentCalls by mutableStateOf(false); private set
    /** Top bar style: true = WhatsApp-2024 white bar with a green title, false = the solid green bar (default). */
    var lightBar by mutableStateOf(false); private set

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        val s = p() ?: return
        colorTheme = s.getInt("colorTheme", 0).coerceIn(0, ColorPresets.size - 1)
        highContrast = s.getBoolean("highContrast", false)
        reduceMotion = s.getBoolean("reduceMotion", false)
        wallpaper = s.getString("wallpaper", "default") ?: "default"
        silentCalls = s.getBoolean("silentCalls", false)
        lightBar = s.getBoolean("lightBar", false)
    }
    fun putColor(i: Int) { colorTheme = i.coerceIn(0, ColorPresets.size - 1); p()?.edit()?.putInt("colorTheme", colorTheme)?.apply() }
    fun putHighContrast(v: Boolean) { highContrast = v; p()?.edit()?.putBoolean("highContrast", v)?.apply() }
    fun putReduceMotion(v: Boolean) { reduceMotion = v; p()?.edit()?.putBoolean("reduceMotion", v)?.apply() }
    fun putWallpaper(id: String) { wallpaper = id; p()?.edit()?.putString("wallpaper", id)?.apply() }
    fun putLightBar(v: Boolean) { lightBar = v; p()?.edit()?.putBoolean("lightBar", v)?.apply() }
    fun putSilentCalls(v: Boolean) { silentCalls = v; p()?.edit()?.putBoolean("silentCalls", v)?.apply() }

    // Per-chat wallpaper (WhatsApp): a single chat can override the app-wide wallpaper. Stored on-device.
    var rwVersion by mutableStateOf(0); private set
    fun roomWallpaper(id: String): String? = p()?.getString("rw:$id", null)
    fun putRoomWallpaper(id: String, wallpaperId: String?) {
        val e = p()?.edit() ?: return
        if (wallpaperId == null || wallpaperId == "default") e.remove("rw:$id") else e.putString("rw:$id", wallpaperId)
        e.apply(); rwVersion++
    }

    /** The chosen colour theme. Theme.kt: primary = preset.accent (light) / preset.accentLight (dark); Midnight forces dark. */
    val preset: ColorPreset get() = ColorPresets[colorTheme]
    /** The chosen chat wallpaper, or null for the default background. */
    val chatWallpaper: Wallpaper? get() = ChatWallpapers.firstOrNull { it.id == wallpaper && it.from != null }
}

/**
 * The chosen chat wallpaper (js/theme.js applyChatWallpaper: a 160° gradient behind the messages). Put it on the
 * message list's container in the chat screen: `Modifier.fillMaxSize().chatWallpaper()`. Default = no change. In dark mode the preset's dark gradient is used.
 */
fun Modifier.chatWallpaper(roomId: String? = null): Modifier = composed {
    val ver = LookPrefs.rwVersion // recompose when a per-chat wallpaper changes
    val id = (roomId?.let { LookPrefs.roomWallpaper(it) }) ?: LookPrefs.wallpaper
    val w = if (ver >= 0) ChatWallpapers.firstOrNull { it.id == id && it.from != null } else null
    val (from, to) = w?.gradient(isDark()) ?: Pair(null, null)
    when {
        from != null && to != null -> this.background(Brush.linearGradient(listOf(from, to), start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
        // Default background: a faint WhatsApp-style doodle so the chat isn't a flat, "dry" colour.
        else -> this.chatDoodle(isDark())
    }
}

/** A subtle, evenly-tiled doodle (rings, dots, hearts) drawn very faintly behind the messages — WhatsApp's textured chat feel, no image asset needed. */
private fun Modifier.chatDoodle(dark: Boolean): Modifier = this.drawBehind {
    val ink = (if (dark) Color.White else Color(0xFF3A5A4A)).copy(alpha = if (dark) 0.035f else 0.05f)
    val step = 112f
    val sw = 2.2f
    var row = 0
    var y = 28f
    while (y < size.height + step) {
        val xOff = if (row % 2 == 0) 0f else step / 2f
        var x = 20f + xOff
        var col = 0
        while (x < size.width + step) {
            when ((row + col) % 3) {
                0 -> drawCircle(ink, radius = 13f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
                1 -> drawCircle(ink, radius = 3.2f, center = Offset(x, y))
                else -> { // tiny heart from two arcs + a point
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, y + 10f); cubicTo(x - 16f, y - 4f, x - 6f, y - 14f, x, y - 4f)
                        cubicTo(x + 6f, y - 14f, x + 16f, y - 4f, x, y + 10f); close()
                    }
                    drawPath(p, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
                }
            }
            x += step; col++
        }
        y += step; row++
    }
}
