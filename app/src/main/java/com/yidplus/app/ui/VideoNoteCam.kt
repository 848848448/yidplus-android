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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private const val NOTE_MAX_MS = 60_000L
private const val NOTE_MIN_MS = 700L

/**
 * WhatsApp-style video-note recorder: a circular front-camera (selfie) preview, press-and-hold the
 * button to record (a red ring fills over up to 60s), release to send. A flip button swaps front/back.
 * Produces an .mp4 File and hands it back via [onRecorded]; the chat then sends it as a 'videonote'.
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
    var progress by remember { mutableStateOf(0f) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var active by remember { mutableStateOf<Recording?>(null) }

    // Bind the camera (re-binds when permission is granted or the camera is flipped).
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
        val file = File(File(ctx.cacheDir, "captures").apply { mkdirs() }, "note_" + System.currentTimeMillis() + ".mp4")
        val pending = vc.output.prepareRecording(ctx, FileOutputOptions.Builder(file).build())
            .let { if (hasMic) it.withAudioEnabled() else it }
        recording = true; progress = 0f
        active = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    val ms = event.recordingStats.recordedDurationNanos / 1_000_000L
                    progress = (ms.toFloat() / NOTE_MAX_MS).coerceIn(0f, 1f)
                    if (ms >= NOTE_MAX_MS) active?.stop()
                }
                is VideoRecordEvent.Finalize -> {
                    recording = false
                    val ms = event.recordingStats.recordedDurationNanos / 1_000_000L
                    if (!event.hasError() && ms >= NOTE_MIN_MS) onRecorded(file)
                    else { runCatching { file.delete() } }
                    active = null
                }
                else -> {}
            }
        }
    }
    fun stopRec() { runCatching { active?.stop() } }

    Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
        Icon(Sym.Close, "Close", Modifier.align(Alignment.TopStart).padding(16.dp).size(28.dp).clickable { stopRec(); onCancel() }, tint = Color.White)
        if (!recording) Icon(Icons.Filled.Cameraswitch, "Flip camera", Modifier.align(Alignment.TopEnd).padding(16.dp).size(28.dp).clickable { front = !front }, tint = Color.White)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(292.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(280.dp).clip(CircleShape).background(Color(0xFF263238))) {
                    if (hasCam) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
                if (recording) CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFE53935), strokeWidth = 4.dp, trackColor = Color.Transparent)
            }
            Spacer(Modifier.height(26.dp))
            Text(if (!hasCam) "Camera permission needed" else if (recording) "Release to send" else "Hold to record", color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(78.dp).clip(CircleShape).background(if (recording) Color(0xFFE53935) else Color.White)
                .pointerInput(videoCapture) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (videoCapture != null) { startRec(); waitForUpOrCancellation(); stopRec() }
                    }
                }, contentAlignment = Alignment.Center) {
                Icon(Sym.Videocam, "Record", tint = if (recording) Color.White else Color(0xFFE53935), modifier = Modifier.size(34.dp))
            }
        }
    }
}
