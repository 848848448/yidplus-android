package com.yidplus.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yidplus.app.data.Api
import com.yidplus.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Instant notifications on phones with Google Play services: yidplus.com already sends a small Firebase
 * message (project shorts-e05cc, secret FCM_SERVICE_ACCOUNT_JSON on the site) to every device registered at
 * /api/push/native-device. The message only says "something new"; the app then runs the same check as the
 * 15-minute worker, so the notification shows who wrote what, grouped by chat, with the right screen on tap.
 * Without Play services (or before the site's secret is set) nothing breaks: the 15-minute check carries on.
 */
object Push {
    private const val TAG = "YidplusPush"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun prefs(c: Context) = c.getSharedPreferences("push", Context.MODE_PRIVATE)

    var state: String = "off"; private set

    private fun deviceId(c: Context): String {
        prefs(c).getString("id", null)?.let { return it }
        val b = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val id = android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        prefs(c).edit().putString("id", id).apply(); return id
    }

    private fun firebase(c: Context): Boolean {
        if (!BuildConfig.FCM) return false
        return runCatching { if (FirebaseApp.getApps(c).isEmpty()) FirebaseApp.initializeApp(c) != null else true }.getOrDefault(false)
    }

    /** After sign-in and at each app start while signed in (off the main thread, after the first screen). */
    fun start(ctx: Context) {
        val c = ctx.applicationContext
        if (!Api.hasSession() || !Prefs.notify) return
        scope.launch {
            if (!firebase(c)) { state = "not available"; return@launch }
            runCatching {
                FirebaseMessaging.getInstance().isAutoInitEnabled = true
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { t -> scope.launch { register(c, t) } }
                    .addOnFailureListener { e -> state = "no Google Play services"; Log.i(TAG, "no token: ${e.message}") }
            }.onFailure { state = "not available" }
        }
    }

    /** QA: can this phone get a Firebase token at all (Google Play services present)? Logs the answer, never the token. */
    fun probe(ctx: Context) {
        val c = ctx.applicationContext
        scope.launch {
            if (!firebase(c)) { Log.i(TAG, "probe: firebase not available"); return@launch }
            runCatching {
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { t -> Log.i(TAG, "probe: token ok (${t.length} chars)") }
                    .addOnFailureListener { e -> Log.i(TAG, "probe: no token (${e.javaClass.simpleName}: ${e.message})") }
            }.onFailure { Log.i(TAG, "probe: failed ${it.message}") }
        }
    }

    suspend fun register(c: Context, token: String) {
        if (!Api.hasSession()) return
        if (prefs(c).getString("sent", null) == token && System.currentTimeMillis() - prefs(c).getLong("sentAt", 0) < 24L * 3600_000) { state = "on"; return }
        val ok = call(c, "POST", JSONObject().put("id", deviceId(c)).put("token", token))
        state = if (ok) "on" else "waiting for yidplus.com"
        if (ok) prefs(c).edit().putString("sent", token).putLong("sentAt", System.currentTimeMillis()).apply()
        Log.i(TAG, "register -> $state")
    }

    /** Before signing out: this phone stops getting that account's notifications. */
    suspend fun unregister(c: Context) {
        if (prefs(c).getString("sent", null) == null) return
        call(c, "DELETE", JSONObject().put("id", deviceId(c)))
        prefs(c).edit().remove("sent").remove("sentAt").apply()
        runCatching { if (firebase(c)) FirebaseMessaging.getInstance().deleteToken() }
        state = "off"
    }

    private fun call(c: Context, method: String, body: JSONObject): Boolean = runCatching {
        val r = Request.Builder().url(Api.BASE + "/api/push/native-device").header("Origin", Api.BASE).header("Accept", "application/json")
            .method(method, body.toString().toRequestBody(Api.JSON)).build()
        Api.http.newCall(r).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** A Firebase message arrived: check right away and show the real notifications. */
    fun onPush(c: Context, data: Map<String, String>) {
        Api.init(c); Prefs.init(c)
        if (!Prefs.notify || !Api.hasSession()) return
        val done = runBlocking { withTimeoutOrNull(15_000) { runCatching { NotifyWorker.check(c) }.isSuccess } }
        if (done != true) NotifyWorker.now(c)
    }
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Api.init(applicationContext)
        CoroutineScope(Dispatchers.IO).launch { Push.register(applicationContext, token) }
    }
    override fun onMessageReceived(message: RemoteMessage) = Push.onPush(applicationContext, message.data)
}
