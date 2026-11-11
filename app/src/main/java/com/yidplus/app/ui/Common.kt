package com.yidplus.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** True once a keypad / D-pad key was pressed (Qin F21): screens then put the focus on their first item. */
object Keys { var keypad by mutableStateOf(false) }

/** Clickable that works the same by touch and by D-pad: a gold ring + tint shows where the focus is, OK selects. */
fun Modifier.tap(shape: Shape = RoundedCornerShape(10.dp), role: Role = Role.Button, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    this
        .clip(shape)
        .then(if (focused && Keys.keypad) Modifier.background(Gold.copy(alpha = 0.18f)).border(2.dp, Gold, shape) else Modifier)
        .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = role, onClick = onClick)
}

/** Put the keypad focus here when the screen opens (only on keypad phones). */
fun Modifier.firstFocus(always: Boolean = false): Modifier = composed {
    val fr = remember { FocusRequester() }
    val k = Keys.keypad
    var got by remember { mutableStateOf(false) }
    // A list item may not be placed yet (or the window not focused yet) on the first try: try again for up to ~2 s,
    // and stop for good once it has had the focus, so it never pulls the focus back after you move.
    LaunchedEffect(k) { if (k || always) { for (i in 0 until 16) { delay(if (i == 0) 60 else 120); if (got) break; runCatching { fr.requestFocus() } } } }
    this.focusRequester(fr).onFocusChanged { if (it.isFocused || it.hasFocus) got = true }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, sub: String? = null, lead: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, size = 44.dp) else Spacer(Modifier.width(12.dp))
            lead?.invoke()
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            actions()
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).tap(CircleShape, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("YID", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Text(" PLUS", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Gold)
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Dp = 40.dp, emoji: String? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
        val letter = emoji?.takeIf { it.isNotBlank() } ?: name.trim().take(1).uppercase().ifBlank { "Y" }
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun Badge(n: Int) {
    if (n <= 0) return
    Box(Modifier.clip(CircleShape).background(Gold).padding(horizontal = 6.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else n.toString(), color = GreenDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp)) }
}

@Composable
fun Problem(message: String, offline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(10.dp))
        Text(if (offline) "You're offline" else "Couldn't load this", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (offline) "Check your internet. This page loads by itself when you're back online." else message,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(14.dp))
        PrimaryButton("Try again", onRetry, Modifier.firstFocus())
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.heightIn(min = 44.dp).tap(RoundedCornerShape(22.dp), enabled = enabled, onClick = onClick).background(if (enabled) color else color.copy(alpha = .4f)).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (color == Gold) GreenDeep else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.heightIn(min = 44.dp).tap(RoundedCornerShape(22.dp), onClick = onClick).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp))
}

private val iso = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}
fun parseTime(s: String?): Long? = s?.let { runCatching { iso.get()!!.parse(it.take(19))?.time }.getOrNull() }
fun ago(s: String?): String {
    val t = parseTime(s) ?: return ""
    val d = (System.currentTimeMillis() - t) / 1000
    return when {
        d < 60 -> "now"
        d < 3600 -> "${d / 60}m"
        d < 86400 -> "${d / 3600}h"
        d < 7 * 86400 -> "${d / 86400}d"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(t))
    }
}
fun clock(s: String?): String = parseTime(s)?.let { SimpleDateFormat("h:mm a", Locale.US).format(Date(it)) } ?: ""
fun dur(sec: Int): String = if (sec <= 0) "" else "%d:%02d".format(sec / 60, sec % 60)

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { toast(ctx, "No app can open this link") }
}

fun share(ctx: Context, text: String) {
    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

val urlRegex = Regex("""https?://[^\s<>"']+""")

@Composable
fun ctx(): Context = LocalContext.current
