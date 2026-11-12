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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/* ═══════════ Polls exactly like the site's (create with every setting, vote / revote / several answers, quiz, close, suggest) — helper CS 2.1 ═══════════ */

/** New poll: the site's poll modal — question, description, 2-12 options, and its 7 settings. */
data class CsPollNew(val room: Room) : PageScreen() {
    override val perfName get() = "NewPoll"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        var question by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        val opts = remember { mutableStateListOf("", "") }
        val correct = remember { mutableStateListOf<Int>() }
        var multi by remember { mutableStateOf(false) }
        var revote by remember { mutableStateOf(true) }
        var showWho by remember { mutableStateOf(false) }
        var addOpts by remember { mutableStateOf(false) }
        var shuffle by remember { mutableStateOf(false) }
        var quiz by remember { mutableStateOf(false) }
        var timed by remember { mutableStateOf(false) }
        var minutes by remember { mutableStateOf("60") }
        var busy by remember { mutableStateOf(false) }
        fun send() {
            val o = opts.map { it.trim() }.filter { it.isNotEmpty() }
            if (question.isBlank()) { toast(c, "⚠ Please enter a question."); return }
            if (o.size < 2) { toast(c, "⚠ At least 2 options are required."); return }
            // Correct answers are indexes into the options that are actually sent (blank rows dropped), like the site's POLL_correctIndices.
            val keep = opts.withIndex().filter { it.value.trim().isNotEmpty() }.map { it.index }
            val ci = JSONArray(); if (quiz) correct.mapNotNull { k -> keep.indexOf(k).takeIf { it >= 0 } }.sorted().forEach { ci.put(it) }
            val b = JSONObject().put("room_id", room.id).put("question", question.trim()).put("options", JSONArray(o))
                .put("show_who_voted", showWho).put("allow_multiple", multi).put("allow_add_options", addOpts).put("allow_revote", revote)
                .put("shuffle_options", shuffle).put("quiz_mode", quiz).put("duration_minutes", if (timed) (minutes.toIntOrNull()?.coerceAtLeast(1) ?: 60) else JSONObject.NULL).put("correct_indices", ci)
            if (desc.isNotBlank()) b.put("description", desc.trim())
            busy = true; toast(c, "📊 Creating poll...")
            scope.launch {
                runCatching { Api.post("/api/polls", b) }.onSuccess { toast(c, "✅ Poll created!"); Nav.back() }.onFailure { toast(c, Api.friendly(it)) }
                busy = false
            }
        }
        Column(Modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.surface)) {
            ChBar("New poll", sub = room.name, onBack = { Nav.back() }) { ChIcon(Sym.Send, "Create Poll", { send() }) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                val ts = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content)
                OutlinedTextField(question, { if (it.length <= 200) question = it }, Modifier.fillMaxWidth().firstFocus(), label = { Text("Ask a question") }, textStyle = ts)
                OutlinedTextField(desc, { if (it.length <= 300) desc = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Description (optional)") }, textStyle = ts)
                Text("Options", Modifier.padding(top = 14.dp, bottom = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.bar)
                opts.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (quiz) Box(Modifier.size(36.dp).clip(CircleShape).background(if (i in correct) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant)
                            .tap(CircleShape) { if (i in correct) correct.remove(i) else { if (!multi) correct.clear(); correct.add(i) } }, contentAlignment = Alignment.Center) {
                            Icon(Sym.Check, "Mark as correct answer", tint = if (i in correct) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        OutlinedTextField(s, { if (it.length <= 100) opts[i] = it }, Modifier.weight(1f).padding(start = if (quiz) 6.dp else 0.dp, top = 6.dp), singleLine = true, label = { Text("Option ${i + 1}") }, textStyle = ts)
                        if (i >= 2) ChIcon(Sym.Close, "Remove", { opts.removeAt(i); correct.remove(i); val sh = correct.map { if (it > i) it - 1 else it }; correct.clear(); correct.addAll(sh) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (opts.size < 12) TextButton({ opts.add("") }) { Icon(Sym.Add, null); Text(" Add Option") }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                @Composable fun sw(label: String, sub: String?, v: Boolean, set: (Boolean) -> Unit) =
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).tap { set(!v) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(label, fontSize = 16.sp); if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(v, null)
                    }
                sw("Allow Multiple Answers", null, multi) { multi = it }
                sw("Allow Revoting", null, revote) { revote = it }
                sw("Show Who Voted", null, showWho) { showWho = it }
                sw("Let People Add Options", null, addOpts) { addOpts = it }
                sw("Shuffle Options", null, shuffle) { shuffle = it }
                sw("Quiz Mode", "Mark the correct answer(s)", quiz) { quiz = it; if (!it) correct.clear() }
                sw("Set a Time Limit", null, timed) { timed = it }
                if (timed) OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                PrimaryButton("Create Poll", { send() }, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp), enabled = !busy)
            }
        }
    }
}

/** A poll message (the site's _buildPollHTML): POLL · QUIZ, question, description, Anonymous/Public line, options (% only after voting or when closed),
 *  ✓/✗ for quiz, who voted, several answers, revote rule, "N voters · Closes in 2h", Close Poll (creator), + Suggest an option. */
@Composable
internal fun CsPollBubble(id: String) {
    val c = ctx(); val scope = rememberCoroutineScope()
    var poll by remember(id) { mutableStateOf<JSONObject?>(CsPolls.cache[id]) }
    var suggest by remember { mutableStateOf(false) }
    suspend fun load() { runCatching { withContext(Dispatchers.IO) { Api.get("/api/polls?id=" + chEnc(id)).optJSONObject("poll") } }.getOrNull()?.let { poll = it; CsPolls.cache[id] = it } }
    LaunchedEffect(id) { load() }
    val p = poll
    Column(Modifier.padding(6.dp).widthIn(min = 230.dp, max = 300.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Poll, null, Modifier.size(16.dp), tint = ChatLook.bar)
            Text(" POLL" + if (p?.optBoolean("quiz_mode") == true) " · QUIZ" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ChatLook.bar)
        }
        if (p == null) { Text("Loading poll…", fontSize = 14.sp, color = ChatLook.sub()); return@Column }
        Text(p.str("question"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ChatLook.ink(), style = TextStyle(textDirection = TextDirection.Content))
        if (p.str("description").isNotBlank()) Text(p.str("description"), fontSize = 14.sp, color = ChatLook.sub(), style = TextStyle(textDirection = TextDirection.Content))
        Text(if (p.optBoolean("show_who_voted")) "👁 Public poll" else "🔒 Anonymous poll", fontSize = 12.sp, color = ChatLook.sub())
        val closed = p.optBoolean("closed")
        val multi = p.optBoolean("allow_multiple"); val revote = p.optBoolean("allow_revote", true); val quiz = p.optBoolean("quiz_mode")
        val opts = chObjs(p.optJSONArray("options"))
        val voted = opts.any { it.optBoolean("my_vote") }
        opts.forEach { o ->
            val pct = o.optInt("pct"); val my = o.optBoolean("my_vote")
            val hasC = quiz && o.has("is_correct") && !o.isNull("is_correct"); val ok = o.optBoolean("is_correct")
            val clickable = !closed && Session.me != null && !(!revote && voted && !my)
            val tint = when { hasC && ok -> Color(0xFF2E7D32); hasC && my && !ok -> Color(0xFFD32F2F); else -> ChatLook.bar }
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).tap(RoundedCornerShape(8.dp), enabled = clickable) {
                // The site's castPollVote: several answers toggle this option in my set; one answer replaces it.
                val mine = opts.filter { it.optBoolean("my_vote") }.map { it.opt("id") }
                val next: List<Any?> = if (multi) (if (my) mine.filter { it != o.opt("id") } else mine + o.opt("id")) else listOf(o.opt("id"))
                scope.launch { runCatching { ChatApi.pollVote(id, next) }.onFailure { toast(c, Api.friendly(it)) }; load() }
            }.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (my) Sym.CheckCircle else if (multi) Sym.CheckBoxOutlineBlank else Sym.RadioButtonUnchecked, null, Modifier.size(18.dp), tint = if (my) tint else ChatLook.sub())
                    Text("  " + o.str("text"), Modifier.weight(1f), fontSize = 15.sp, color = ChatLook.ink(), style = TextStyle(textDirection = TextDirection.Content))
                    if (hasC) Text(if (ok) "✓ " else if (my) "✗ " else "", color = tint, fontWeight = FontWeight.Bold)
                    if (voted || closed) Text("$pct%", fontSize = 13.sp, color = ChatLook.sub())
                }
                if (voted || closed) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp)), color = tint, trackColor = ChatLook.sub().copy(alpha = .15f))
                val who = o.optJSONArray("voters")
                if (p.optBoolean("show_who_voted") && who != null && who.length() > 0)
                    Text("👁 " + (0 until minOf(3, who.length())).joinToString(", ") { k -> who.optString(k) } + if (who.length() > 3) "..." else "", fontSize = 12.sp, color = ChatLook.sub())
            }
        }
        if (!closed && p.optBoolean("allow_add_options") && Session.me != null) TextButton({ suggest = true }) { Text("+ Suggest an option") }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = p.optInt("total_voters")
            val closes = if (closed) " · Closed" else p.strOrNull("closes_at")?.let { at -> val ms = (parseTime(at) ?: 0L) - System.currentTimeMillis(); if (ms > 0) { val m = ms / 60000; when { m >= 2880 -> " · Closes in ${m / 1440}d"; m >= 60 -> " · Closes in ${m / 60}h"; else -> " · Closes in ${m}m" } } else "" } ?: ""
            Text("$n voter" + (if (n != 1) "s" else "") + closes, Modifier.weight(1f), fontSize = 12.sp, color = ChatLook.sub())
            if (!closed && p.str("creator_id") == Session.me?.id && Session.me != null)
                Text("Close Poll", Modifier.clip(RoundedCornerShape(8.dp)).tap(RoundedCornerShape(8.dp)) { scope.launch { runCatching { ChatApi.pollClose(id) }.onFailure { toast(c, Api.friendly(it)) }; load() } }.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD32F2F))
        }
    }
    if (suggest) {
        var t by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { suggest = false }, title = { Text("Suggest an option") }, text = { OutlinedTextField(t, { if (it.length <= 100) t = it }, singleLine = true, modifier = Modifier.firstFocus()) },
            confirmButton = { TextButton({ suggest = false; if (t.isNotBlank()) scope.launch { runCatching { ChatApi.pollAdd(id, t.trim()) }.onFailure { toast(c, Api.friendly(it)) }; load() } }) { Text("Add") } },
            dismissButton = { TextButton({ suggest = false }) { Text("Cancel") } })
    }
}

internal object CsPolls { val cache = java.util.concurrent.ConcurrentHashMap<String, JSONObject>() }
