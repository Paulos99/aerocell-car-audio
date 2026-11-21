package com.aerocell.stand.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.SystemClock
import com.aerocell.stand.R
import android.util.Log
import com.aerocell.stand.viz.BassBand
import com.aerocell.stand.viz.KickDetector
import java.io.File
import kotlin.math.max

enum class StandTrack(
    val label: String,
    val subtitle: String,
    val rawRes: Int,
    val genre: String
) {
    Club("CLUB", "Electro House", R.raw.club, "CLUB"),
    Rock("ROCK", "Hard Rock", R.raw.rock, "ROCK"),
    Classical("CLASSIC", "Вивальди · Весна", R.raw.classical, "CLASSIC");

    companion object {
        fun previousOf(current: StandTrack): StandTrack {
            val all = entries
            val idx = all.indexOf(current)
            return all[(idx - 1 + all.size) % all.size]
        }
    }
}

/** То, что визуализатор читает каждый кадр. Поля пишет поток Visualizer, читает UI. */
interface AudioFeed {
    /** Общий уровень трека 0..1 (RMS волны, нормированный). */
    val level: Float
    /** Номер последнего удара бочки; меняется на каждом ударе. */
    val kickSeq: Int
    val kickStrong: Boolean
    val kickPower: Float
    /** Время последнего удара, SystemClock.uptimeMillis(). */
    val kickAtMs: Long
}

class StandPlayer(private val context: Context) : AudioFeed {
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

    @Volatile override var level: Float = 0f
        private set
    @Volatile override var kickSeq: Int = 0
        private set
    @Volatile override var kickStrong: Boolean = false
        private set
    @Volatile override var kickPower: Float = 0f
        private set
    @Volatile override var kickAtMs: Long = 0L
        private set
    private val kicks = KickDetector()

    val isPlaying: Boolean get() = player?.isPlaying == true
    val position: Int get() = player?.currentPosition ?: 0
    val duration: Int get() = player?.duration?.takeIf { it > 0 } ?: 0

    fun prepare(track: StandTrack, pan: Float, volume: Float, autoplay: Boolean, file: File? = null) {
        releaseVisualizerOnly()
        player?.reset()
        player?.release()
        player = null
        waveform = ByteArray(0)
        midEnergy = 0f

        this.pan = pan.coerceIn(-1f, 1f)
        this.volume = volume.coerceIn(0f, 1f)
        trackTag = if (file != null) "own" else track.label
        val next = createPlayer(track, file) ?: return
        player = next
        applyLevels()
        next.setOnCompletionListener {
            it.seekTo(0)
            it.pause()
        }
        attachVisualizer(next.audioSessionId)
        if (autoplay) next.start()
    }

    /** Plays a visitor-uploaded file. Returns false if the file cannot be decoded. */
    fun prepareFromFile(file: File, pan: Float, volume: Float, autoplay: Boolean): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        releaseVisualizerOnly()
        player?.reset()
        player?.release()
        player = null
        waveform = ByteArray(0)
        midEnergy = 0f
        this.pan = pan.coerceIn(-1f, 1f)
        this.volume = volume.coerceIn(0f, 1f)
        trackTag = "own"
        val next = try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
        } catch (_: Throwable) {
            return false
        }
        player = next
        applyLevels()
        next.setOnCompletionListener {
            it.seekTo(0)
            it.pause()
        }
        attachVisualizer(next.audioSessionId)
        if (autoplay) next.start()
        return true
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

    private fun createPlayer(track: StandTrack, file: File?): MediaPlayer? {
        if (file != null && file.isFile && file.length() > 0L) {
            try {
                return MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                }
            } catch (_: Throwable) {
                // fall back to the packaged track
            }
        }
        return MediaPlayer.create(context, track.rawRes)
    }

    private fun applyLevels() {
        val vol = volume
        val clamped = pan
        val left = (if (clamped <= 0f) 1f else 1f - clamped) * vol
        val right = (if (clamped >= 0f) 1f else 1f + clamped) * vol
        player?.setVolume(left, right)
    }

    private fun resetFeed() {
        level = 0f
        kicks.reset()
        statStartMs = 0L
    }

    private fun releaseVisualizerOnly() {
        resetFeed()
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
    }

    private fun attachVisualizer(sessionId: Int) {
        try {
            // Максимальный кадр (обычно 1024): так в окно попадает хотя бы период низкой ноты.
            val capture = Visualizer.getCaptureSizeRange()?.let { range -> range[1] } ?: 1024
            visualizer = Visualizer(sessionId).apply {
                captureSize = capture
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null || waveform.isEmpty()) return
                            // Для волны на экране — первые 512 отсчётов, как в 1.5.0 (тот же отрезок времени).
                            this@StandPlayer.waveform = waveform.copyOf(minOf(UI_WAVE, waveform.size))
                            var sum = 0.0
                            for (b in waveform) {
                                val v = ((b.toInt() and 0xFF) - 128) / 128.0
                                sum += v * v
                            }
                            val rms = kotlin.math.sqrt(sum / waveform.size)
                            level = (rms * 2.4).toFloat().coerceIn(0f, 1f)
                            onBass(waveform, samplingRate)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    // Максимальная частота (обычно 20 раз в секунду): удары ловятся точнее.
                    Visualizer.getMaxCaptureRate(),
                    true,
                    false
                )
                enabled = true
            }
        } catch (_: Throwable) {
            visualizer = null
        }
    }

    private var band: BassBand? = null
    private var statStartMs = 0L
    private var statKicks = 0
    private var statStrong = 0

    /** Низы 30–150 Гц → детектор удара. samplingRate приходит в миллигерцах. */
    private fun onBass(waveform: ByteArray, samplingRateMilliHz: Int) {
        val hz = (samplingRateMilliHz / 1000).takeIf { it in 8000..192000 } ?: 44100
        val b = band?.takeIf { it.size == waveform.size && it.sampleRateHz == hz }
            ?: BassBand(waveform.size, hz).also { band = it }
        val bass = b.energy(waveform)
        val now = SystemClock.uptimeMillis()
        if (player?.isPlaying != true) {
            statStartMs = 0L
            return
        }
        if (kicks.feed(bass, now)) {
            kickStrong = kicks.lastStrong
            kickPower = kicks.lastPower
            kickAtMs = now
            kickSeq++
            statKicks++
            if (kickStrong) statStrong++
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "kick power=%.2f strong=%b flux=%.3f bass=%.3f".format(kickPower, kickStrong, kicks.lastFlux, bass))
            }
        }
        if (statStartMs == 0L) {
            statStartMs = now
            statKicks = 0
            statStrong = 0
        } else if (now - statStartMs >= 30_000L) {
            Log.i(TAG, "30s track=$trackTag kicks=$statKicks strong=$statStrong pos=${position / 1000}s rate=${hz}Hz size=${waveform.size}")
            statStartMs = now
            statKicks = 0
            statStrong = 0
        }
    }

    private var trackTag: String = ""

    private companion object {
        const val TAG = "AeroKick"
        const val UI_WAVE = 512
    }
}
