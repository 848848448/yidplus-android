package com.yidplus.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import com.yidplus.app.data.Room
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* ═══════════ Voice notes like WhatsApp and the site's voice-recording.js — helper CS 2.1 ═══════════
 * Touch: hold the mic = record, release = send, slide left = cancel, slide up = lock.
 * Tap the mic (or OK on the keypad) = a locked recording right away: Pause/Resume, Stop to listen first, Delete, Send. */

class CsRec(ctx: Context) {
    val rec = ChatVoiceRecorder(ctx)
    /** 0 idle · 1 holding · 2 locked · 3 stopped, listening before sending */
    var mode by mutableStateOf(0)
    var sec by mutableStateOf(0)
    var paused by mutableStateOf(false)
    var cancelArmed by mutableStateOf(false)
    var lockNear by mutableStateOf(false)
    var level by mutableStateOf<List<Int>>(emptyList())
    var take by mutableStateOf<Pair<java.io.File, String>?>(null)
    var startedAt = 0L
    fun start(locked: Boolean): Boolean { if (!rec.start()) return false; mode = if (locked) 2 else 1; sec = 0; paused = false; cancelArmed = false; lockNear = false; startedAt = System.currentTimeMillis(); ChatVoicePlayer.stop(); return true }
    fun tick() { rec.sample(); sec = rec.seconds(); level = rec.last(28) }
    fun pauseResume() { if (paused) { if (rec.resume()) paused = false } else if (rec.pause()) paused = true }
    /** Stop and keep the file to listen to (WhatsApp's stop button after lock). */
    fun stopToListen() { if (paused) rec.resume(); take = rec.stop(true); mode = if (take != null) 3 else 0; paused = false }
    /** Ends everything; returns the take to send, or null (cancelled / too short). */
    fun finish(send: Boolean): Pair<java.io.File, String>? {
        val t = when (mode) { 3 -> take; 1, 2 -> { if (paused) rec.resume(); rec.stop(send) }; else -> null }
        if (!send) t?.first?.delete()
        if (ChatVoicePlayer.playing == "cs-preview") ChatVoicePlayer.stop()
        mode = 0; take = null; paused = false; cancelArmed = false; lockNear = false
        return if (send) t else null
    }
}

/** The round button at the end of the composer: Send when there is text; otherwise the mic with hold / slide / lock (touch) and tap / OK (locked). */
@Composable
internal fun CsMicButton(size: androidx.compose.ui.unit.Dp = 48.dp, canSend: Boolean, editing: Boolean, busy: Boolean, r: CsRec, onSend: () -> Unit, onStart: (locked: Boolean) -> Unit, onRelease: () -> Unit, onCancel: () -> Unit) {
    val d = LocalDensity.current; val hap = LocalHapticFeedback.current
    val cancelPx = with(d) { 90.dp.toPx() }; val lockPx = with(d) { 60.dp.toPx() }
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val send by rememberUpdatedState(canSend)
    val isBusy by rememberUpdatedState(busy)
    val doSend by rememberUpdatedState(onSend); val doStart by rememberUpdatedState(onStart)
    val doRelease by rememberUpdatedState(onRelease); val doCancel by rememberUpdatedState(onCancel)
    Box(Modifier.size(size).clip(CircleShape).background(if (r.mode == 1) Color(0xFFE53935) else ChatLook.bar)
        .then(if (focused) Modifier.border(2.dp, Color(0xFFFFD54F), CircleShape) else Modifier)
        .onKeyEvent { e -> if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) { if (send) onSend() else if (!busy) onStart(true); true } else false }
        .focusable(interactionSource = src)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (send) { if (waitForUpOrCancellation() != null) doSend(); return@awaitEachGesture }
                if (isBusy) return@awaitEachGesture
                val up = withTimeoutOrNull(260) { waitForUpOrCancellation() }
                if (up != null) { doStart(true); return@awaitEachGesture } // a tap: locked recording (keypad-friendly)
                doStart(false); hap.performHapticFeedback(HapticFeedbackType.LongPress)
                var done = false
                while (!done) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                    val dx = ch.position.x - down.position.x; val dy = ch.position.y - down.position.y
                    if (r.mode != 1) { done = true; break }
                    r.cancelArmed = dx < -cancelPx
                    r.lockNear = dy < -lockPx / 2
                    if (dy < -lockPx) { r.mode = 2; hap.performHapticFeedback(HapticFeedbackType.LongPress); done = true; break }
                    if (!ch.pressed) { done = true; if (r.cancelArmed) doCancel() else doRelease() }
                }
            }
        }, contentAlignment = Alignment.Center) {
        if (busy && !canSend) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(if (canSend) (if (editing) Sym.Check else Sym.Send) else Sym.Mic,
            if (canSend) "Send" else "Record voice message (hold, or tap for hands-free)", tint = Color.White)
    }
}

/** The live level bars (the site's rec-waveform). */
@Composable
internal fun CsMeter(levels: List<Int>, modifier: Modifier = Modifier, color: Color = Color(0xFFE53935)) {
    Row(modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        levels.forEach { v -> Box(Modifier.weight(1f).height((3 + v * 24 / 100).coerceIn(3, 27).dp).clip(RoundedCornerShape(2.dp)).background(color)) }
    }
}

/** While holding: ● 0:07 [meter] ‹ Slide left to cancel — and a lock hint above. */
@Composable
internal fun CsHoldBar(r: CsRec) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            Column(Modifier.padding(end = 14.dp).clip(RoundedCornerShape(16.dp)).background(if (r.lockNear) ChatLook.bar else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym.Lock, null, Modifier.size(18.dp), tint = if (r.lockNear) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Slide up to lock", fontSize = 11.sp, color = if (r.lockNear) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.cancelArmed) Sym.Delete else Sym.Mic, null, tint = Color(0xFFE53935))
            Text("  %d:%02d  ".format(r.sec / 60, r.sec % 60), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            CsMeter(r.level, Modifier.weight(1f))
            Text(if (r.cancelArmed) "  Release to cancel" else "  ‹ Slide left to cancel", fontSize = 13.sp, color = if (r.cancelArmed) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Locked (hands-free) recording and the listen-first step: Delete · time + meter (or the take's player) · Pause/Resume or Stop · Send. */
@Composable
internal fun CsLockedBar(r: CsRec, onDelete: () -> Unit, onSend: () -> Unit) {
    val c = ctx()
    // The mic that had keypad focus is replaced by this bar, which dropped focus (fx5 run 43511759626: focus [none] right after the start). Put it on Send so OK sends and the D-pad reaches Delete/Pause/Stop.
    val sendFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); try { sendFocus.requestFocus() } catch (_: Exception) {} }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 6.dp)) {
        if (r.mode == 2 && r.paused) Text("Paused. Stop to listen before sending.", Modifier.padding(start = 12.dp, bottom = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChIcon(Sym.Delete, "Delete recording", onDelete, tint = Color(0xFFD32F2F))
            if (r.mode == 3) {
                val t = r.take
                val playing = ChatVoicePlayer.playing == "cs-preview" && !ChatVoicePlayer.paused
                LaunchedEffect(ChatVoicePlayer.playing) { while (ChatVoicePlayer.playing == "cs-preview") { ChatVoicePlayer.tick(); kotlinx.coroutines.delay(150) } }
                ChIcon(if (playing) Sym.Pause else Sym.PlayArrow, if (playing) "Pause" else "Listen", { t?.let { ChatVoicePlayer.toggle(c, "cs-preview", Uri.fromFile(it.first).toString()) } }, tint = ChatLook.bar)
                val peaks = remember(t) { t?.second?.substringAfter('|', "")?.split(',')?.mapNotNull { it.toIntOrNull() } ?: emptyList() }
                val done = if (ChatVoicePlayer.playing == "cs-preview") (ChatVoicePlayer.progress * peaks.size).toInt() else 0
                Row(Modifier.weight(1f).height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    peaks.forEachIndexed { i, v -> Box(Modifier.weight(1f).height((3 + v * 24 / 100).coerceIn(3, 27).dp).clip(RoundedCornerShape(2.dp)).background(if (i < done) ChatLook.bar else ChatLook.sub().copy(alpha = .45f))) }
                }
                Text("  " + (t?.second?.substringBefore('|') ?: ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.paused) Color.Gray else Color(0xFFE53935)))
                Text("  %d:%02d  ".format(r.sec / 60, r.sec % 60), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                CsMeter(r.level, Modifier.weight(1f), if (r.paused) Color.Gray else Color(0xFFE53935))
                if (android.os.Build.VERSION.SDK_INT >= 24) ChIcon(if (r.paused) Sym.CircleFill else Sym.Pause, if (r.paused) "Resume recording" else "Pause recording", { r.pauseResume() }, tint = Color(0xFFE53935))
                ChIcon(Sym.Stop, "Stop and listen", { r.stopToListen() }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(ChatLook.bar).focusRequester(sendFocus).tap(CircleShape) { onSend() }, contentAlignment = Alignment.Center) {
                Icon(Sym.Send, "Send voice message", tint = Color.White)
            }
        }
    }
}


/** A voice note or a music file in a bubble: play/pause, tap or drag on the waveform to seek, 1x/1.5x/2x, live time; music gets a 🎵 card with its title. */
@Composable
internal fun CsVoiceBubble(m: ChatMsg) {
    val c = ctx()
    val me = ChatVoicePlayer.playing == m.id
    LaunchedEffect(me) { while (me) { ChatVoicePlayer.tick(); kotlinx.coroutines.delay(150) } }
    val peaks = remember(m.text) { m.text.substringAfter('|', "").split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { List(32) { i -> 20 + (i * 37 % 60) } } }
    val music = !m.isVoice
    Row(Modifier.padding(4.dp).width(250.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(if (music) RoundedCornerShape(10.dp) else CircleShape).background(ChatLook.bar).tap(CircleShape) { (m.media ?: m.local)?.let { ChatVoicePlayer.toggle(c, m.id, it) } ?: toast(c, "Audio not available") }, contentAlignment = Alignment.Center) {
            Icon(if (me && !ChatVoicePlayer.paused) Sym.Pause else Sym.PlayArrow, if (me && !ChatVoicePlayer.paused) "Pause" else "Play", tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            if (music) Text("🎵 " + m.fileName.substringBeforeLast('.').ifBlank { "Audio" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ChatLook.ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            var w by remember { mutableStateOf(1) }
            val seekMod = Modifier.onSizeChanged { w = it.width.coerceAtLeast(1) }.pointerInput(m.id) {
                detectTapGestures { o -> (m.media ?: m.local)?.let { ChatVoicePlayer.seek(c, m.id, it, o.x / w) } }
            }
            if (music) {
                Box(seekMod.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
                    LinearProgressIndicator(progress = { if (me) ChatVoicePlayer.progress else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = ChatLook.bar, trackColor = ChatLook.sub().copy(alpha = .25f))
                }
            } else Row(seekMod.height(26.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val n = peaks.size.coerceAtMost(32); val done = if (me) (ChatVoicePlayer.progress * n).toInt() else 0
                for (i in 0 until n) Box(Modifier.weight(1f).height((4 + peaks[i * peaks.size / n] * 22 / 100).coerceIn(3, 26).dp).clip(RoundedCornerShape(2.dp)).background(if (i < done) ChatLook.bar else ChatLook.sub().copy(alpha = .45f)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pos = ChatVoicePlayer.position
                Text(if (me) "%d:%02d".format(pos / 60, pos % 60) else if (m.isVoice) m.voiceDur.ifBlank { "Voice message" } else m.fileName.substringAfterLast('.', "audio").uppercase().take(5),
                    Modifier.weight(1f), fontSize = 12.sp, color = ChatLook.sub(), maxLines = 1)
                if (me) Text(ChatVoicePlayer.speedLabel(), Modifier.clip(RoundedCornerShape(10.dp)).background(ChatLook.bar.copy(alpha = .15f)).tap(RoundedCornerShape(10.dp)) { ChatVoicePlayer.cycleSpeed() }
                    .padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ChatLook.bar)
            }
        }
        if (m.isVoice && !me) Icon(Sym.Mic, null, Modifier.padding(start = 4.dp).size(18.dp), tint = ChatLook.bar)
    }
}

/** Tap a name in a group: who it is, with Message (open a private chat) and Mention. */
@Composable
internal fun CsPersonCard(m: ChatMsg, onMention: () -> Unit, onClose: () -> Unit) {
    val c = ctx(); val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onClose, text = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ChAvatar(m.photo, m.nick, m.senderId, 72.dp)
            Text(m.nick, Modifier.padding(top = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.nameColor(m.senderId))
            if (m.title.isNotBlank()) Text(m.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        if (Session.me != null && m.senderId != Session.me?.id) TextButton({
            onClose(); scope.launch { runCatching { ChatApi.openDm(m.senderId) }.onSuccess { id -> if (id.isNotBlank()) Nav.open(Screen.Chat(ChatApi.roomOf(id, "private", m.nick))) }.onFailure { toast(c, Api.friendly(it)) } }
        }, Modifier.firstFocus(always = true)) { Text("Message") }
    }, dismissButton = {
        Row { if (Session.me != null) TextButton({ onClose(); onMention() }) { Text("Mention") }; TextButton(onClose) { Text("Close") } }
    })
}
