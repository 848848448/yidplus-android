package com.yidplus.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * In-app photo camera for the chat: a full-screen back-camera preview.
 * Tap the shutter to take a photo; the .jpg File is handed back via [onPhoto].
 * A flip button swaps front/back. Mirrors VideoNoteRecorder's CameraX setup
 * (PreviewView, ProcessCameraProvider binding, CAMERA permission request,
 * files under cacheDir/captures) -- without audio/recording.
 */
@Composable
fun PhotoCamera(onCancel: () -> Unit, onPhoto: (File) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCam = granted }
    LaunchedEffect(Unit) { if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA) }

    var front by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(hasCam, front) {
        if (hasCam) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val ic = ImageCapture.Builder().build()
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, ic)
                    imageCapture = ic
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } }
    }

    fun takePhoto() {
        val ic = imageCapture ?: return
        val file = File(File(ctx.cacheDir, "captures").apply { mkdirs() }, "photo_" + System.currentTimeMillis() + ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        ic.takePicture(options, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { onPhoto(file) }
            override fun onError(exc: ImageCaptureException) { toast(ctx, "Couldn't take the photo") }
        })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCam) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (!hasCam) Text("Camera permission needed", Modifier.align(Alignment.Center), color = Color.White, fontSize = 15.sp)

        // Close (top-left).
        Icon(Sym.Close, "Close", Modifier.align(Alignment.TopStart).padding(16.dp).size(28.dp).clickable { onCancel() }, tint = Color.White)

        // Bottom bar: flip (left) - shutter (centre) - spacer (right, keeps shutter centred).
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x40FFFFFF)).clickable { front = !front }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Cameraswitch, "Flip camera", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF00A884)).clickable { takePhoto() }, contentAlignment = Alignment.Center) {
                Icon(Sym.PhotoCamera, "Take photo", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Box(Modifier.size(48.dp))
        }
    }
}
