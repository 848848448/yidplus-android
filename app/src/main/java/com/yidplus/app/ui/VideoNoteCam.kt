package com.yidplus.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private const val NOTE_MAX_MS = 60_000L
private const val NOTE_MIN_MS = 700L

private fun fmt(ms: Long): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

/**
 * WhatsApp-style video-note recorder: a circular front-camera (selfie) preview.
 * Press-and-hold the button to record (a red ring fills over up to 60s); release to send.
 * Slide up while holding to LOCK (hands-free) — then Delete / Stop / Send buttons appear.
 * A flip button swaps front/back. Produces an .mp4 File handed back via [onRecorded].
 */
@Composable
fun VideoNoteRecorder(onCancel: () -> Unit, onRecorded: (File) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasCam = res[Manifest.permission.CAMERA] ?: hasCam
        hasMic = res[Manifest.permission.RECORD_AUDIO] ?: hasMic
    }
    LaunchedEffect(Unit) { if (!hasCam || !hasMic) permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }

    var front by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var cancelArmed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var elapsed by remember { mutableStateOf(0L) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var active by remember { mutableStateOf<Recording?>(null) }
    var cancelled by remember { mutableStateOf(false) }

    DisposableEffect(hasCam, front) {
        if (hasCam) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
                    val vc = VideoCapture.withOutput(recorder)
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                    videoCapture = vc
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { runCatching { active?.stop() }; runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } }
    }

    fun startRec() {
        val vc = videoCapture ?: return
        if (recording) return
        cancelled = false; elapsed = 0L
        val file = File(File(ctx.cacheDir, "captures").apply { mkdirs() }, "note_" + System.currentTimeMillis() + ".mp4")
        val pending = vc.output.prepareRecording(ctx, FileOutputOptions.Builder(file).build())
            .let { if (hasMic) it.withAudioEnabled() else it }
        recording = true; progress = 0f
        active = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    val ms = event.recordingStats.recordedDurationNanos / 1_000_000L
                    elapsed = ms; progress = (ms.toFloat() / NOTE_MAX_MS).coerceIn(0f, 1f)
                    if (ms >= NOTE_MAX_MS) active?.stop()
                }
                is VideoRecordEvent.Finalize -> {
                    recording = false; locked = false; cancelArmed = false
                    val ms = event.recordingStats.recordedDurationNanos / 1_000_000L
                    if (!cancelled && !event.hasError() && ms >= NOTE_MIN_MS) onRecorded(file)
                    else runCatching { file.delete() }
                    active = null
                }
                else -> {}
            }
        }
    }
    fun sendRec() { runCatching { active?.stop() } }
    fun deleteRec() { cancelled = true; runCatching { active?.stop() } }

    val ink = Color(0xFF54656F)      // WhatsApp grey ink
    val grey = Color(0xFFEDEDED)     // light control bg
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
        // Close (top-left) — only before recording.
        if (!recording) Icon(Sym.Close, "Close", Modifier.align(Alignment.TopStart).padding(16.dp).size(28.dp).clickable { onCancel() }, tint = ink)

        // Red timer pill, top centre (recording).
        if (recording) Row(Modifier.align(Alignment.TopCenter).padding(top = 42.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE53935)).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)); Spacer(Modifier.width(6.dp))
            Text(fmt(elapsed), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        // Round selfie preview, upper centre, with the thin green progress arc at its top.
        Box(Modifier.align(Alignment.TopCenter).padding(top = 100.dp).size(300.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(288.dp).clip(CircleShape).background(Color(0xFF1F2C34))) {
                if (hasCam) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
            if (recording) CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(300.dp),
                color = Color(0xFF25D366), strokeWidth = 4.dp, trackColor = Color.Transparent)
        }
        if (!hasCam) Text("Camera permission needed", Modifier.align(Alignment.Center), color = ink, fontSize = 15.sp)

        // Right-edge "slide up to lock" pill (recording, not yet locked).
        if (recording && !locked) Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).clip(RoundedCornerShape(22.dp)).background(grey).padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Sym.Lock, "Slide up to lock", Modifier.size(22.dp), tint = ink); Spacer(Modifier.height(8.dp)); Icon(Sym.ExpandLess, null, Modifier.size(22.dp), tint = ink)
        }

        // Bottom bar.
        if (hasCam && recording && locked) {
            // Hands-free: Delete · Stop · Send
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 44.dp, vertical = 30.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Delete, "Delete", Modifier.size(30.dp).clickable { deleteRec() }, tint = ink)
                Box(Modifier.size(52.dp).clip(CircleShape).background(grey).clickable { sendRec() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE53935)))
                }
                Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF00A884)).clickable { sendRec() }, contentAlignment = Alignment.Center) {
                    Icon(Sym.Send, "Send", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        } else if (hasCam) {
            // flip (left) · slide-to-cancel (centre) · record button (right)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(grey).clickable(enabled = !recording) { front = !front }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Cameraswitch, "Flip camera", tint = ink, modifier = Modifier.size(24.dp))
                }
                Text(if (recording) (if (cancelArmed) "Release to cancel" else "‹ Slide to cancel") else "Hold to record",
                    color = if (cancelArmed) Color(0xFFE53935) else Color(0xFF667781), fontSize = 14.sp)
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF00A884))
                    .pointerInput(videoCapture) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (videoCapture == null) return@awaitEachGesture
                            startRec()
                            val lockPx = 110.dp.toPx(); val cancelPx = 110.dp.toPx()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
                                if (ch == null) { sendRec(); break }
                                val dx = ch.position.x - down.position.x
                                val dy = ch.position.y - down.position.y
                                cancelArmed = dx < -cancelPx
                                if (dy < -lockPx) { locked = true; break }   // slid up → lock (hands-free)
                                if (!ch.pressed) { if (cancelArmed) deleteRec() else sendRec(); break }
                            }
                        }
                    }, contentAlignment = Alignment.Center) {
                    Icon(Sym.Videocam, "Record", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
