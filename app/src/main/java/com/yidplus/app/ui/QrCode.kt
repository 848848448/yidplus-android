package com.yidplus.app.ui

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.yidplus.app.data.Api
import com.yidplus.app.data.Session
import kotlinx.coroutines.launch

/** Build a crisp black-on-white QR bitmap for [content]. */
fun qrBitmap(content: String, sizePx: Int = 640): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val w = matrix.width; val h = matrix.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val black = android.graphics.Color.BLACK; val white = android.graphics.Color.WHITE
    val row = IntArray(w)
    for (y in 0 until h) {
        for (x in 0 until w) row[x] = if (matrix.get(x, y)) black else white
        bmp.setPixels(row, 0, w, 0, y, w, 1)
    }
    bmp
}.getOrNull()

/** The link a YID PLUS QR encodes for a person: the app recognises it on scan and opens a chat with them. */
fun qrLinkFor(id: String, nick: String): String =
    "https://yidplus.com/u/" + id + "?n=" + java.net.URLEncoder.encode(nick, "UTF-8")

/** My QR code + a scanner, like WhatsApp. Scan someone's code → open a chat with them. */
class QrPage(val startScan: Boolean = false) : PageScreen() {
    override val perfName get() = "QrCode"
    @Composable override fun Content() {
        val c = ctx(); val scope = rememberCoroutineScope()
        val me = Session.me
        var scanning by remember { mutableStateOf(startScan) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ChBar(if (scanning) "Scan code" else "My QR code", onBack = { Nav.back() }) {
                IconBtn(if (scanning) Sym.QrCode else Sym.QrCodeScanner, if (scanning) "My code" else "Scan", { scanning = !scanning })
            }
            if (scanning) {
                QrScanner { text ->
                    scanning = false
                    val id = Regex("""yidplus\.com/u/([A-Za-z0-9_-]+)""").find(text)?.groupValues?.get(1)
                    if (id != null && id != me?.id) scope.launch {
                        runCatching { ChatApi.openDm(id) }.onSuccess { rid -> ChatApi.rooms.load(true); Nav.open(Screen.Chat(ChatApi.info(rid)?.room ?: ChatApi.roomOf(rid, "private", "Chat"))) }
                            .onFailure { toast(c, Api.friendly(it)) }
                    } else if (id == me?.id) toast(c, "That's your own code 🙂")
                    else if (text.startsWith("http")) openUrl(c, text)
                    else toast(c, "Not a YID PLUS code")
                }
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    ChAvatar(me?.photo, me?.nickname ?: "Me", me?.id ?: "me", 72.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(me?.nickname ?: "Me", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(textDirection = TextDirection.Content))
                    Text("YID PLUS contact", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    val link = remember(me?.id) { qrLinkFor(me?.id ?: "", me?.nickname ?: "") }
                    val bmp = remember(link) { qrBitmap(link) }
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                        if (bmp != null) Image(bmp.asImageBitmap(), "My QR code", Modifier.size(240.dp))
                        else Text("Couldn't make the code", Modifier.size(240.dp).wrapContentSize(Alignment.Center), color = Color.Black)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Scan this code to start a chat with you", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.clip(RoundedCornerShape(24.dp)).background(ChatLook.accent).tap(RoundedCornerShape(24.dp)) { share(c, link) }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Share, null, tint = Color.White, modifier = Modifier.size(18.dp)); Text("  Share my code", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

/** CameraX preview that decodes QR codes with ZXing; calls [onFound] once with the first code it reads. */
@Composable
private fun QrScanner(onFound: (String) -> Unit) {
    val c = ctx()
    val perm = android.Manifest.permission.CAMERA
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(c, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(perm) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var done by remember { mutableStateOf(false) }
    if (!hasPerm) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Allow camera access to scan", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx ->
            val view = PreviewView(ctx)
            val providerF = ProcessCameraProvider.getInstance(ctx)
            providerF.addListener({
                val provider = providerF.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val reader = MultiFormatReader().apply { setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                    if (!done) runCatching {
                        val plane = image.planes[0]
                        val buf = plane.buffer
                        val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                        val rowStride = plane.rowStride.coerceAtLeast(image.width)
                        val src = PlanarYUVLuminanceSource(bytes, rowStride, image.height, 0, 0, image.width, image.height, false)
                        val res = reader.decodeWithState(BinaryBitmap(HybridBinarizer(src)))
                        if (res != null && !done) { done = true; onFound(res.text) }
                    }
                    reader.reset(); image.close()
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        }, modifier = Modifier.fillMaxSize())
        // A simple viewfinder frame.
        Box(Modifier.align(Alignment.Center).size(230.dp).clip(RoundedCornerShape(20.dp)).background(Color.Transparent))
        Text("Point at a YID PLUS QR code", Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .5f)).padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White)
    }
}
