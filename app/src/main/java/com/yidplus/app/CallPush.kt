package com.yidplus.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.yidplus.app.data.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Rings the phone for an incoming voice/video call even when the app is closed. The site sends a high-priority
 * Firebase data message (type=call) the moment a caller dials; this turns it into a full-screen, WhatsApp-style
 * incoming-call notification over the lock screen, with Answer / Decline. Answer opens the app, which picks the
 * ringing call up through CallKit's incoming poll; Decline hangs up on the site without opening anything.
 */
object CallPush {
    private const val TAG = "YidCallPush"
    const val CH_CALLS = "calls"
    private const val NOTIF_ID = 90210
    const val EXTRA_CALL_ID = "call_id"

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH_CALLS) != null) return
        val ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val ch = NotificationChannel(CH_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Voice and video calls"
            setShowBadge(false)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            runCatching { setSound(ring, attrs) }
        }
        nm.createNotificationChannel(ch)
    }

    /** A Firebase call-message arrived. */
    fun onCallPush(ctx: Context, data: Map<String, String>) {
        val c = ctx.applicationContext
        Api.init(c)
        val callId = data["call_id"].orEmpty()
        if (callId.isBlank()) return
        val name = data["caller_nick"].orEmpty().ifBlank { data["title"].orEmpty().ifBlank { "Someone" } }
        val kind = data["kind"].orEmpty().ifBlank { "video" }
        channel(c)
        val caller = Person.Builder().setName(name).setImportant(true).build()

        // Answer → open the app; CallKit's incoming poll shows the in-app accept screen.
        val answerIntent = Intent(c, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CALL).putExtra(EXTRA_CALL_ID, callId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val answerPi = PendingIntent.getActivity(c, callId.hashCode(), answerIntent, pendingFlags())
        // Decline → hang up on the site, show nothing.
        val declineIntent = Intent(c, CallDeclineReceiver::class.java)
            .setAction("com.yidplus.app.CALL_DECLINE").putExtra(EXTRA_CALL_ID, callId)
        val declinePi = PendingIntent.getBroadcast(c, callId.hashCode() + 1, declineIntent, pendingFlags())

        val label = if (kind == "audio") "Incoming voice call" else "Incoming video call"
        val b = NotificationCompat.Builder(c, CH_CALLS)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(0xFF008069.toInt())
            .setContentTitle(name)
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(answerPi, true)
            .setContentIntent(answerPi)
        runCatching {
            b.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, answerPi))
        }.onFailure {
            // Older cores: plain actions instead of CallStyle.
            b.addAction(0, "Decline", declinePi).addAction(0, "Answer", answerPi)
        }
        runCatching { NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build()) }
            .onFailure { Log.w(TAG, "call notif failed: ${it.message}") }
    }

    fun dismiss(ctx: Context) = runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) }

    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}

/** Decline from the call notification: hang up on the site, then clear the notification. */
class CallDeclineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        val callId = intent.getStringExtra(CallPush.EXTRA_CALL_ID).orEmpty()
        CallPush.dismiss(c)
        if (callId.isBlank()) return
        Api.init(c)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body = JSONObject().put("action", "decline").put("call_id", callId)
                val r = Request.Builder().url(Api.BASE + "/api/call").header("Origin", Api.BASE)
                    .header("Accept", "application/json").post(body.toString().toRequestBody(Api.JSON)).build()
                Api.http.newCall(r).execute().use { it.isSuccessful }
            }
            pending.finish()
        }
    }
}
