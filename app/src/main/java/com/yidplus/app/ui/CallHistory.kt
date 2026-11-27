package com.yidplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One past call for the list. */
private data class CallRow(val id: String, val kind: String, val otherId: String, val nick: String, val photo: String?,
                           val outgoing: Boolean, val missed: Boolean, val answered: Boolean, val at: String?)

private fun callRows(j: JSONObject): List<CallRow> = chObjs(j.optJSONArray("calls")).map { c ->
    CallRow(c.chStr("id"), c.chStr("kind").ifBlank { "video" }, c.chStr("other_id"),
        c.chStr("other_nick").ifBlank { "Unknown" }, Api.abs(c.chStr("other_photo").ifBlank { null }),
        c.optBoolean("outgoing"), c.optBoolean("missed"), c.optBoolean("answered"), c.chStr("created_at").ifBlank { null })
}

/** WhatsApp's Calls tab: recent calls with in/out/missed arrows, and a tap to call back. */
class ChCalls : PageScreen() {
    override val perfName get() = "Calls"
    @Composable override fun Content() {
        val c = ctx()
        var list by remember { mutableStateOf<List<CallRow>?>(null) }
        suspend fun load() { list = runCatching { withContext(Dispatchers.IO) { callRows(Api.get("/api/call?history=1")) } }.getOrElse { toast(c, Api.friendly(it)); emptyList() } }
        LaunchedEffect(Unit) { load() }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar("Calls", onBack = { Nav.back() })
            val l = list
            when {
                l == null -> ChSkeleton(6)
                l.isEmpty() -> ChEmpty(Sym.Call, "No calls yet. Call someone from their chat and it shows up here.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(l, key = { it.id }) { r -> Box(Modifier.animateItem()) { CallHistoryRow(r) } }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(r: CallRow) {
    val c = ctx()
    val arrow = when { r.missed -> Sym.CallReceived; r.outgoing -> Sym.CallMade; else -> Sym.CallReceived }
    val arrowTint = if (r.missed) Color(0xFFE53935) else Color(0xFF25D366)
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
        .tap(androidx.compose.foundation.shape.RoundedCornerShape(0.dp)) {
            // Tap the row → open the chat with that person.
            Nav.open(Screen.Chat(ChatApi.roomOf(r.otherId, "private", r.nick)))
        }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ChAvatar(r.photo, r.nick, r.otherId, 50.dp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(r.nick, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (r.missed) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface, style = TextStyle(textDirection = TextDirection.Content))
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(arrow, null, Modifier.size(16.dp), tint = arrowTint)
                Text("  " + (if (r.missed) "Missed" else if (r.outgoing) "Outgoing" else "Incoming") + " · " + chatTime(r.at),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Call back, same kind.
        ChIcon(if (r.kind == "audio") Sym.Call else Sym.Videocam, "Call back", onClick = {
            CallKit.startCall(c, r.otherId, r.nick, r.photo, video = r.kind != "audio")
        })
    }
}
