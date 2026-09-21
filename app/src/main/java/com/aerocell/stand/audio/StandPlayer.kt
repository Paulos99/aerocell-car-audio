package com.aerocell.stand.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import com.aerocell.stand.R
import kotlin.math.max

enum class StandTrack(
    val label: String,
    val subtitle: String,
    val rawRes: Int
) {
    Club("CLUB", "Electro House", R.raw.club),
    Rock("ROCK", "Hard Rock", R.raw.rock),
    Classical("CLASSIC", "Вивальди · Весна", R.raw.classical);

    companion object {
        fun previousOf(current: StandTrack): StandTrack {
            val all = entries
            val idx = all.indexOf(current)
            return all[(idx - 1 + all.size) % all.size]
        }
    }
}

class StandPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var pan: Float = 0f
    private var volume: Float = 0.85f

    var waveform: ByteArray = ByteArray(0)
        private set
    /** 0..1 mid-band energy from Visualizer FFT (~250 Hz–2.5 kHz). */
    @Volatile
    var midEnergy: Float = 0f
        private set

    val isPlaying: Boolean get() = player?.isPlaying == true
    val position: Int get() = player?.currentPosition ?: 0
    val duration: Int get() = player?.duration?.takeIf { it > 0 } ?: 0

    fun prepare(track: StandTrack, pan: Float, volume: Float, autoplay: Boolean) {
        releaseVisualizerOnly()
        player?.reset()
        player?.release()
        player = null
        waveform = ByteArray(0)
        midEnergy = 0f

        this.pan = pan.coerceIn(-1f, 1f)
        this.volume = volume.coerceIn(0f, 1f)
        val next = MediaPlayer.create(context, track.rawRes) ?: return
        player = next
        applyLevels()
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
        this.pan = pan.coerceIn(-1f, 1f)
        applyLevels()
    }

    fun applyVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyLevels()
    }

    fun release() {
        releaseVisualizerOnly()
        player?.reset()
        player?.release()
        player = null
        waveform = ByteArray(0)
        midEnergy = 0f
    }

    private fun applyLevels() {
        val vol = volume
        val clamped = pan
        val left = (if (clamped <= 0f) 1f else 1f - clamped) * vol
        val right = (if (clamped >= 0f) 1f else 1f + clamped) * vol
        player?.setVolume(left, right)
    }

    private fun releaseVisualizerOnly() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
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
                        ) {
                            if (fft == null || fft.size < 8) return
                            // FFT layout: [Re0, Im0, Re1, Im1, ...]
                            val n = fft.size / 2
                            val nyquist = samplingRate / 2f
                            val hzPerBin = if (n > 1) nyquist / (n - 1).toFloat() else 1f
                            var sum = 0.0
                            var count = 0
                            for (i in 1 until n) {
                                val hz = i * hzPerBin
                                if (hz < 250f || hz > 2500f) continue
                                val re = fft[i * 2].toInt()
                                val im = fft[i * 2 + 1].toInt()
                                sum += re * re + im * im
                                count++
                            }
                            if (count == 0) return
                            val rms = kotlin.math.sqrt(sum / count).toFloat()
                            // Visualizer magnitudes are small; scale into 0..1
                            midEnergy = (rms / 40f).coerceIn(0f, 1f)
                        }
                    },
                    max(Visualizer.getMaxCaptureRate() / 2, 10000),
                    true,
                    true
                )
                enabled = true
            }
        } catch (_: Throwable) {
            visualizer = null
        }
    }
}
