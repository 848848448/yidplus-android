package com.yidplus.app.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Prefs

// ── Brand ──────────────────────────────────────────────────────────────────────
// One brand colour (the site's green). Gold is only a small accent (the logo's "PLUS", the keypad focus ring,
// verified marks), never a badge or a big surface.
val Green = Color(0xFF1F6F5C)
val GreenDeep = Color(0xFF14221F)
val Gold = Color(0xFFD4AF37)
val GoldSoft = Color(0xFFF3E2A6)
/** Unread badges, online dots, "sent" ticks. A lighter green than the bar so it reads on both. */
val BadgeGreen = Color(0xFF25A36F)

private val Light = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFD2EEE5), onPrimaryContainer = Color(0xFF002019),
    inversePrimary = Color(0xFF86D5BB),
    secondary = Color(0xFF4A635B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DE), onSecondaryContainer = Color(0xFF062019),
    tertiary = Color(0xFF8A6D10), onTertiary = Color.White,
    tertiaryContainer = GoldSoft, onTertiaryContainer = Color(0xFF2A2000),
    background = Color(0xFFF6F8F7), onBackground = Color(0xFF171D1B),
    surface = Color.White, onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFEBF0EE), onSurfaceVariant = Color(0xFF5B6B66),
    surfaceTint = Green,
    inverseSurface = Color(0xFF2B3230), inverseOnSurface = Color(0xFFECF2EF),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFBFCAC6), outlineVariant = Color(0xFFE2E8E5), scrim = Color.Black,
    surfaceBright = Color.White, surfaceDim = Color(0xFFDDE4E1),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7FAF9), surfaceContainer = Color(0xFFF1F5F3),
    surfaceContainerHigh = Color(0xFFEBF0EE), surfaceContainerHighest = Color(0xFFE5EBE8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7DD3B6), onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF1C5446), onPrimaryContainer = Color(0xFFC6F0E1),
    inversePrimary = Green,
    secondary = Color(0xFFB1CCC2), onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF2A3F38), onSecondaryContainer = Color(0xFFCDE8DE),
    tertiary = Color(0xFFE3C35A), onTertiary = Color(0xFF3C2F00),
    tertiaryContainer = Color(0xFF574500), onTertiaryContainer = GoldSoft,
    background = Color(0xFF0E1412), onBackground = Color(0xFFE1E8E5),
    surface = Color(0xFF121A17), onSurface = Color(0xFFE1E8E5),
    surfaceVariant = Color(0xFF1F2A26), onSurfaceVariant = Color(0xFFA2B2AC),
    surfaceTint = Color(0xFF7DD3B6),
    inverseSurface = Color(0xFFE1E8E5), inverseOnSurface = Color(0xFF2B3230),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3E4C47), outlineVariant = Color(0xFF283330), scrim = Color.Black,
    surfaceBright = Color(0xFF2E3835), surfaceDim = Color(0xFF0E1412),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF151D1A), surfaceContainer = Color(0xFF19221F),
    surfaceContainerHigh = Color(0xFF212C28), surfaceContainerHighest = Color(0xFF2B3632),
)

/** The app's own look settings that the website keeps on the phone too (Text size). */
object UiPrefs {
    private var ctx: Context? = null
    /** 0 = small, 1 = normal, 2 = large (the website's "Text Size"). */
    var textSize by mutableStateOf(1); private set
    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        textSize = c.applicationContext.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("textSize", 1).coerceIn(0, 2)
    }
    fun setText(v: Int) {
        textSize = v.coerceIn(0, 2)
        ctx?.getSharedPreferences("ui", Context.MODE_PRIVATE)?.edit()?.putInt("textSize", textSize)?.apply()
    }
    val scale get() = when (textSize) { 0 -> 0.9f; 2 -> 1.15f; else -> 1f }
}

private fun TextStyle.sized(size: Float, line: Float, w: FontWeight, s: Float, track: Float = 0f) =
    copy(fontSize = (size * s).sp, lineHeight = (line * s).sp, fontWeight = w, letterSpacing = track.sp)

/** One type scale for every screen (bigger, like WhatsApp): 20 sp bar titles, 17 sp row titles, 16 sp body, 15 sp previews, 13 sp times. */
fun yidTypography(s: Float): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.sized(48f, 56f, FontWeight.Normal, s),
        displayMedium = b.displayMedium.sized(40f, 48f, FontWeight.Normal, s),
        displaySmall = b.displaySmall.sized(32f, 40f, FontWeight.Normal, s),
        headlineLarge = b.headlineLarge.sized(28f, 36f, FontWeight.SemiBold, s),
        headlineMedium = b.headlineMedium.sized(24f, 32f, FontWeight.SemiBold, s),
        headlineSmall = b.headlineSmall.sized(22f, 28f, FontWeight.SemiBold, s),
        titleLarge = b.titleLarge.sized(20f, 26f, FontWeight.SemiBold, s),
        titleMedium = b.titleMedium.sized(17f, 23f, FontWeight.SemiBold, s, 0.1f),
        titleSmall = b.titleSmall.sized(14f, 20f, FontWeight.SemiBold, s, 0.1f),
        bodyLarge = b.bodyLarge.sized(16f, 22f, FontWeight.Normal, s, 0.15f),
        bodyMedium = b.bodyMedium.sized(15f, 21f, FontWeight.Normal, s, 0.15f),
        bodySmall = b.bodySmall.sized(13f, 18f, FontWeight.Normal, s, 0.2f),
        labelLarge = b.labelLarge.sized(14f, 20f, FontWeight.SemiBold, s, 0.1f),
        labelMedium = b.labelMedium.sized(13f, 18f, FontWeight.Medium, s, 0.3f),
        labelSmall = b.labelSmall.sized(12f, 16f, FontWeight.Medium, s, 0.4f),
    )
}

private val YidShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDark(): Boolean = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

/** The brand bar's colour: the site's green by day, a deep green-grey at night (like WhatsApp's dark header). */
@Composable
fun topBarColor(): Color = if (isDark()) Color(0xFF1A2723) else Green

@Composable
fun YidTheme(content: @Composable () -> Unit) {
    UiPrefs.init(LocalContext.current)
    val dark = isDark()
    val scheme = if (dark) Dark else Light
    val scale = UiPrefs.scale
    val type = remember(scale) { yidTypography(scale) }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = YidShapes) {
        // Text and icons drawn straight on the page (not inside a Surface) take the page's own ink, so dark mode is right everywhere.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
