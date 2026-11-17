package com.yidplus.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App lock: require the phone's fingerprint / PIN to open YID PLUS, like WhatsApp. Off by default. It uses the
 * device's own secure-lock confirmation (KeyguardManager), so there is always a PIN fallback and no way to get
 * locked out of your own app. If the phone has no lock set, turning it on does nothing and opening never blocks.
 */
object AppLock {
    var enabled by mutableStateOf(false); private set
    /** true = show the lock screen and block the app until unlocked. */
    var locked by mutableStateOf(false)
    /** Set by MainActivity: launches the system confirm-credential screen. */
    var onUnlockRequest: (() -> Unit)? = null

    private var p: android.content.SharedPreferences? = null

    fun init(c: Context) {
        p = c.applicationContext.getSharedPreferences("applock", Context.MODE_PRIVATE)
        enabled = p?.getBoolean("on", false) == true
        locked = enabled
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        p?.edit()?.putBoolean("on", on)?.apply()
        locked = on && locked
    }

    /** App went to the background: re-lock so the next open asks again. */
    fun onBackground() { if (enabled) locked = true }

    fun markUnlocked() { locked = false }
}
