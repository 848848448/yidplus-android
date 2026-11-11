package com.yidplus.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google sign-in runs in the phone's browser (Google blocks it inside apps), then yidplus.com hands a one-time
 * code back to the app (yidplus://auth?code=…), bound to a secret only this app holds. No Google Play services needed.
 */
object GoogleAuth {
    private fun prefs(c: Context) = c.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun start(c: Context) {
        val b = ByteArray(32); SecureRandom().nextBytes(b)
        val v = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(v.toByteArray(StandardCharsets.US_ASCII)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs(c).edit().putString("v", v).putLong("at", System.currentTimeMillis()).apply()
        val url = Uri.parse(Api.BASE + "/api/auth/google-login?return_to=" + Uri.encode("/native-login-complete?challenge=$challenge"))
        try {
            CustomTabsIntent.Builder().setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF1F6F5C.toInt()).build())
                .setShowTitle(true).build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(c, url)
        } catch (e: ActivityNotFoundException) {
            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch (e: Exception) {
            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** true when signed in. */
    suspend fun finish(c: Context, u: Uri): Boolean {
        val p = prefs(c)
        val v = p.getString("v", null); val at = p.getLong("at", 0L)
        val code = u.getQueryParameter("code")
        p.edit().clear().apply()
        if (v == null || System.currentTimeMillis() - at > 10 * 60 * 1000 || code == null || !code.matches(Regex("[0-9a-f]{64}"))) return false
        return try {
            Api.post("/api/auth/native-exchange", JSONObject().put("code", code).put("verifier", v))
            Session.refresh(); Session.me != null
        } catch (e: Exception) { false }
    }
}
