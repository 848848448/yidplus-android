package com.yidplus.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yidplus.app.data.Clip
import kotlinx.coroutines.launch

// Shorts 2.1 (helper SH): the creators list, the options sheet, end-of-feed pages, the heart burst, sharing.

/** A creator's picture: the photo (only while it loads fine), else the first letter on its colour. */
@Composable
fun ShAvatar(url: String?, name: String, size: Dp) {
    val n = name.replace(Regex("^[^\\p{L}\\p{N}]+"), "")
    var ok by remember(url) { mutableStateOf(!url.isNullOrBlank() && !ShAvatars.bad(url)) }
    Box(Modifier.size(size).clip(CircleShape).background(avatarColor(name.ifBlank { "Y" })), contentAlignment = Alignment.Center) {
        Text((n.take(1).ifBlank { "•" }).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.44f).sp, maxLines = 1)
        if (ok) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            onError = { url?.let { ShAvatars.mark(it) }; ok = false })
    }
}

/** A small pill on the dark video (chips row): filled when [on]. */
@Composable
fun ShChip(text: String, on: Boolean, onClick: () -> Unit, icon: ImageVector? = null, trailing: ImageVector? = null, lead: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.height(32.dp).tap(shape, onClick = onClick)
        .background(if (on) Color.White else Color.Black.copy(alpha = .38f), shape)
        .border(1.dp, Color.White.copy(alpha = if (on) 1f else .35f), shape)
        .padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        val fg = if (on) Color.Black else Color.White
        if (lead != null) { lead(); Spacer(Modifier.width(6.dp)) }
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Content), modifier = Modifier.widthIn(max = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400) 120.dp else 180.dp))
        if (trailing != null) { Spacer(Modifier.width(4.dp)); Icon(trailing, null, tint = fg, modifier = Modifier.size(15.dp)) }
    }
}

/**
 * Every creator, to follow right here (site: Following → the list, "+ Follow more", the caught-up screen).
 * Search, "▶ Videos" (only their videos), "+ Follow" / "Following". Follows stay on this phone.
 */
@Composable
fun ShCreatorList(title: String?, inline: Boolean, onWho: (List<String>, String) -> Unit, onSeeFollowed: () -> Unit, onBackToForYou: () -> Unit, modifier: Modifier = Modifier) {
    val c = ctx()
    LaunchedEffect(Unit) { ShDir.loadDir() }
    var q by remember { mutableStateOf(ShCreatorsState.q) }
    val dir = ShDir.dir
    var tick by remember { mutableIntStateOf(0) }
    val all = remember(dir, Feeds.hidden.size, ShDir.known.size) { ShDir.groups() }
    val nq = ShDir.norm(q)
    val view = remember(all, nq) { if (nq.isBlank()) all else all.filter { it.q.contains(nq) } }
    val state = rememberLazyListState(ShCreatorsState.first, ShCreatorsState.offset)
    DisposableEffect(Unit) { onDispose { ShCreatorsState.q = q; ShCreatorsState.first = state.firstVisibleItemIndex; ShCreatorsState.offset = state.firstVisibleItemScrollOffset } }
    val n = Feeds.follows.size
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val small = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 560
        val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
        if (title != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = if (small) 4.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
            if (inline) TextButton({ onBackToForYou() }) { Text("For You ›", fontWeight = FontWeight.SemiBold) }
        }
        if (all.size > 12 || dir == null) {
            // the same look as the shared SearchPill (WhatsApp 2024), but you type right here
            val shape = RoundedCornerShape(50)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (small) 4.dp else 6.dp).height(if (small) 40.dp else 44.dp).clip(shape)
                .background(if (isDark()) Color(0xFF202C33) else Color(0xFFF0F2F5), shape)
                .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text(if (all.size > 12) "Search " + "%,d".format(all.size) + " creators" else "Search creators", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search creators" }, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { }))
                }
                if (q.isNotEmpty()) IconBtn(Sym.Close, "Clear search", { q = "" }, size = 32.dp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                all.isEmpty() && (dir == null) -> SoSkeleton(rows = 7, avatar = 44.dp)
                view.isEmpty() -> SoEmpty(Sym.PersonSearch, if (q.isNotBlank()) "No creator called “$q”" else "No creators right now")
                else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(view, key = { it.keys.joinToString(",") }) { x ->
                        val fol = ShDir.isFollowed(x.keys).also { tick.let { } }
                        Row(Modifier.fillMaxWidth().heightIn(min = if (small) 54.dp else 60.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).tap(RoundedCornerShape(12.dp)) { onWho(x.keys, x.name) }.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                ShAvatar(x.avatar, x.name, if (small) 40.dp else 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(x.name, Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground, style = TextStyle(textDirection = TextDirection.Content))
                                if (x.verified) Icon(Sym.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            val vs = RoundedCornerShape(17.dp)
                            // narrow phones: ▶ alone (the row itself opens the videos too), so the name keeps its room
                            if (narrow) Box(Modifier.padding(start = 2.dp).size(30.dp).tap(CircleShape) { onWho(x.keys, x.name) }.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .semantics { contentDescription = "Videos" }, contentAlignment = Alignment.Center) {
                                Icon(Sym.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            } else Row(Modifier.padding(start = 4.dp).height(34.dp).tap(vs) { onWho(x.keys, x.name) }.border(1.dp, MaterialTheme.colorScheme.outline, vs).padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Sym.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Videos", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(6.dp))
                            SoPill(if (fol) "Following" else "+ Follow", !fol, {
                                ShDir.setFollow(c, x.keys, !fol, x.name, x.avatar, x.verified, quiet = fol); tick++
                                ShCreatorsState.changed = true
                            }, Modifier.widthIn(min = if (narrow) 80.dp else 96.dp))
                        }
                    }
                }
            }
        }
        if (inline && n > 0) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
            PrimaryButton("See their videos · $n followed", { onSeeFollowed() }, Modifier.fillMaxWidth())
        }
    }
}

/** Where the creators list was (search + scroll), for "‹ Back" from a creator's videos. */
object ShCreatorsState { var q = ""; var first = 0; var offset = 0; var changed = false }

/** "+ Follow more": the same list in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShCreatorsSheet(onDismiss: () -> Unit, onWho: (List<String>, String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Creators to follow", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconBtn(Sym.Close, "Close", onDismiss)
            }
            ShCreatorList(null, inline = false, onWho = onWho, onSeeFollowed = onDismiss, onBackToForYou = onDismiss)
        }
    }
}

/** The ⋯ options (site: menu()): speed, auto-scroll, not interested, hide creator, copy link, saved, reset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShOptionsSheet(s: Clip, speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit, onNotInterested: () -> Unit, onHide: () -> Unit,
                   onCopy: () -> Unit, onSaved: () -> Unit, onReset: () -> Unit, onDelete: (() -> Unit)?, onCreator: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(max = 520.dp).verticalScrollCompat()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Options", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconBtn(Sym.Close, "Close", onDismiss)
            }
            Text("Playback speed", Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (sp in ShPrefs.SPEEDS) {
                    val on = sp == speed; val sh = RoundedCornerShape(16.dp)
                    Box(Modifier.weight(1f).height(36.dp).tap(sh) { onSpeed(sp) }.background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, sh),
                        contentAlignment = Alignment.Center) {
                        Text(if (sp == 1f) "Normal" else (if (sp == sp.toInt().toFloat()) sp.toInt().toString() else sp.toString()) + "×", fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            SettingRow(Sym.ArrowUpward, "Auto-scroll", switch = ShPrefs.auto) { ShPrefs.changeAuto(!ShPrefs.auto) }
            if (s.fromSrc) SettingRow(Sym.Block, "Not interested") { onDismiss(); onNotInterested() }
            if (s.fromSrc) SettingRow(Sym.VisibilityOff, "Don’t show videos from " + s.creator.ifBlank { "this creator" }) { onDismiss(); onHide() }
            SettingRow(Sym.AccountCircle, s.creator.ifBlank { "This creator" } + "’s videos") { onDismiss(); onCreator() }
            SettingRow(Sym.Link, "Copy link") { onDismiss(); onCopy() }
            SettingRow(Sym.Bookmarks, "Saved videos (${Feeds.saved.size})") { onDismiss(); onSaved() }
            if (onDelete != null) SettingRow(Sym.Delete, "Delete my video", danger = true) { onDismiss(); onDelete() }
            if (s.fromSrc) SettingRow(Sym.Refresh, "Reset my For You") { onDismiss(); onReset() }
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

/** Shorts settings kept on the phone (site: yp_fy_pref): speed, auto-scroll, hints. */
object ShPrefs {
    val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
    var speed by mutableFloatStateOf(1f)
    var auto by mutableStateOf(false)
    var swipeHint by mutableStateOf(false)
    var likeHint = false
    private var p: android.content.SharedPreferences? = null
    fun init(c: Context) {
        if (p != null) return
        val sp = c.getSharedPreferences("shorts", Context.MODE_PRIVATE); p = sp
        speed = sp.getFloat("speed", 1f).takeIf { it in SPEEDS } ?: 1f; auto = sp.getBoolean("auto", false)
        swipeHint = sp.getBoolean("swipeHint", false); likeHint = sp.getBoolean("likeHint", false)
        ShStore.init(c)
    }
    fun changeSpeed(v: Float) { speed = v; p?.edit()?.putFloat("speed", v)?.apply() }
    fun changeAuto(v: Boolean) { auto = v; p?.edit()?.putBoolean("auto", v)?.apply() }
    fun hintDone() { swipeHint = true; p?.edit()?.putBoolean("swipeHint", true)?.apply() }
    fun likeHintDone() { likeHint = true; p?.edit()?.putBoolean("likeHint", true)?.apply() }
    fun lastTab(): String = p?.getString("tab", "") ?: ""
    fun setLastTab(t: String) { p?.edit()?.putString("tab", t)?.apply() }
}

/** The page after the last video (or instead of an empty feed): icon, one line, and the site's buttons. */
@Composable
fun ShEndPage(icon: ImageVector?, line: String, sub: String? = null, spinner: Boolean = false, buttons: List<Pair<String, () -> Unit>> = emptyList()) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (spinner) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        else if (icon != null) Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        if (line.isNotBlank()) Text(line, Modifier.padding(top = 14.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            style = TextStyle(textDirection = TextDirection.Content))
        if (!sub.isNullOrBlank()) Text(sub, Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, textAlign = TextAlign.Center)
        buttons.forEachIndexed { i, (label, go) ->
            Spacer(Modifier.height(if (i == 0) 18.dp else 8.dp))
            val sh = RoundedCornerShape(22.dp)
            Box(Modifier.widthIn(min = 200.dp).height(44.dp).then(if (i == 0) Modifier.firstFocus() else Modifier).tap(sh, onClick = go)
                .then(if (i == 0) Modifier.background(Green, sh) else Modifier.border(1.dp, Color.White.copy(alpha = .6f), sh)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

/** The double-tap heart (site: burst): grows, tilts, floats up and fades, where the finger was. */
@Composable
fun ShBurst(x: Float, y: Float, rot: Float, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900)); onDone() }
    val t = a.value
    val sc = if (t < 0.25f) 0.4f + t / 0.25f * 0.9f else 1.3f - (t - 0.25f) * 0.3f
    Box(Modifier.offset { IntOffset((x - 48.dp.toPx()).toInt(), (y - 48.dp.toPx() - t * 60.dp.toPx()).toInt()) }.size(96.dp).scale(sc).rotate(rot).alpha(if (t < 0.7f) 1f else (1f - t) / 0.3f)) {
        Icon(Sym.FavoriteFill, null, tint = Color(0xFFFF3B5C), modifier = Modifier.fillMaxSize())
    }
}

/** Share like the site: "Name · YID PLUS", the caption (120), and the link. */
fun shShare(c: Context, s: Clip) {
    val title = s.creator.ifBlank { "Video" } + " · YID PLUS"
    val text = (if (s.fromSrc) s.caption.take(120).ifBlank { "Watch on YID PLUS" } else "Check out this short!") + "\n" + s.shareUrl
    runCatching {
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, text), "Share")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { soCopy(c, s.shareUrl) }
}

/** Blocked from Shorts by an admin (site: FeatureBlock.renderBlockedScreen): until when, the email, and a message to the admins. */
@Composable
fun ShBlocked(until: String?) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Sym.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
        Text("You are currently blocked from Shorts", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        val u = until?.let { parseTime(it) }?.let { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(it)) }
        Text("You will not be able to view or use Shorts " + (if (u != null) "until $u." else "until further notice."), Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text("Please contact yidplusmedia@gmail.com if you believe this is a mistake.", Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 80.dp), placeholder = { Text("Or send a message to the admins here...") },
            textStyle = TextStyle(textDirection = TextDirection.Content))
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Send Message to Admins", {
            val t = text.trim(); if (t.isEmpty()) { toast(c, "Write a message first."); return@PrimaryButton }
            busy = true
            scope.launch {
                runCatching { com.yidplus.app.data.Api.post("/api/feedback", org.json.JSONObject().put("type", "block_appeal").put("text", "Blocked from shorts: $t")) }
                    .onSuccess { toast(c, "Sent to the admins."); text = "" }.onFailure { toast(c, com.yidplus.app.data.Api.friendly(it)) }
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = !busy)
    }
}


/**
 * Comments over the bottom 3/4 of the video (TikTok / Reels; site: the comments sheet): the video stays in sight above,
 * a tap there or Back closes it, a drag down on the handle closes it. The box to write always stays above the keyboard
 * (the panel shrinks into the space the keyboard leaves, so nothing hides at 480x640).
 */
@Composable
fun ShCommentsPanel(title: String, onDismiss: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    // A window of its own over the whole screen (also over the app's bottom bar), so at 480x640 the list gets the room;
    // the video still shows above it, dimmed. Back, a tap above, or dragging the handle down closes it.
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var dragY by remember { mutableFloatStateOf(0f) }
        val ime = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)).pointerInputTap(onDismiss))
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(if (ime) 1f else 0.72f).navigationBarsPadding()
                .offset { IntOffset(0, dragY.coerceAtLeast(0f).toInt()) },
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { if (dragY > 120.dp.toPx()) onDismiss() else dragY = 0f }, onDragCancel = { dragY = 0f }) { _, d -> dragY += d }
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 8.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant))
                        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground)
                            IconBtn(Sym.Close, "Close comments", onDismiss, modifier = Modifier.firstFocus())
                        }
                    }
                    body()
                }
            }
        }
    }
}

private fun Modifier.pointerInputTap(go: () -> Unit): Modifier = this.pointerInput(Unit) { detectTapGestures { go() } }
