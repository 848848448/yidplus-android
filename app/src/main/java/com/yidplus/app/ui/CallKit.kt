package com.yidplus.app.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume

enum class CallPhase { IDLE, OUTGOING, INCOMING, ACTIVE, ENDED }

data class IncomingCall(val id: String, val kind: String, val callerId: String, val callerNick: String, val callerPhoto: String?, val offer: String)

/**
 * Native WebRTC engine for YID PLUS 1-on-1 voice/video calls. It uses the same backend the
 * website does (POST/GET /api/call, /api/turn-credentials), so a native call and a web call connect.
 * Signaling is relayed through /api/call and polled; media flows peer-to-peer (STUN + Cloudflare TURN).
 */
object CallKit {
    // ── Observable state for the call UI ──
    var phase by mutableStateOf(CallPhase.IDLE); private set
    var kind by mutableStateOf("video"); private set
    var peerName by mutableStateOf(""); private set
    var peerPhoto by mutableStateOf<String?>(null); private set
    var muted by mutableStateOf(false); private set
    var cameraOn by mutableStateOf(true); private set
    var speakerOn by mutableStateOf(true); private set
    var connected by mutableStateOf(false); private set
    var startedAt by mutableStateOf(0L); private set
    var statusLine by mutableStateOf(""); private set
    var incoming by mutableStateOf<IncomingCall?>(null); private set
    var localVideo by mutableStateOf<VideoTrack?>(null); private set
    var remoteVideo by mutableStateOf<VideoTrack?>(null); private set

    // ── Engine ──
    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var callId: String? = null
    private var isCaller = false
    private var since = 0L
    private var haveRemote = false
    private val pendingIce = ArrayList<IceCandidate>()
    private val outIce = ArrayList<IceCandidate>()
    private var iceServers: List<PeerConnection.IceServer> =
        listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    private var pollJob: Job? = null
    private var appCtx: Context? = null

    fun eglContext(): EglBase.Context? = egl?.eglBaseContext

    /** Shared with the group-call engine so both reuse one factory + EGL context. */
    internal fun sharedFactory(c: Context): PeerConnectionFactory = factory(c)

    @Synchronized
    private fun factory(c: Context): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(c.applicationContext).createInitializationOptions()
        )
        val e = EglBase.create(); egl = e
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f; return f
    }

    // ── Public: place a call ──
    fun startCall(c: Context, calleeId: String, name: String, photo: String?, video: Boolean) {
        // "Use less data for calls" (Settings → Calls): start as audio only even when the video-call
    // button was tapped, so no video is captured or sent.
    val startVideo = video && !LookPrefs.useLessDataForCalls
    if (phase != CallPhase.IDLE) return
        appCtx = c.applicationContext
        runCatching { factory(c) }   // creates the EGL context now, so the call UI can render video right away
        isCaller = true; kind = if (startVideo) "video" else "audio"; peerName = name; peerPhoto = photo
        cameraOn = startVideo; muted = false; connected = false; startedAt = 0; since = 0; haveRemote = false
        pendingIce.clear(); outIce.clear(); remoteVideo = null; localVideo = null
        phase = CallPhase.OUTGOING; statusLine = "Calling…"
        scope.launch {
            loadIce()
            val p = createPc(c) ?: return@launch fail("Couldn't start the call")
            addLocalMedia(c, startVideo)
            val offer = createLocal(p, offer = true) ?: return@launch fail("Couldn't create the call")
            val res = runCatching {
                Api.post("/api/call", JSONObject().put("action", "start").put("callee_id", calleeId).put("kind", kind).put("offer", sdpJson(offer)))
            }.getOrNull()
            val id = res?.optString("call_id").orEmpty()
            if (id.isBlank()) return@launch fail(res?.optString("error").orEmpty().ifBlank { "Couldn't reach them" })
            callId = id
            flushOutIce()
            startPolling(c)
        }
    }

    // ── Public: answer the ringing call ──
    fun acceptIncoming(c: Context) {
        val inc = incoming ?: return
        appCtx = c.applicationContext
        runCatching { factory(c) }   // EGL context ready before the call UI renders video
        isCaller = false; kind = inc.kind; peerName = inc.callerNick; peerPhoto = inc.callerPhoto
        cameraOn = inc.kind == "video"; muted = false; connected = false; since = 0; haveRemote = false
        pendingIce.clear(); outIce.clear(); remoteVideo = null; localVideo = null
        callId = inc.id; incoming = null; phase = CallPhase.ACTIVE; statusLine = "Connecting…"
        scope.launch {
            loadIce()
            val p = createPc(c) ?: return@launch fail("Couldn't answer")
            addLocalMedia(c, inc.kind == "video")
            val offer = runCatching { JSONObject(inc.offer) }.getOrNull() ?: return@launch fail("Couldn't answer")
            setRemote(p, SessionDescription(SessionDescription.Type.OFFER, offer.optString("sdp")))
            haveRemote = true; flushPendingIce()
            val answer = createLocal(p, offer = false) ?: return@launch fail("Couldn't answer")
            runCatching { Api.post("/api/call", JSONObject().put("action", "answer").put("call_id", inc.id).put("answer", sdpJson(answer))) }
            callId?.let { flushOutIce() }
            startedAt = System.currentTimeMillis()
            startPolling(c)
        }
    }

    fun declineIncoming() {
        val inc = incoming ?: return
        incoming = null
        scope.launch { runCatching { Api.post("/api/call", JSONObject().put("action", "decline").put("call_id", inc.id)) } }
    }

    fun hangUp() {
        val id = callId; val ph = phase
        if (id != null && (ph == CallPhase.OUTGOING || ph == CallPhase.ACTIVE)) {
            val action = if (ph == CallPhase.OUTGOING) "cancel" else "end"
            scope.launch { runCatching { Api.post("/api/call", JSONObject().put("action", action).put("call_id", id)) } }
        }
        teardown("Call ended")
    }

    fun toggleMute() { muted = !muted; localAudio?.setEnabled(!muted) }
    fun toggleCamera() { cameraOn = !cameraOn; localVideo?.setEnabled(cameraOn) }
    fun switchCamera() { (capturer as? CameraVideoCapturer)?.switchCamera(null) }
    fun toggleSpeaker(c: Context) {
        speakerOn = !speakerOn
        runCatching {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = speakerOn
        }
    }

    /** The app calls this on a timer while signed in, to catch an incoming call. */
    suspend fun checkIncoming(c: Context) {
        if (phase != CallPhase.IDLE || incoming != null) return
        appCtx = c.applicationContext
        val res = runCatching { Api.get("/api/call?incoming=1") }.getOrNull() ?: return
        val call = res.optJSONObject("call") ?: return
        val offer = call.optString("offer")
        if (offer.isBlank() || call.optString("id").isBlank()) return
        incoming = IncomingCall(
            call.optString("id"), call.optString("kind", "video").ifBlank { "video" }, call.optString("caller_id"),
            call.optString("caller_nick").ifBlank { "Someone" },
            Api.abs(call.optString("caller_photo").ifBlank { null }), offer
        )
    }

    // ── Media ──
    private fun addLocalMedia(c: Context, video: Boolean) {
        val f = factory(c)
        val src = f.createAudioSource(MediaConstraints()); audioSource = src
        val a = f.createAudioTrack("audio0", src); localAudio = a
        pc?.addTrack(a, listOf("stream0"))
        if (video) {
            val cap = buildCameraCapturer(c) ?: return
            capturer = cap
            val sh = SurfaceTextureHelper.create("CaptureThread", egl?.eglBaseContext); surfaceHelper = sh
            val vs = f.createVideoSource(false); videoSource = vs
            cap.initialize(sh, c.applicationContext, vs.capturerObserver)
            runCatching { cap.startCapture(1280, 720, 30) }
            val v = f.createVideoTrack("video0", vs); localVideo = v
            pc?.addTrack(v, listOf("stream0"))
        }
    }

    private fun buildCameraCapturer(c: Context): VideoCapturer? {
        val en = Camera2Enumerator(c)
        val names = en.deviceNames
        names.firstOrNull { en.isFrontFacing(it) }?.let { return en.createCapturer(it, null) }
        names.firstOrNull()?.let { return en.createCapturer(it, null) }
        return null
    }

    // ── Peer connection ──
    private fun createPc(c: Context): PeerConnection? {
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val p = factory(c).createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                if (s == PeerConnection.IceConnectionState.FAILED) scope.launch { renegotiate() }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(ic: IceCandidate?) {
                ic ?: return
                scope.launch {
                    if (callId != null) runCatching { Api.post("/api/call", JSONObject().put("action", "ice").put("call_id", callId).put("candidate", iceJson(ic))) }
                    else synchronized(outIce) { outIce.add(ic) }
                }
            }
            override fun onIceCandidatesRemoved(ics: Array<out IceCandidate>?) {}
            override fun onAddStream(ms: MediaStream?) {}
            override fun onRemoveStream(ms: MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val t = receiver?.track()
                if (t is VideoTrack) scope.launch { remoteVideo = t }
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState?) {
                scope.launch {
                    when (s) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            connected = true; if (startedAt == 0L) startedAt = System.currentTimeMillis(); statusLine = ""
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> statusLine = "Reconnecting…"
                        PeerConnection.PeerConnectionState.FAILED -> renegotiate()
                        PeerConnection.PeerConnectionState.CLOSED -> {}
                        else -> {}
                    }
                }
            }
        })
        pc = p
        return p
    }

    private fun renegotiate() {
        val p = pc ?: return; val id = callId ?: return
        if (!isCaller) return   // only the caller re-offers, to avoid glare
        scope.launch {
            val o = createLocal(p, offer = true, iceRestart = true) ?: return@launch
            runCatching { Api.post("/api/call", JSONObject().put("action", "renegotiate").put("call_id", id).put("offer", sdpJson(o))) }
        }
    }

    // ── Signal polling ──
    private fun startPolling(c: Context) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (phase == CallPhase.OUTGOING || phase == CallPhase.ACTIVE) {
                val res = runCatching { Api.get("/api/call?call_id=" + callId + "&since=" + since) }.getOrNull()
                if (res != null) {
                    val status = res.optString("status")
                    if (status == "ended" || status == "declined") { teardown(if (status == "declined") "Call declined" else "Call ended"); break }
                    val sigs = res.optJSONArray("signals")
                    if (sigs != null) for (i in 0 until sigs.length()) {
                        val s = sigs.optJSONObject(i) ?: continue
                        since = maxOf(since, s.optLong("id"))
                        handleSignal(s.optString("type"), runCatching { JSONObject(s.optString("data")) }.getOrNull() ?: continue)
                    }
                }
                delay(1200)
            }
        }
    }

    private fun handleSignal(type: String, data: JSONObject) {
        val p = pc ?: return
        when (type) {
            "answer" -> {
                setRemote(p, SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")))
                haveRemote = true; flushPendingIce()
                if (phase == CallPhase.OUTGOING) phase = CallPhase.ACTIVE
            }
            "ice" -> {
                val ic = IceCandidate(data.optString("sdpMid"), data.optInt("sdpMLineIndex"), data.optString("candidate"))
                if (haveRemote) p.addIceCandidate(ic) else synchronized(pendingIce) { pendingIce.add(ic) }
            }
            "reoffer" -> scope.launch {
                setRemote(p, SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")))
                val ans = createLocal(p, offer = false) ?: return@launch
                runCatching { Api.post("/api/call", JSONObject().put("action", "reanswer").put("call_id", callId).put("answer", sdpJson(ans))) }
            }
            "reanswer" -> setRemote(p, SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")))
        }
    }

    private fun flushPendingIce() {
        val p = pc ?: return
        synchronized(pendingIce) { pendingIce.forEach { runCatching { p.addIceCandidate(it) } }; pendingIce.clear() }
    }

    private fun flushOutIce() {
        val id = callId ?: return
        val list: List<IceCandidate>
        synchronized(outIce) { list = ArrayList(outIce); outIce.clear() }
        scope.launch { list.forEach { runCatching { Api.post("/api/call", JSONObject().put("action", "ice").put("call_id", id).put("candidate", iceJson(it))) } } }
    }

    // ── SDP coroutine bridges ──
    private suspend fun createLocal(p: PeerConnection, offer: Boolean, iceRestart: Boolean = false): SessionDescription? =
        suspendCancellableCoroutine { cont ->
            val constraints = MediaConstraints().apply {
                if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            val create = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    p.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() { if (cont.isActive) cont.resume(sdp) }
                        override fun onSetFailure(e: String?) { if (cont.isActive) cont.resume(null) }
                        override fun onCreateSuccess(d: SessionDescription?) {}
                        override fun onCreateFailure(e: String?) {}
                    }, sdp)
                }
                override fun onCreateFailure(e: String?) { if (cont.isActive) cont.resume(null) }
                override fun onSetSuccess() {}
                override fun onSetFailure(e: String?) {}
            }
            if (offer) p.createOffer(create, constraints) else p.createAnswer(create, constraints)
        }

    private fun setRemote(p: PeerConnection, sdp: SessionDescription) {
        p.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
            override fun onCreateSuccess(d: SessionDescription?) {}
            override fun onCreateFailure(e: String?) {}
        }, sdp)
    }

    // ── ICE helpers ──
    private suspend fun loadIce() {
        val res = runCatching { Api.get("/api/turn-credentials") }.getOrNull() ?: return
        val arr = res.optJSONArray("iceServers") ?: return
        val list = ArrayList<PeerConnection.IceServer>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val urls = ArrayList<String>()
            val u = s.opt("urls")
            if (u is String) urls.add(u)
            else s.optJSONArray("urls")?.let { for (j in 0 until it.length()) urls.add(it.optString(j)) }
            if (urls.isEmpty()) continue
            val b = PeerConnection.IceServer.builder(urls)
            s.optString("username").takeIf { it.isNotBlank() }?.let { b.setUsername(it) }
            s.optString("credential").takeIf { it.isNotBlank() }?.let { b.setPassword(it) }
            list.add(b.createIceServer())
        }
        if (list.isNotEmpty()) iceServers = list
    }

    private fun sdpJson(sdp: SessionDescription) = JSONObject().put("type", sdp.type.canonicalForm()).put("sdp", sdp.description)
    private fun iceJson(ic: IceCandidate) = JSONObject().put("candidate", ic.sdp).put("sdpMid", ic.sdpMid).put("sdpMLineIndex", ic.sdpMLineIndex)

    // ── Shutdown ──
    private fun fail(msg: String) { statusLine = msg; teardown(msg) }

    private fun teardown(reason: String) {
        pollJob?.cancel(); pollJob = null
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        pc = null; capturer = null; surfaceHelper = null; videoSource = null; audioSource = null
        localAudio = null; localVideo = null; remoteVideo = null
        callId = null; haveRemote = false; since = 0
        synchronized(pendingIce) { pendingIce.clear() }; synchronized(outIce) { outIce.clear() }
        connected = false; statusLine = reason; phase = CallPhase.IDLE
    }
}
