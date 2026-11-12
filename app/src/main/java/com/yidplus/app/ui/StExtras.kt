package com.yidplus.app.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yidplus.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/* 2.1 ST extras: Download my data (the site's export-own.js), and App lock (a WhatsApp-style lock, kept on this phone only). */

private val stSections = listOf("account", "posts", "shorts", "statuses", "music", "sent_messages")

/** export-own.js downloadMyData: GET /api/my-export?section=<s>[&before=&after=] page by page, the same JSON file (format yidplus-own-data-v1), saved to Downloads. */
suspend fun stExportMyData(c: Context, progress: (String) -> Unit): String = withContext(Dispatchers.IO) {
    try {
        val data = JSONObject().put("format", "yidplus-own-data-v1").put("exported_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
            .put("scope", "Own account, owned content and own sent messages. Media references only. No other people's private messages or account secrets.")
        var pages = 0; var size = 0L
        for (s in stSections) {
            withContext(Dispatchers.Main) { progress("Preparing " + s.replace('_', ' ') + "…") }
            val rows = JSONArray()
            var cursor: JSONObject? = null
            do {
                var path = "/api/my-export?section=$s"
                if (cursor != null) path += "&before=" + android.net.Uri.encode(cursor.optString("before")) + "&after=" + android.net.Uri.encode(cursor.optString("after"))
                val r = Api.get(path)
                if (r.has("ok") && !r.optBoolean("ok")) throw Api.ApiError(500, r.optString("error").ifBlank { "Export unavailable" })
                val page = r.optJSONArray("rows") ?: JSONArray()
                for (i in 0 until page.length()) rows.put(page.get(i))
                pages++; size += page.toString().length
                if (pages > 500 || size > 30L * 1024 * 1024) throw Api.ApiError(413, "Export is too large for this device. Nothing was downloaded.")
                cursor = r.optJSONObject("next")
            } while (cursor != null)
            data.put(s, rows)
        }
        val text = data.toString(2)
        val name = "yidplus-my-data.json"
        android.util.Log.i("YidFx", "EXPORT sections=${stSections.size} pages=$pages bytes=${text.length}")
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return@withContext "Couldn't save the file"
            c.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        } else {
            val dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: c.filesDir
            java.io.File(dir, name).writeText(text)
        }
        "Own data export ready in Downloads ($name). Keep it private."
    } catch (e: Exception) { Api.friendly(e) }
}

/** App lock: a 4-digit PIN asked when the app opens (no fingerprint on the Qin F21). Kept on this phone only; default off. */
object StLock {
    private var ctx: Context? = null
    private fun p() = ctx?.getSharedPreferences("st_lock", Context.MODE_PRIVATE)
    var enabled by mutableStateOf(false); private set
    var locked by mutableStateOf(false)
    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        enabled = p()?.getString("pin", null) != null
        locked = enabled
    }
    private fun hash(pin: String) = MessageDigest.getInstance("SHA-256").digest(("yidplus:" + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    fun set(pin: String) { p()?.edit()?.putString("pin", hash(pin))?.apply(); enabled = true; locked = false }
    fun off() { p()?.edit()?.remove("pin")?.apply(); enabled = false; locked = false }
    fun check(pin: String): Boolean = p()?.getString("pin", null) == hash(pin)
}

/** Wrap the app in this (AppRoot patch): while locked it shows the PIN pad instead. */
@Composable
fun StLockGate(content: @Composable () -> Unit) {
    StLock.init(ctx())
    if (StLock.locked) StPinPad("Enter your PIN", "YID PLUS is locked", onDone = { pin -> if (StLock.check(pin)) { StLock.locked = false; null } else "Wrong PIN" }) else content()
}

/** A 4-digit PIN pad that works by touch and by the phone's number keys. onDone returns an error text, or null when accepted. */
@Composable
fun StPinPad(title: String, sub: String?, onDone: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun add(d: String) {
        if (pin.length >= 4) return
        pin += d; err = null
        if (pin.length == 4) { err = onDone(pin); pin = "" }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).firstFocus(always = true)
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val n = when (e.key) { Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"; Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"
                Key.Five, Key.NumPad5 -> "5"; Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"; Key.Nine, Key.NumPad9 -> "9"; else -> null }
            if (n != null) { add(n); true } else if (e.key == Key.Backspace || e.key == Key.Delete) { pin = pin.dropLast(1); true } else false
        }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i -> Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
        }
        Text(err ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { k ->
                    if (k.isEmpty()) Spacer(Modifier.size(64.dp, 52.dp))
                    else Box(Modifier.size(64.dp, 52.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .tap(RoundedCornerShape(26.dp)) { if (k == "<") pin = pin.dropLast(1) else add(k) }, contentAlignment = Alignment.Center) {
                        if (k == "<") Icon(Icons.Rounded.Backspace, "Delete") else Text(k, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Settings → Privacy → App lock. */
class StAppLockPage : PageScreen() {
    override val perfName: String get() = "AppLock"
    @Composable override fun Content() {
        val c = ctx()
        StLock.init(c)
        var step by remember { mutableStateOf(0) } // 0 = settings, 1 = new pin, 2 = confirm, 3 = test lock
        var first by remember { mutableStateOf("") }
        when (step) {
            1 -> StPinPad("Choose a 4-digit PIN", "You'll type it when YID PLUS opens", onDone = { first = it; step = 2; null })
            2 -> StPinPad("Type the PIN again", null, onDone = { if (it == first) { StLock.set(it); toast(c, "App lock is on"); android.util.Log.i("YidFx", "LOCK on"); step = 0; null } else { step = 1; "The two PINs are not the same" } })
            3 -> StPinPad("Enter your PIN", "YID PLUS is locked", onDone = { if (StLock.check(it)) { step = 0; null } else "Wrong PIN" })
            else -> SettingsPage("App lock") {
                SettingsCard {
                    SettingRow(if (StLock.enabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock with a PIN", if (StLock.enabled) "On · asks your PIN when the app opens" else "Off", switch = StLock.enabled,
                        modifier = Modifier.firstFocus()) { if (StLock.enabled) { StLock.off(); toast(c, "App lock is off"); android.util.Log.i("YidFx", "LOCK off") } else step = 1 }
                    if (StLock.enabled) {
                        RowDivider(64.dp)
                        SettingRow(Icons.Rounded.Pin, "Change PIN", null) { step = 1 }
                        RowDivider(64.dp)
                        SettingRow(Icons.Rounded.Lock, "Lock now", "See the lock screen") { step = 3 }
                    }
                }
                Text("The PIN is kept on this phone only. If you forget it, clear the app's data in Android settings and sign in again.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
