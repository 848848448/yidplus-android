package com.yidplus.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yidplus.app.data.Prefs

val Green = Color(0xFF1F6F5C)
val GreenDeep = Color(0xFF14221F)
val Gold = Color(0xFFD4AF37)
val GoldSoft = Color(0xFFF3E2A6)

private val Light = lightColorScheme(
    primary = Green, onPrimary = Color.White, primaryContainer = Color(0xFFCDEBE2), onPrimaryContainer = GreenDeep,
    secondary = Gold, onSecondary = GreenDeep, secondaryContainer = GoldSoft, onSecondaryContainer = GreenDeep,
    background = Color(0xFFF0F5F3), onBackground = Color(0xFF14231E), surface = Color.White, onSurface = Color(0xFF14231E),
    surfaceVariant = Color(0xFFE3ECE9), onSurfaceVariant = Color(0xFF4F625C), outline = Color(0xFFB9C9C3), error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF6CC9AE), onPrimary = GreenDeep, primaryContainer = Color(0xFF1F6F5C), onPrimaryContainer = Color(0xFFE6F4EF),
    secondary = Gold, onSecondary = GreenDeep, secondaryContainer = Color(0xFF5B4B17), onSecondaryContainer = GoldSoft,
    background = GreenDeep, onBackground = Color(0xFFEEF3F1), surface = Color(0xFF1B2C27), onSurface = Color(0xFFEEF3F1),
    surfaceVariant = Color(0xFF263A33), onSurfaceVariant = Color(0xFFA9B7B2), outline = Color(0xFF52665F), error = Color(0xFFF2B8B5),
)

@Composable
fun isDark(): Boolean = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

@Composable
fun YidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark()) Dark else Light, content = content)
}
