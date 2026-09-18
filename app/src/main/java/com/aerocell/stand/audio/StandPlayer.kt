package com.aerocell.stand.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import com.aerocell.stand.R
import kotlin.math.max

enum class StandTrack(
    val title: String,
    val subtitle: String,
    val rawRes: Int
) {
    Club("Клуб", "Electro House", R.raw.club),
    Classical("Классика", "Вивальди · Весна", R.raw.classical),
    Rock("Рок", "Hard Rock", R.raw.rock)
}

class StandPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    var waveform: ByteArray = ByteArray(0)
        private set

    val isPlaying: Boolean get() = player?.isPlaying == true
    val position: Int get() = player?.currentPosition ?: 0
    val duration: Int get() = player?.duration?.takeIf { it > 0 } ?: 0

    fun prepare(track: StandTrack, pan: Float, autoplay: Boolean) {
        release()
        val next = MediaPlayer.create(context, track.rawRes) ?: return
        player = next
        applyPan(pan)
        next.setOnCompletionListener {
            it.seekTo(0)
            it.pause()
        }
        attachVisualizer(next.audioSessionId)
        if (autoplay) next.start()
    }

    fun play() {
        val p = player ?: return
        if (!p.isPlaying) p.start()
        try {
            visualizer?.enabled = true
        } catch (_: Throwable) {
        }
    }

    fun pause() {
        val p = player ?: return
        if (p.isPlaying) p.pause()
    }

    fun stop() {
        val p = player ?: return
        if (p.isPlaying) p.pause()
        p.seekTo(0)
    }

    fun seek(positionMs: Int) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun applyPan(pan: Float) {
        val clamped = pan.coerceIn(-1f, 1f)
        val left = if (clamped <= 0f) 1f else 1f - clamped
        val right = if (clamped >= 0f) 1f else 1f + clamped
        player?.setVolume(left, right)
    }

    fun release() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        player?.reset()
        player?.release()
        player = null
        waveform = ByteArray(0)
    }

    private fun attachVisualizer(sessionId: Int) {
        try {
            val capture = Visualizer.getCaptureSizeRange()?.let { range ->
                512.coerceIn(range[0], range[1])
            } ?: 512
            visualizer = Visualizer(sessionId).apply {
                captureSize = capture
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform != null) {
                                this@StandPlayer.waveform = waveform.copyOf()
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    max(Visualizer.getMaxCaptureRate() / 2, 10000),
                    true,
                    false
                )
                enabled = true
            }
        } catch (_: Throwable) {
            visualizer = null
        }
    }
}
