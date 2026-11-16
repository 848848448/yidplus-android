package com.yidplus.app.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yidplus.app.data.Api
import com.yidplus.app.data.Room
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
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume

/**
 * Native group call (mesh): everyone connects to everyone else through /api/group-call.
 * To avoid offer glare, the peer with the smaller user id makes the offer for each pair —
 * the same rule the website's group call uses, so native and web clients mesh together.
 */
object GroupCallKit {
    class Peer(val id: String, name: String, photo: String?) {
        var name by mutableStateOf(name)
        var photo by mutableStateOf(photo)
        var video by mutableStateOf<VideoTrack?>(null)
        var pc: PeerConnection? = null
        var haveRemote = false
        val pendingIce = ArrayList<IceCandidate>()
    }

    var active by mutableStateOf(false); private set
    var roomName by mutableStateOf(""); private set
    var kind by mutableStateOf("video"); private set
    var muted by mutableStateOf(false); private set
    var cameraOn by mutableStateOf(true); private set
    var speakerOn by mutableStateOf(true); private set
    var localVideo by mutableStateOf<VideoTrack?>(null); private set
    val peers = mutableStateListOf<Peer>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var me = ""
    private var roomId = ""
    private var since = 0L
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var iceServers: List<PeerConnection.IceServer> =
        listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    private var pollJob: Job? = null
    private var appCtx: Context? = null

    fun join(c: Context, room: Room, video: Boolean) {
        if (active) return
        appCtx = c.applicationContext
        runCatching { CallKit.sharedFactory(c) }
        roomId = room.id; roomName = room.name; kind = if (video) "video" else "audio"
        cameraOn = video; muted = false; speakerOn = true; since = 0; peers.clear(); localVideo = null
        active = true
        scope.launch {
            loadIce()
            makeLocalMedia(c, video)
            val res = runCatching { Api.post("/api/group-call", JSONObject().put("action", "join").put("room_id", roomId).put("kind", kind)) }.getOrNull()
            if (res == null || !res.optBoolean("ok", true)) { leave(); return@launch }
            me = res.optString("me")
            val parts = res.optJSONArray("participants")
            if (parts != null) for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                addPeer(p.optString("user_id"), p.optString("nickname").ifBlank { "Member" }, Api.abs(p.optString("photo_url").ifBlank { null }), connect = true)
            }
            startPolling(c)
        }
    }

    fun leave() {
        val id = roomId
        if (id.isNotBlank()) scope.launch { runCatching { Api.post("/api/group-call", JSONObject().put("action", "leave").put("room_id", id)) } }
        teardown()
    }

    fun toggleMute() { muted = !muted; localAudio?.setEnabled(!muted) }
    fun toggleCamera() { cameraOn = !cameraOn; localVideo?.setEnabled(cameraOn) }
    fun switchCamera() { (capturer as? CameraVideoCapturer)?.switchCamera(null) }
    fun toggleSpeaker(c: Context) {
        speakerOn = !speakerOn
        runCatching {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = speakerOn
        }
    }

    // ── Media (shared across every peer connection) ──
    private fun makeLocalMedia(c: Context, video: Boolean) {
        val f = CallKit.sharedFactory(c)
        val src = f.createAudioSource(MediaConstraints()); audioSource = src
        localAudio = f.createAudioTrack("gaudio", src)
        if (video) {
            val cap = buildCameraCapturer(c) ?: return
            capturer = cap
            val sh = SurfaceTextureHelper.create("GCapture", CallKit.eglContext()); surfaceHelper = sh
            val vs = f.createVideoSource(false); videoSource = vs
            cap.initialize(sh, c.applicationContext, vs.capturerObserver)
            runCatching { cap.startCapture(1280, 720, 30) }
            localVideo = f.createVideoTrack("gvideo", vs)
        }
    }

    private fun buildCameraCapturer(c: Context): VideoCapturer? {
        val en = Camera2Enumerator(c)
        val names = en.deviceNames
        names.firstOrNull { en.isFrontFacing(it) }?.let { return en.createCapturer(it, null) }
        names.firstOrNull()?.let { return en.createCapturer(it, null) }
        return null
    }

    // ── Peers ──
    private fun findPeer(id: String) = peers.firstOrNull { it.id == id }

    private fun addPeer(id: String, name: String, photo: String?, connect: Boolean): Peer {
        findPeer(id)?.let { if (name.isNotBlank()) it.name = name; if (photo != null) it.photo = photo; return it }
        val peer = Peer(id, name, photo)
        peers.add(peer)
        val c = appCtx ?: return peer
        val p = createPc(c, peer) ?: return peer
        peer.pc = p
        localAudio?.let { p.addTrack(it, listOf(me)) }
        localVideo?.let { p.addTrack(it, listOf(me)) }
        // The smaller id makes the offer, so each pair negotiates once.
        if (connect && me.isNotBlank() && me < id) scope.launch {
            val o = createLocal(p, offer = true) ?: return@launch
            runCatching { Api.post("/api/group-call", JSONObject().put("action", "signal").put("room_id", roomId).put("to_id", id).put("type", "offer").put("data", sdpJson(o))) }
        }
        return peer
    }

    private fun removePeer(id: String) {
        val peer = findPeer(id) ?: return
        runCatching { peer.pc?.close() }; runCatching { peer.pc?.dispose() }
        peers.remove(peer)
    }

    private fun createPc(c: Context, peer: Peer): PeerConnection? {
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        return CallKit.sharedFactory(c).createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(ic: IceCandidate?) {
                ic ?: return
                scope.launch { runCatching { Api.post("/api/group-call", JSONObject().put("action", "signal").put("room_id", roomId).put("to_id", peer.id).put("type", "ice").put("data", iceJson(ic))) } }
            }
            override fun onIceCandidatesRemoved(ics: Array<out IceCandidate>?) {}
            override fun onAddStream(ms: MediaStream?) {}
            override fun onRemoveStream(ms: MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val t = receiver?.track()
                if (t is VideoTrack) scope.launch { peer.video = t }
            }
        })
    }

    // ── Signal handling ──
    private fun handleSignal(fromId: String, type: String, data: JSONObject) {
        val c = appCtx ?: return
        val peer = findPeer(fromId) ?: addPeer(fromId, "Member", null, connect = false)
        val p = peer.pc ?: return
        when (type) {
            "offer" -> scope.launch {
                setRemote(p, SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")))
                peer.haveRemote = true; flushPeerIce(peer)
                val ans = createLocal(p, offer = false) ?: return@launch
                runCatching { Api.post("/api/group-call", JSONObject().put("action", "signal").put("room_id", roomId).put("to_id", fromId).put("type", "answer").put("data", sdpJson(ans))) }
            }
            "answer" -> { setRemote(p, SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp"))); peer.haveRemote = true; flushPeerIce(peer) }
            "ice" -> {
                val ic = IceCandidate(data.optString("sdpMid"), data.optInt("sdpMLineIndex"), data.optString("candidate"))
                if (peer.haveRemote) p.addIceCandidate(ic) else peer.pendingIce.add(ic)
            }
        }
    }

    private fun flushPeerIce(peer: Peer) {
        val p = peer.pc ?: return
        peer.pendingIce.forEach { runCatching { p.addIceCandidate(it) } }; peer.pendingIce.clear()
    }

    // ── Polling: roster + routed signals ──
    private fun startPolling(c: Context) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (active) {
                val res = runCatching { Api.get("/api/group-call?room_id=" + roomId + "&since=" + since) }.getOrNull()
                if (res != null) {
                    val parts = res.optJSONArray("participants")
                    if (parts != null) {
                        val live = HashSet<String>()
                        for (i in 0 until parts.length()) {
                            val pj = parts.optJSONObject(i) ?: continue
                            val id = pj.optString("user_id"); if (id.isBlank()) continue
                            live.add(id)
                            addPeer(id, pj.optString("nickname").ifBlank { "Member" }, Api.abs(pj.optString("photo_url").ifBlank { null }), connect = true)
                        }
                        peers.map { it.id }.filter { it !in live }.forEach { removePeer(it) }
                    }
                    val sigs = res.optJSONArray("signals")
                    if (sigs != null) for (i in 0 until sigs.length()) {
                        val s = sigs.optJSONObject(i) ?: continue
                        since = maxOf(since, s.optLong("id"))
                        handleSignal(s.optString("from_id"), s.optString("type"), runCatching { JSONObject(s.optString("data")) }.getOrNull() ?: continue)
                    }
                }
                delay(1500)
            }
        }
    }

    // ── SDP / ICE ──
    private suspend fun createLocal(p: PeerConnection, offer: Boolean): SessionDescription? =
        suspendCancellableCoroutine { cont ->
            val obs = object : SdpObserver {
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
            if (offer) p.createOffer(obs, MediaConstraints()) else p.createAnswer(obs, MediaConstraints())
        }

    private fun setRemote(p: PeerConnection, sdp: SessionDescription) {
        p.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
            override fun onCreateSuccess(d: SessionDescription?) {}
            override fun onCreateFailure(e: String?) {}
        }, sdp)
    }

    private suspend fun loadIce() {
        val res = runCatching { Api.get("/api/turn-credentials") }.getOrNull() ?: return
        val arr = res.optJSONArray("iceServers") ?: return
        val list = ArrayList<PeerConnection.IceServer>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val urls = ArrayList<String>()
            val u = s.opt("urls")
            if (u is String) urls.add(u) else s.optJSONArray("urls")?.let { for (j in 0 until it.length()) urls.add(it.optString(j)) }
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

    private fun teardown() {
        pollJob?.cancel(); pollJob = null
        peers.toList().forEach { runCatching { it.pc?.close() }; runCatching { it.pc?.dispose() } }
        peers.clear()
        runCatching { capturer?.stopCapture() }; runCatching { capturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }; runCatching { videoSource?.dispose() }; runCatching { audioSource?.dispose() }
        capturer = null; surfaceHelper = null; videoSource = null; audioSource = null; localAudio = null; localVideo = null
        roomId = ""; me = ""; since = 0; active = false
    }
}
