package com.yidplus.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yidplus.app.MusicService
import com.yidplus.app.data.Api
import com.yidplus.app.data.Media
import com.yidplus.app.data.Net
import com.yidplus.app.data.Perf
import com.yidplus.app.data.Session
import com.yidplus.app.data.Store
import com.yidplus.app.data.Track
import com.yidplus.app.data.objects
import com.yidplus.app.data.str
import com.yidplus.app.data.strOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

/* QA only (fixture account): `adb shell am broadcast -a com.yidplus.app.QA_SM --es page <name>` opens helper SM's pages by name.
 * Registered while the Chats status strip or the Music tab is on screen (MainActivity's QA_OPEN list belongs to another helper). */
fun smQaOpen(page: String) {
    when (page) {
        "updates" -> Nav.open(SmUpdatesPage())
        "highlights" -> Nav.open(SmHighlightsPage())
        "post_media" -> Nav.open(PostStatusPage())
        "album" -> Nav.open(SmAlbumPage("alb1"))
        "upload" -> Nav.open(SmMusicUploadPage())
        "player" -> Nav.open(Screen.Player)
        "gallery" -> openGallery(listOf(
            GItem("https://yidplus.com/api/media/avatars%2F32a9be01-7448-43c7-b23d-cc89c97ce89f.png?v=1783239342549", false, title = "משה'לע", time = "Yesterday 19:40",
                caption = "YID PLUS 2.1 — more at yidplus.com/music 🎶"),
            GItem("https://yidplus.com/api/media/avatars%2F5df73439-246d-43d2-9b1e-fd72b30432e1.png?v=1785535039162", false, title = "DealPoint", time = "Today 08:12"),
            GItem("https://yidplus.com/api/media/shorts%2F5df73439-246d-43d2-9b1e-fd72b30432e1%2F1787477485784_b54fcb03-99e1-4453-9cad-ad7d5345bfd7.mp4", true, title = "DealPoint", time = "Today 08:15"),
            GItem("https://yidplus.com/api/media/qa-missing-photo.jpg", false, title = "Broken link test")),
            "https://yidplus.com/api/media/avatars%2F32a9be01-7448-43c7-b23d-cc89c97ce89f.png?v=1783239342549")
    }
    android.util.Log.i("YidFx", "SM-STEP open $page")
}

private object SmQaHub {
    var users = 0
    var receiver: android.content.BroadcastReceiver? = null
}

/** Mounted by the status strip (Chats) and the Music tab; one receiver however many are on screen. */
@Composable
fun SmQaReceiver() {
    val c = ctx().applicationContext
    DisposableEffect(com.yidplus.app.data.Fixture.on) {
        if (!com.yidplus.app.data.Fixture.on) return@DisposableEffect onDispose { }
        SmQaHub.users++
        if (SmQaHub.receiver == null) {
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(x: android.content.Context?, i: android.content.Intent?) { smQaOpen(i?.getStringExtra("page") ?: "") }
            }
            androidx.core.content.ContextCompat.registerReceiver(c, r, android.content.IntentFilter("com.yidplus.app.QA_SM"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
            SmQaHub.receiver = r
        }
        onDispose {
            SmQaHub.users--
            if (SmQaHub.users <= 0) { SmQaHub.users = 0; SmQaHub.receiver?.let { r -> runCatching { c.unregisterReceiver(r) } }; SmQaHub.receiver = null }
        }
    }
}
