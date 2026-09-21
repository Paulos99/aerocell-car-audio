package com.aerocell.stand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerocell.stand.audio.StandPlayer
import com.aerocell.stand.audio.StandTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StandUiState(
    val track: StandTrack = StandTrack.Club,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val pan: Float = 0f,
    val volume: Float = 0.85f,
    val waveform: ByteArray = ByteArray(0)
)

class StandViewModel(application: Application) : AndroidViewModel(application) {
    private val player = StandPlayer(application)
    private val _state = MutableStateFlow(StandUiState())
    val state: StateFlow<StandUiState> = _state
    private var ticker: Job? = null

    init {
        val s = _state.value
        player.prepare(StandTrack.Club, pan = s.pan, volume = s.volume, autoplay = false)
        publish()
        ticker = viewModelScope.launch {
            while (isActive) {
                publish()
                delay(80)
            }
        }
    }

    fun selectTrack(track: StandTrack) {
        val s = _state.value
        player.prepare(track, s.pan, s.volume, autoplay = true)
        _state.update { it.copy(track = track) }
        publish()
    }

    fun previousTrack() {
        selectTrack(StandTrack.previousOf(_state.value.track))
    }

    fun play() {
        player.play()
        publish()
    }

    fun pause() {
        player.pause()
        publish()
    }

    fun stop() {
        player.stop()
        publish()
    }

    fun seek(positionMs: Int) {
        player.seek(positionMs)
        publish()
    }

    fun setPan(pan: Float) {
        val next = pan.coerceIn(-1f, 1f)
        player.applyPan(next)
        _state.update { it.copy(pan = next) }
    }

    fun setVolume(volume: Float) {
        val next = volume.coerceIn(0f, 1f)
        player.applyVolume(next)
        _state.update { it.copy(volume = next) }
    }

    private fun publish() {
        _state.update {
            it.copy(
                playing = player.isPlaying,
                positionMs = player.position,
                durationMs = player.duration,
                waveform = player.waveform
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        player.release()
        super.onCleared()
    }
}
