package com.yidplus.app.ui

import kotlinx.coroutines.launch

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.graphicsLayer
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
        .then(if (focused && Keys.keypad) Modifier.background(Gold.copy(alpha = 0.18f)).border(if (LookPrefs.highContrast) 3.dp else 2.dp, Gold, shape) else Modifier)
        .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = role, onClick = onClick)
}

/** The row that opened a screen gets the keypad focus back when you come Back to the list. */
fun Modifier.restoreFocus(key: String): Modifier = composed {
    if (Nav.lastOpened != key) return@composed this
    val fr = remember { FocusRequester() }
    val back = Nav.stack.isEmpty()
    LaunchedEffect(back) { if (back && Keys.keypad) { for (i in 0 until 8) { delay(80); if (runCatching { fr.requestFocus() }.isSuccess) break } } }
    this.focusRequester(fr)
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
    // The brand bar (WhatsApp/Telegram style): green, white 20 sp title, white icons. Everything placed in it
    // (the back arrow, `lead`, `actions`) takes white as its content colour.
    val ink = barContent()
    Surface(color = topBarColor(), contentColor = ink, shadowElevation = if (LookPrefs.lightBar && !isDark()) 1.dp else 3.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconBtn(Sym.ArrowBack, "Back", onBack, size = 48.dp) else Spacer(Modifier.width(12.dp))
            if (lead != null) Box(Modifier.padding(start = 2.dp, end = 4.dp)) { lead() }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = barTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = ink.copy(alpha = .82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = LocalContentColor.current, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).tap(CircleShape, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("YID", fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        Text(" PLUS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Gold)
    }
}

/** Calm, distinct colours for people without a photo (picked from the name, so a person keeps the same colour). */
private val avatarPalette = listOf(
    Color(0xFF1F6F5C), Color(0xFF3A86D4), Color(0xFF8A68D2), Color(0xFFDB6A55), Color(0xFFE0913A),
    Color(0xFF45A35F), Color(0xFF239FAE), Color(0xFFCF5C91), Color(0xFF5A76C0), Color(0xFF7E8F33),
)
/** The same colour for a name however it is written ("'ישיבה" and "ישיבה" match): only its letters and digits count. */
fun avatarColor(name: String): Color {
    val key = name.filter { it.isLetterOrDigit() }.lowercase().ifEmpty { name.trim().lowercase() }
    return avatarPalette[Math.floorMod(key.hashCode(), avatarPalette.size)]
}

/** The first real letter (or digit) of a word: skips ' " ״ ׳ - . @ # _ and emoji. Hebrew/Yiddish letters are letters. */
fun firstLetterOf(w: String): String? {
    var i = 0
    while (i < w.length) {
        val cp = w.codePointAt(i)
        if (Character.isLetterOrDigit(cp)) return String(Character.toChars(cp)).uppercase()
        i += Character.charCount(cp)
    }
    return null
}

/** "Moshe Klein" -> "MK", "yidplus" -> "Y", "משה קליין" -> "מ", "'ישיבה" -> "יש" (a lone yod looks like an apostrophe). Never punctuation or an emoji. */
fun nameInitials(name: String): String {
    val letters = name.trim().split(Regex("\\s+")).mapNotNull { firstLetterOf(it) }
    if (letters.isEmpty()) return "Y"
    val a = letters[0]
    // A lone yod / vav (or the Yiddish ײ, which looks like ״) reads like an apostrophe in a circle: show the word's first two letters (יש, ײד).
    if (a == "י" || a == "ו" || a == "ן" || a == "ײ" || a == "ױ" || a == "װ") {
        val w = name.trim().split(Regex("\\s+")).firstOrNull { firstLetterOf(it) != null }.orEmpty()
        val two = w.filter { it.isLetter() && (it.code in 0x05D0..0x05EA || it.code in 0x05F0..0x05F2) }.take(2)
        if (two.length == 2) return two
    }
    // Two letters only for Latin names of two words, like Telegram; a Hebrew/Yiddish name shows its first letter, like the site.
    if (letters.size >= 2 && a[0].code < 0x0590 && letters[1][0].code < 0x0590 && a[0].isLetter() && letters[1][0].isLetter()) return a + letters[1]
    return a
}

private val defaultPic = Regex("(default[-_]?(avatar|user|profile)|placeholder|no[-_]?avatar|/globe|anonymous)", RegexOption.IGNORE_CASE)

@Composable
fun Avatar(url: String?, name: String, size: Dp = 40.dp, emoji: String? = null) {
    // The photo first when there is one; under it (while it loads, or when it fails) a coloured circle with the first real letter.
    val photo = url?.takeIf { it.isNotBlank() && !defaultPic.containsMatchIn(it) }
    val label = emoji?.takeIf { it.isNotBlank() } ?: nameInitials(name)
    Box(Modifier.size(size).clip(CircleShape).background(avatarColor(name.ifBlank { "Y" })), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TextStyle(textDirection = TextDirection.Ltr),
            fontSize = (size.value * (if (label.length > 1 && emoji.isNullOrBlank()) 0.38f else 0.46f)).sp)
        if (photo != null) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** A slim one-line "sign in" bar for guests (40 dp, never a third of the screen): a lock, one line, and a Sign in link. */
@Composable
fun GuestBar(text: String = "Sign in to chat, post and like", modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp).background(MaterialTheme.colorScheme.primaryContainer).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Sym.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.heightIn(min = 36.dp).tap(RoundedCornerShape(18.dp)) { Nav.go(Tab.Profile) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text("Sign in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Unread count: a green pill with white numbers (grey when the chat is muted). */
@Composable
fun Badge(n: Int, muted: Boolean = false) {
    if (n <= 0) return
    Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(CircleShape).background(if (muted) MaterialTheme.colorScheme.outline else BadgeGreen)
        .padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Loading state, only for a first-ever load (screens show their cached copy and refresh quietly): a short skeleton of
 * rows shaped like the list that is coming, after a quarter second so a fast load never flashes. Never a spinner over
 * content; `skeleton = false` keeps a small spinner for a tiny box (and black Shorts).
 */
@Composable
fun Loading(modifier: Modifier = Modifier.fillMaxSize(), skeleton: Boolean? = null) {
    val list = skeleton ?: !(Nav.tab == Tab.Shorts && Nav.stack.isEmpty())
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(250); show = true }
    if (!show) { Box(modifier); return }
    if (list) SkeletonRows(modifier, rows = if (modifier == Modifier.fillMaxSize()) 6 else 2) else
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }
}

/** Placeholder rows shaped like the list that is coming (avatar + two lines), pulsing gently. */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier.fillMaxSize(), rows: Int = 8, avatar: Boolean = true) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "pulse")
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark()) 0.10f else 0.07f)
    Column(modifier.graphicsLayer { alpha = a }) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (avatar) { Box(Modifier.size(48.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(14.dp)) }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.55f else 0.42f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.85f else 0.7f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
        }
    }
}

/** A friendly empty state: an icon in a soft circle, one bold line, one quiet line, and an optional button. */
@Composable
fun EmptyNote(icon: ImageVector, title: String, sub: String? = null, modifier: Modifier = Modifier.fillMaxSize(), action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (!sub.isNullOrBlank()) Text(sub, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(16.dp)); PrimaryButton(action, onAction, Modifier.firstFocus()) }
    }
}

@Composable
fun Problem(message: String, offline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(if (offline) Sym.CloudOff else Sym.ErrorOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text(if (offline) "You're offline" else "Couldn't load this", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(if (offline) "Check your internet. This page loads by itself when you're back online." else plainProblem(message),
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        PrimaryButton("Try again", onRetry, Modifier.firstFocus())
    }
}

/** A server's own words made human: database, quota or stack text never reaches the screen. */
fun plainProblem(msg: String?): String {
    val m = msg?.trim().orEmpty()
    return when {
        m.isEmpty() -> "Something went wrong"
        m.contains("D1_ERROR", true) || m.contains("read limit", true) || m.contains("exceeded", true) || m.contains("rate limit", true) ->
            "yidplus.com isn't answering right now. Please try again in a little while."
        m.contains("SQLITE", true) || m.contains("Exception") || m.contains("stack", true) || m.contains("Error:") || m.length > 140 -> "yidplus.com had a problem. Please try again."
        else -> m
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary) {
    val fg = if (color == Gold) GreenDeep else if (color == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary else Color.White
    Box(modifier.heightIn(min = 48.dp).tap(RoundedCornerShape(24.dp), enabled = enabled, onClick = onClick).background(if (enabled) color else color.copy(alpha = .4f)).padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.heightIn(min = 48.dp).tap(RoundedCornerShape(24.dp), onClick = onClick).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp))
}

/** A thin list divider that starts after the avatar/icon column, like WhatsApp's. */
@Composable
fun RowDivider(start: Dp = 72.dp) {
    HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A settings / menu row: 24 dp icon, 16 sp title with one quiet line under it, and at the end a switch (when
 * `switch` is set), a value (e.g. "Dark") or nothing. 56 dp tall, 72 with a subtitle. Works by touch and by OK.
 */
@Composable
fun SettingRow(icon: ImageVector, title: String, sub: String? = null, switch: Boolean? = null, value: String? = null, danger: Boolean = false,
               modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth().heightIn(min = if (sub.isNullOrBlank()) 56.dp else 68.dp).tap(RoundedCornerShape(0.dp), role = if (switch != null) Role.Switch else Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 24.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        if (switch != null) Switch(switch, null, modifier = Modifier.padding(start = 10.dp),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary, checkedBorderColor = MaterialTheme.colorScheme.primary))
    }
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


/**
 * The app's floating action button (WhatsApp-2024 look): 56 dp rounded square in the brand colour, a white Material
 * Symbol, D-pad focusable with the same gold ring as everything else. Put it in a Box with Modifier.align(BottomEnd)
 * and give the list under it `contentPadding = PaddingValues(bottom = Dim.fab + 24.dp)` so no row hides behind it.
 */
@Composable
fun Fab(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, extended: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    androidx.compose.material3.Surface(
        modifier = modifier.padding(16.dp).heightIn(min = Dim.fab).then(if (extended) Modifier else Modifier.width(Dim.fab)).tap(shape, onClick = onClick)
            .semantics { contentDescription = label },
        shape = shape, color = if (isDark()) MaterialTheme.colorScheme.primary else topBarColor().takeIf { !LookPrefs.lightBar } ?: MaterialTheme.colorScheme.primary,
        contentColor = if (isDark()) MaterialTheme.colorScheme.onPrimary else Color.White, shadowElevation = 6.dp
    ) {
        Row(Modifier.heightIn(min = Dim.fab).padding(horizontal = if (extended) 16.dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(Dim.icon))
            if (extended) Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One confirm dialog for the whole app (the site's safe-dialogs.js): title, one line, a red action when it destroys something. */
@Composable
fun ConfirmDialog(title: String, text: String?, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false, dismiss: String = "Cancel") {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); onConfirm() }, modifier = Modifier.firstFocus()) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(dismiss) } }
    )
}

/** One row of an [ActionSheet]: a Material Symbol, the words, and red when it deletes or leaves. */
data class SheetAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The app's long-press / ⋮ menu as a bottom sheet (WhatsApp-on-Android style): 56 dp rows, icon + label, first row
 * focused for the D-pad, Back closes it. Use for chat-row, message and post menus so every area looks the same.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(actions: List<SheetAction>, onDismiss: () -> Unit, title: String? = null) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            if (!title.isNullOrBlank()) Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions.forEachIndexed { i, a ->
                val ink = if (a.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (i == 0) Modifier.firstFocus() else Modifier)
                    .tap(RoundedCornerShape(0.dp)) { onDismiss(); a.onClick() }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(a.icon, null, Modifier.size(Dim.icon), tint = if (a.destructive) ink else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(a.label, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge, color = ink)
                }
            }
        }
    }
}

/**
 * App-wide snackbar (WhatsApp's "Chat archived · UNDO"): `Snack.show("Chat archived", "Undo") { … }` from anywhere.
 * AppRoot draws it above the bottom bar. Plain info can stay a toast.
 */
object Snack {
    val host = androidx.compose.material3.SnackbarHostState()
    private val scope = kotlinx.coroutines.MainScope()
    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(text, actionLabel = action, withDismissAction = false,
                duration = if (action != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}
