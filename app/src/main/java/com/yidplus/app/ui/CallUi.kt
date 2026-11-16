package com.yidplus.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * The call overlay, mounted once at the app root. It covers the screen while a call is
 * ringing in, going out, or connected — over whatever page is behind it.
 */
@Composable
fun CallHost() {
    val inc = CallKit.incoming
    val phase = CallKit.phase
    if (phase != CallPhase.IDLE) {
        Box(Modifier.fillMaxSize().zIndex(10f)) { ActiveCall() }
    } else if (inc != null) {
        Box(Modifier.fillMaxSize().zIndex(10f)) { IncomingCall(inc) }
    }
}

@Composable
private fun IncomingCall(inc: IncomingCall) {
    val c = ctx()
    val ask = rememberCallPermissions(inc.kind == "video") { CallKit.acceptIncoming(c) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B141A)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(inc.callerPhoto, inc.callerNick, 110.dp)
            Spacer(Modifier.height(18.dp))
            Text(inc.callerNick, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(if (inc.kind == "video") "Incoming video call…" else "Incoming voice call…", color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 36.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallAction(Sym.CallEnd, "Decline", Color(0xFFE53935)) { CallKit.declineIncoming() }
            CallAction(Sym.Call, "Accept", Color(0xFF25D366)) { ask() }
        }
    }
}

@Composable
private fun ActiveCall() {
    val c = ctx()
    val video = CallKit.kind == "video"
    Box(Modifier.fillMaxSize().background(Color(0xFF0B141A))) {
        // Remote video fills the screen; local camera sits in a corner.
        if (video && CallKit.remoteVideo != null) {
            RtcVideo(CallKit.remoteVideo, mirror = false, Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize().padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(CallKit.peerPhoto, CallKit.peerName, 120.dp)
                Spacer(Modifier.height(18.dp))
                Text(CallKit.peerName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                CallDuration()
            }
        }
        if (video && CallKit.cameraOn && CallKit.localVideo != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(104.dp, 150.dp).clip(RoundedCornerShape(12.dp))) {
                RtcVideo(CallKit.localVideo, mirror = true, Modifier.fillMaxSize())
            }
        }
        if (video && CallKit.remoteVideo != null) {
            Row(Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = .35f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(CallKit.peerName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp)); CallDuration()
            }
        }
        // Controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 34.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            CallAction(if (CallKit.muted) Sym.MicOff else Sym.Mic, if (CallKit.muted) "Unmute" else "Mute", Color(0xFF2A3942)) { CallKit.toggleMute() }
            if (video) CallAction(if (CallKit.cameraOn) Sym.Videocam else Sym.VideocamOff, "Camera", Color(0xFF2A3942)) { CallKit.toggleCamera() }
            else CallAction(if (CallKit.speakerOn) Sym.VolumeUp else Sym.VolumeOff, "Speaker", Color(0xFF2A3942)) { CallKit.toggleSpeaker(c) }
            if (video) CallAction(Sym.Videocam, "Flip", Color(0xFF2A3942)) { CallKit.switchCamera() }
            CallAction(Sym.CallEnd, "End", Color(0xFFE53935)) { CallKit.hangUp() }
        }
    }
}

private fun statusText(): String {
    if (CallKit.phase == CallPhase.OUTGOING) return CallKit.statusLine.ifBlank { "Calling…" }
    if (!CallKit.connected) return CallKit.statusLine.ifBlank { "Connecting…" }
    return "" // duration shown by DurationText where needed
}

@Composable
private fun RtcVideo(track: VideoTrack?, mirror: Boolean, modifier: Modifier) {
    val c = ctx()
    val renderer = remember {
        SurfaceViewRenderer(c).apply {
            runCatching { CallKit.eglContext()?.let { init(it, null) } }
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }
    LaunchedEffect(mirror) { runCatching { renderer.setMirror(mirror) } }
    DisposableEffect(track) {
        runCatching { track?.addSink(renderer) }
        onDispose { runCatching { track?.removeSink(renderer) } }
    }
    DisposableEffect(Unit) { onDispose { runCatching { renderer.release() } } }
    AndroidView(factory = { renderer }, modifier = modifier)
}

@Composable
private fun CallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(bg).tap(CircleShape, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
    }
}

/** Shows a live mm:ss timer once connected; falls back to the status line otherwise. */
@Composable
fun CallDuration(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(CallKit.connected) { while (CallKit.connected) { now = System.currentTimeMillis(); delay(1000) } }
    val secs = if (CallKit.connected && CallKit.startedAt > 0) ((now - CallKit.startedAt) / 1000).toInt() else 0
    val t = if (CallKit.connected) "%d:%02d".format(secs / 60, secs % 60) else statusText()
    Text(t, color = Color.White.copy(alpha = .75f), fontSize = 16.sp, modifier = modifier)
}

/** Returns a launcher that asks for mic (+ camera for video), then runs onGranted if allowed. */
@Composable
private fun rememberCallPermissions(video: Boolean, onGranted: () -> Unit): () -> Unit {
    val c = ctx()
    val needed = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onGranted()
        else toast(c, "Allow the microphone to make calls")
    }
    return {
        if (needed.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }) onGranted()
        else launcher.launch(needed)
    }
}

