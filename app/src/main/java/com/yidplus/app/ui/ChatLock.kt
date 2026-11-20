package com.yidplus.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lock individual chats behind the phone's fingerprint / PIN, like WhatsApp's Chat lock. Which chats are locked is
 * stored on this device only; opening one asks for the device credential (no lockout — there is always a PIN fallback).
 * An unlocked chat stays open until the app leaves the foreground, then it asks again.
 */
object ChatLock {
    private var p: android.content.SharedPreferences? = null
    private val unlockedSession = HashSet<String>()
    /** Bumped on lock/unlock so Compose lists recompose. */
    var version by mutableStateOf(0); private set
    /** Set by MainActivity: launches the system confirm-credential screen for a room. */
    var onUnlockRequest: ((String) -> Unit)? = null

    fun init(c: Context) { if (p == null) p = c.applicationContext.getSharedPreferences("chatlock", Context.MODE_PRIVATE) }

    private fun ids(): MutableSet<String> = HashSet(p?.getStringSet("ids", emptySet()) ?: emptySet())

    fun isLocked(roomId: String): Boolean = p?.getStringSet("ids", emptySet())?.contains(roomId) == true
    fun needsUnlock(roomId: String): Boolean = isLocked(roomId) && roomId !in unlockedSession

    fun lock(roomId: String) { val s = ids(); s.add(roomId); p?.edit()?.putStringSet("ids", s)?.apply(); unlockedSession.remove(roomId); version++ }
    fun unlock(roomId: String) { val s = ids(); s.remove(roomId); p?.edit()?.putStringSet("ids", s)?.apply(); unlockedSession.remove(roomId); version++ }

    /** Confirmed by the device credential (or no lock set): open this chat for the rest of the session. */
    fun markUnlocked(roomId: String) { unlockedSession.add(roomId); version++ }

    /** App went to the background: every locked chat asks again next time. */
    fun relockAll() { if (unlockedSession.isNotEmpty()) { unlockedSession.clear(); version++ } }
}
