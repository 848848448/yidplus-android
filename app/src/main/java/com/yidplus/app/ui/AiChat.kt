package com.yidplus.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Disk
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

/* ─────────────── YID PLUS AI: the site's pinned "Ask me anything" chat (chat.js openAIChat, /api/ai/chat) ─────────────── */

data class AiMsg(val role: String, val content: String)

/** One AI conversation per account, like the site: GET /api/ai/chat (history), POST {message} (ask), DELETE (clear). */
object AiChat {
    val messages = mutableStateListOf<AiMsg>()
    var sending by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var name by mutableStateOf("YID PLUS AI"); private set
    var welcome by mutableStateOf<String?>(null); private set
    var notConfigured by mutableStateOf(false); private set
    var disabled by mutableStateOf(false); private set
    var status by mutableStateOf("Ready to help"); private set
    /** The owner switched YID PLUS AI off for everyone (site setting ai_enabled = "false"): the pinned row disappears. */
    var siteOff by mutableStateOf(false); private set
    private var diskTried = false
    private var switchAt = 0L
    private const val KEY = "nw|ai-chat"
    private val IMG = Regex("\\[img:([^\\]]+)\\]")

    private fun diskKey() = Store.key(KEY)

    private fun apply(j: JSONObject) {
        val list = j.optJSONArray("messages").objects().mapNotNull { m ->
            val c = m.str("content"); if (c.isBlank()) null else AiMsg(if (m.str("role") == "user") "user" else "assistant", c)
        }
        messages.clear(); messages.addAll(list)
        notConfigured = j.has("configured") && !j.optBoolean("configured", true)
        disabled = j.has("enabled") && !j.optBoolean("enabled", true)
        j.strOrNull("name")?.let { name = it }
        welcome = j.strOrNull("welcome")
    }

    private suspend fun saveDisk() {
        val arr = JSONArray(); messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val j = JSONObject().put("ok", true).put("messages", arr).put("name", name).put("welcome", welcome ?: JSONObject.NULL)
            .put("configured", !notConfigured).put("enabled", !disabled)
        withContext(Dispatchers.IO) { Disk.write(diskKey(), j) }
    }

    /** The saved copy first (instant), then the site's copy quietly. */
    suspend fun load(network: Boolean = true) {
        if (!diskTried) {
            diskTried = true
            val d = withContext(Dispatchers.IO) { Disk.read(diskKey()) }
            if (d != null && !loaded && messages.isEmpty()) apply(d)
        }
        if (!network || sending || Session.me == null) return
        try {
            val j = Api.get("/api/ai/chat")
            if (!sending) { apply(j); loaded = true; withContext(Dispatchers.IO) { Disk.write(diskKey(), j) } }
        } catch (_: Exception) { /* keep what we have; the welcome shows */ }
    }

    /** Reads the owner's on/off switch for the pinned row (public /api/settings), at most every 10 minutes. */
    suspend fun refreshSwitch() {
        val now = SystemClock.elapsedRealtime()
        if (switchAt != 0L && now - switchAt < 600_000) return
        switchAt = now
        runCatching { siteOff = Api.get("/api/settings").optJSONObject("settings")?.optString("ai_enabled") == "false" }
    }

    fun looksLikeImage(t: String) = Regex("\\b(draw|picture|image|photo|sketch|paint)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
        Regex("בילד|צייכן|געמעל|מאל\\s+מיר").containsMatchIn(t)

    /** Same call as the site's _aiSend: POST /api/ai/chat {message}; the answer is res.reply (+ res.image). */
    suspend fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        messages.add(AiMsg("user", t))
        sending = true
        status = if (looksLikeImage(t)) "Creating an image… (one moment)" else "Typing…"
        try {
            val res = Api.post("/api/ai/chat", JSONObject().put("message", t))
            if (res.optBoolean("ok")) {
                var content = res.str("reply")
                res.strOrNull("image")?.let { content = (content + "\n[img:" + it + "]").trim() }
                if (content.isNotBlank()) messages.add(AiMsg("assistant", content))
            } else {
                messages.add(AiMsg("assistant", "⚠️ " + (res.strOrNull("message") ?: res.strOrNull("error") ?: "Something went wrong. Please try again.")))
            }
        } catch (e: Exception) {
            val b = (e as? Api.ApiError)?.body
            messages.add(AiMsg("assistant", "⚠️ " + (b?.strOrNull("message") ?: b?.strOrNull("error") ?: Api.friendly(e))))
        } finally {
            sending = false; status = "Ready to help"
            saveDisk()
        }
    }

    /** Same call as the site's clearAIChat: DELETE /api/ai/chat. */
    suspend fun clear(): Boolean = try {
        Api.delete("/api/ai/chat"); messages.clear(); saveDisk(); true
    } catch (_: Exception) { false }

    fun preview(): String {
        val m = messages.lastOrNull() ?: return "Ask me anything"
        val t = m.content.replace(IMG, "📷 Image").replace('\n', ' ').trim()
        return if (m.role == "user") "You: $t" else t
    }

    fun split(content: String): Pair<String, String?> {
        val im = IMG.find(content) ?: return content to null
        return content.replace(im.value, "").trim() to Api.abs(im.groupValues[1])
    }
}

/** The site's own little markdown: **bold**, *italic*, `code`. */
fun aiFormat(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val rx = Regex("\\*\\*([^*]+)\\*\\*|`([^`]+)`|(?<![*\\w])\\*([^*\\n]+)\\*(?!\\*)")
    var i = 0
    for (m in rx.findAll(s)) {
        append(s.substring(i, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(m.groupValues[2]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[3]) }
        }
        i = m.range.last + 1
    }
    if (i < s.length) append(s.substring(i))
}

/** The AI's round avatar (the site's green gradient with 🤖). */
@Composable
fun AiAvatar(size: Dp, square: Boolean = false) {
    Box(Modifier.size(size).clip(if (square) RoundedCornerShape(size * 0.3f) else CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFF2B8A73), Green))), contentAlignment = Alignment.Center) {
        Text("🤖", fontSize = (size.value * 0.48f).sp)
    }
}

/** The pinned row at the top of the chat list, styled like a chat row (site: _aiChatRow). Hidden when the owner turned AI off. */
@Composable
fun AiChatRow(onOpen: () -> Unit) {
    LaunchedEffect(Unit) { AiChat.load(network = false); AiChat.refreshSwitch() }
    if (AiChat.siteOff) return
    Column(Modifier.fillMaxWidth().background(Green.copy(alpha = if (isDark()) .14f else .06f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).tap(RoundedCornerShape(0.dp)) { onOpen() }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AiAvatar(52.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AiChat.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Green).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(3.dp))
                Text(AiChat.preview(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start))
            }
        }
        HorizontalDivider(Modifier.padding(start = 82.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    }
}

/** The AI chat screen: opened with Nav.open(AiChatPage()). [ask] sends one question as soon as it opens (QA / deep links). */
data class AiChatPage(val ask: String? = null) : PageScreen() {
    override val perfName: String get() = "AiChat"
    @Composable override fun Content() { AiChatScreen(ask) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiChatScreen(ask: String?) {
    val c = ctx()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        AiChat.load(network = false)
        Perf.shown("AiChat", AiChat.messages.isNotEmpty())
        AiChat.load()
        if (!ask.isNullOrBlank()) AiChat.send(ask)
    }
    LaunchedEffect(Unit) { delay(200); runCatching { fr.requestFocus() } }
    LaunchedEffect(AiChat.messages.size, AiChat.sending) { if (AiChat.messages.isNotEmpty()) runCatching { list.animateScrollToItem(0) } }
    fun doSend() {
        val t = input.trim()
        if (t.isEmpty() || AiChat.sending) return
        input = ""
        scope.launch { AiChat.send(t) }
    }
    val blocked = AiChat.disabled || AiChat.notConfigured
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        TopBar(AiChat.name, onBack = { Nav.back() }, sub = AiChat.status, lead = { AiAvatar(38.dp) }) {
            IconBtn(Icons.Filled.DeleteSweep, "Clear chat", { confirm = true })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val msgs = AiChat.messages
            if (msgs.isEmpty() && !AiChat.sending) AiWelcome(blocked) { q -> scope.launch { AiChat.send(q) } }
            else LazyColumn(Modifier.fillMaxSize(), state = list, reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (AiChat.sending) item(key = "typing") { AiBubble("assistant", "", typing = true) }
                items(msgs.size, key = { msgs.size - 1 - it }) { i -> val m = msgs[msgs.size - 1 - i]; AiBubble(m.role, m.content) }
            }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            TextField(value = input, onValueChange = { input = it }, enabled = !blocked,
                modifier = Modifier.weight(1f).focusRequester(fr).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && !e.isShiftPressed) { doSend(); true } else false
                },
                placeholder = { Text("Message YID PLUS AI", fontSize = 16.sp) },
                shape = RoundedCornerShape(24.dp), maxLines = 5,
                textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Content),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onSend = { doSend() }))
            Spacer(Modifier.width(6.dp))
            val ready = input.isNotBlank() && !AiChat.sending
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (ready) Green else Green.copy(alpha = .45f)).tap(CircleShape) { doSend() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Clear chat?") }, text = { Text("Clear the entire chat with YID PLUS AI?") },
        confirmButton = { TextButton({ confirm = false; scope.launch { toast(c, if (AiChat.clear()) "Cleared" else "Couldn't clear. Try again.") } }) {
            Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiWelcome(blocked: Boolean, onQuick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AiAvatar(68.dp, square = true)
        Spacer(Modifier.height(12.dp))
        Text(AiChat.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(AiChat.welcome ?: "Ask me anything: questions, writing, translation, ideas, explanations, and even making images 🎨.",
            fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            style = TextStyle(textDirection = TextDirection.Content), modifier = Modifier.widthIn(max = 340.dp))
        Spacer(Modifier.height(16.dp))
        when {
            AiChat.disabled -> AiNote("⏸️ YID PLUS AI is currently turned off.")
            AiChat.notConfigured -> AiNote("⚠️ YID PLUS AI is not set up yet.")
            Session.me == null -> AiNote("Sign in to chat with YID PLUS AI.")
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 380.dp)) {
                listOf("Make me an image 🎨", "Write me a message", "Translate to English", "Give me an idea").forEachIndexed { i, q ->
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = (if (i == 0) Modifier.firstFocus() else Modifier).tap(RoundedCornerShape(20.dp)) { if (!blocked) onQuick(q) }) {
                        Text(q, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiNote(t: String) {
    Text(t, Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp),
        fontSize = 14.sp, textAlign = TextAlign.Center)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiBubble(role: String, content: String, typing: Boolean = false) {
    val c = ctx()
    val clip = LocalClipboardManager.current
    val mine = role == "user"
    val bg = if (mine) Green else MaterialTheme.colorScheme.surface
    val fg = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
    val (text, img) = remember(content) { AiChat.split(content) }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.85f).dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, contentColor = fg,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp),
            border = if (mine) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 0.5.dp,
            modifier = Modifier.widthIn(max = maxW).clip(RoundedCornerShape(16.dp))
                .combinedClickable(enabled = !typing, onClick = {}, onLongClick = { clip.setText(AnnotatedString(text)); toast(c, "Copied") })) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (typing) AiTypingDots(MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    if (text.isNotBlank()) Text(aiFormat(text, Color(0x2E808080)), fontSize = 16.sp, lineHeight = 22.sp, color = fg,
                        style = TextStyle(textDirection = TextDirection.Content))
                    if (img != null) AsyncImage(img, "Image from YID PLUS AI", contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(top = if (text.isNotBlank()) 6.dp else 0.dp).widthIn(max = 288.dp).heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(10.dp)).tap(RoundedCornerShape(10.dp)) { Nav.open(Screen.Photo(img)) })
                }
            }
        }
    }
}

@Composable
private fun AiTypingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "ai-dots")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ai-dots-p")
    Row(Modifier.padding(vertical = 6.dp, horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val ph = ((p - i) % 3f + 3f) % 3f
            val a = if (ph < 1f) 0.3f + 0.7f * sin(ph * PI).toFloat() else 0.3f
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(color))
        }
    }
}
