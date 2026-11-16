package com.yidplus.app.ui

import android.content.Context
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory

/**
 * Native WebRTC engine for YID PLUS voice/video calls. It talks to the same backend the
 * website uses (/api/call, /api/turn-credentials), so a native call and a web call interoperate.
 *
 * Phase 4a: the PeerConnectionFactory foundation and local audio. The signaling loop, peer
 * connection and call UI build on this in the next steps.
 */
object CallKit {
    @Volatile private var factory: PeerConnectionFactory? = null
    @Volatile private var egl: EglBase? = null

    /** The shared EGL context for rendering local/remote video (used by the call UI's renderers). */
    fun eglContext(): EglBase.Context? = egl?.eglBaseContext

    /** The one PeerConnectionFactory for the process, created on first use. */
    @Synchronized
    fun factory(c: Context): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(c.applicationContext)
                .createInitializationOptions()
        )
        val e = EglBase.create()
        egl = e
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    /** A microphone audio track for a call. */
    fun newAudioTrack(c: Context): AudioTrack {
        val f = factory(c)
        return f.createAudioTrack("audio0", f.createAudioSource(MediaConstraints()))
    }
}
