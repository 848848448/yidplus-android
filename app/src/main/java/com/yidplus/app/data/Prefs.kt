package com.yidplus.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
  private lateinit var ctx: Context
  private val p get() = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
  /** 0 = follow the phone, 1 = light, 2 = dark, 3 = scheduled (dark between darkFrom and darkTo) */
  var theme by mutableStateOf(0)
  /** For theme == 3: dark mode turns on at this hour (0-23) and off at darkTo. Default 19:00 → 07:00. */
  var darkFrom by mutableStateOf(19)
  var darkTo by mutableStateOf(7)
  var muted by mutableStateOf(false)
  var notify by mutableStateOf(true)
  var keypadHintSeen by mutableStateOf(false)
  /** Shorts plays on to the next video by itself when one ends (like the website). On by default. */
  var autoAdvance by mutableStateOf(true)

  /** Media auto-download (WhatsApp "Storage and data"): whether each kind of chat media downloads
    * on its own, per network. Documents have no rule: files only open when tapped, so nothing ever
    * downloads for them on its own. Defaults are WhatsApp's: photos and audio on mobile data and
    * Wi-Fi, videos on Wi-Fi only, nothing while roaming. */
  enum class DlMedia { Photo, Video, Audio }
  enum class DlNet { Mobile, Wifi, Roaming }
  var dlPhotoMobile by mutableStateOf(true)
  var dlPhotoWifi by mutableStateOf(true)
  var dlPhotoRoaming by mutableStateOf(false)
  var dlVideoMobile by mutableStateOf(false)
  var dlVideoWifi by mutableStateOf(true)
  var dlVideoRoaming by mutableStateOf(false)
  var dlAudioMobile by mutableStateOf(true)
  var dlAudioWifi by mutableStateOf(true)
  var dlAudioRoaming by mutableStateOf(false)

  /** The stored rule for one media type on one network. */
  fun autoDownloadValue(media: DlMedia, net: DlNet): Boolean = when (media) {
    DlMedia.Photo -> when (net) { DlNet.Mobile -> dlPhotoMobile; DlNet.Wifi -> dlPhotoWifi; DlNet.Roaming -> dlPhotoRoaming }
    DlMedia.Video -> when (net) { DlNet.Mobile -> dlVideoMobile; DlNet.Wifi -> dlVideoWifi; DlNet.Roaming -> dlVideoRoaming }
    DlMedia.Audio -> when (net) { DlNet.Mobile -> dlAudioMobile; DlNet.Wifi -> dlAudioWifi; DlNet.Roaming -> dlAudioRoaming }
  }

  fun setAutoDownload(media: DlMedia, net: DlNet, v: Boolean) {
    when (media) {
      DlMedia.Photo -> when (net) { DlNet.Mobile -> dlPhotoMobile = v; DlNet.Wifi -> dlPhotoWifi = v; DlNet.Roaming -> dlPhotoRoaming = v }
      DlMedia.Video -> when (net) { DlNet.Mobile -> dlVideoMobile = v; DlNet.Wifi -> dlVideoWifi = v; DlNet.Roaming -> dlVideoRoaming = v }
      DlMedia.Audio -> when (net) { DlNet.Mobile -> dlAudioMobile = v; DlNet.Wifi -> dlAudioWifi = v; DlNet.Roaming -> dlAudioRoaming = v }
    }
    p.edit().putBoolean("dl_" + media.name.lowercase() + "_" + net.name.lowercase(), v).apply()
  }

  /** Whether [media] may download on its own on the current network (Net.kind). Offline, or a network
    * whose kind could not be read, never auto-downloads. */
  fun autoDownload(media: DlMedia): Boolean = when (Net.kind) {
    NetKind.Wifi -> autoDownloadValue(media, DlNet.Wifi)
    NetKind.Mobile -> autoDownloadValue(media, DlNet.Mobile)
    NetKind.Roaming -> autoDownloadValue(media, DlNet.Roaming)
    NetKind.Offline, NetKind.Unknown -> false
  }

  fun init(c: Context) {
    ctx = c.applicationContext
    theme = p.getInt("theme", 0); muted = p.getBoolean("muted", false); notify = p.getBoolean("notify", true)
    keypadHintSeen = p.getBoolean("keypadHint", false)
    autoAdvance = p.getBoolean("autoAdvance", true)
    darkFrom = p.getInt("darkFrom", 19); darkTo = p.getInt("darkTo", 7)
    dlPhotoMobile = p.getBoolean("dl_photo_mobile", true); dlPhotoWifi = p.getBoolean("dl_photo_wifi", true); dlPhotoRoaming = p.getBoolean("dl_photo_roaming", false)
    dlVideoMobile = p.getBoolean("dl_video_mobile", false); dlVideoWifi = p.getBoolean("dl_video_wifi", true); dlVideoRoaming = p.getBoolean("dl_video_roaming", false)
    dlAudioMobile = p.getBoolean("dl_audio_mobile", true); dlAudioWifi = p.getBoolean("dl_audio_wifi", true); dlAudioRoaming = p.getBoolean("dl_audio_roaming", false)
  }

  fun setDarkSchedule(from: Int, to: Int) { darkFrom = from.coerceIn(0, 23); darkTo = to.coerceIn(0, 23); p.edit().putInt("darkFrom", darkFrom).putInt("darkTo", darkTo).apply() }
  fun enableAutoAdvance(v: Boolean) { autoAdvance = v; p.edit().putBoolean("autoAdvance", v).apply() }
  fun setThemeMode(v: Int) { theme = v; p.edit().putInt("theme", v).apply() }
  fun setMute(v: Boolean) { muted = v; p.edit().putBoolean("muted", v).apply() }
  fun enableNotify(v: Boolean) { notify = v; p.edit().putBoolean("notify", v).apply() }
  /** The tab you were on, so the app opens straight back to it. */
  var lastTab: String
    get() = p.getString("lastTab", "") ?: ""
    set(v) { p.edit().putString("lastTab", v).apply() }
  fun hintSeen() { keypadHintSeen = true; p.edit().putBoolean("keypadHint", true).apply() }

  // Guests' likes on For You stay on the phone (same as the website).
  fun localLikes(): MutableSet<String> = HashSet(p.getStringSet("likes", emptySet()) ?: emptySet())
  fun saveLocalLikes(s: Set<String>) { p.edit().putStringSet("likes", s.toList().takeLast(2000).toSet()).apply() }
  fun saved(): MutableSet<String> = HashSet(p.getStringSet("saved", emptySet()) ?: emptySet())
  fun saveSaved(s: Set<String>) { p.edit().putStringSet("saved", s).apply() }
  fun seen(): MutableList<String> = (p.getString("seen", "") ?: "").split('\n').filter { it.isNotBlank() }.toMutableList()
  fun saveSeen(l: List<String>) { p.edit().putString("seen", l.takeLast(5000).joinToString("\n")).apply() }
}
