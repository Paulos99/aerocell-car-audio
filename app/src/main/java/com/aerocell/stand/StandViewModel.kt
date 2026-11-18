package com.aerocell.stand

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerocell.stand.audio.StandPlayer
import com.aerocell.stand.audio.StandTrack
import com.aerocell.stand.update.ApkInstaller
import com.aerocell.stand.update.ContentRepository
import com.aerocell.stand.update.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StandUiState(
    val track: StandTrack = StandTrack.Club,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val pan: Float = 0f,
    val volume: Float = 0.85f,
    val waveform: ByteArray = ByteArray(0),
    val midEnergy: Float = 0f,
    val updateAvailable: Boolean = false,
    val updateRequired: Boolean = false,
    val updateVersionName: String? = null,
    val contentStatusText: String? = null,
    val contentVersion: Int? = null,
    val cachedFiles: Int = 0,
    val checkingUpdates: Boolean = false,
    val apkProgress: Float? = null,
    val apkBusy: Boolean = false,
    val serviceMenuVisible: Boolean = false,
    val lastError: String? = null
)

class StandViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ContentRepository =
        (application as StandApplication).contentRepository
    private val installer = ApkInstaller(application)
    private val player = StandPlayer(application)
    private val _state = MutableStateFlow(StandUiState())
    val state: StateFlow<StandUiState> = _state
    private var ticker: Job? = null
    private var pendingInstall = false
    private var cacheBoundFor: StandTrack? = null

    init {
        val s = _state.value
        player.prepare(StandTrack.Club, pan = s.pan, volume = s.volume, autoplay = false, file = repository.cachedTrackFile(StandTrack.Club.genre))
        if (repository.cachedTrackFile(StandTrack.Club.genre) != null) {
            cacheBoundFor = StandTrack.Club
        }
        publish()
        ticker = viewModelScope.launch {
            while (isActive) {
                publish()
                delay(80)
            }
        }
        viewModelScope.launch {
            repository.state.collect { sync -> applySync(sync) }
        }
        viewModelScope.launch {
            repository.start(wifiOnly = true)
        }
    }

    fun selectTrack(track: StandTrack) {
        val s = _state.value
        val file = repository.cachedTrackFile(track.genre)
        player.prepare(track, s.pan, s.volume, autoplay = true, file = file)
        cacheBoundFor = if (file != null) track else null
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

    fun onLogoLongPress() {
        _state.update { it.copy(serviceMenuVisible = true) }
    }

    fun dismissServiceMenu() {
        _state.update { it.copy(serviceMenuVisible = false) }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            repository.refresh(wifiOnly = false, enqueueWorker = true)
        }
    }

    fun startApkUpdate() {
        viewModelScope.launch { performApkUpdate() }
    }

    fun onResume() {
        if (pendingInstall && installer.canInstall()) {
            pendingInstall = false
            repository.markNeedInstallPermission(false)
            viewModelScope.launch { performApkUpdate() }
        }
        maybeBindCachedTrack()
    }

    fun installPermissionIntent(): Intent? {
        return if (installer.canInstall()) null else installer.permissionSettingsIntent()
    }

    private suspend fun performApkUpdate() {
        val manifest = repository.loadCachedManifest()
        val apk = manifest?.apk ?: return
        if (apk.versionCode <= BuildConfig.VERSION_CODE) return
        if (!apk.applicationId.isNullOrBlank() && apk.applicationId != BuildConfig.APPLICATION_ID) return
        if (!installer.canInstall()) {
            pendingInstall = true
            repository.markNeedInstallPermission(true)
            return
        }
        try {
            val file = withContext(Dispatchers.IO) { repository.downloadApk(apk) }
            repository.markInstalling()
            installer.install(file)
        } catch (t: Throwable) {
            repository.reportError(t.message ?: "Не удалось установить обновление")
        }
    }

    private fun applySync(sync: SyncState) {
        _state.update {
            it.copy(
                updateAvailable = sync.updateAvailable,
                updateRequired = sync.updateRequired,
                updateVersionName = sync.updateVersionName,
                contentStatusText = when {
                    sync.apkDownloading -> sync.statusText
                    sync.downloading -> "Обновление контента…"
                    sync.checking -> sync.statusText
                    sync.updateAvailable -> it.contentStatusText
                    else -> sync.statusText
                },
                contentVersion = sync.contentVersion,
                cachedFiles = sync.cachedFiles,
                checkingUpdates = sync.checking,
                apkProgress = sync.apkProgress,
                apkBusy = sync.apkDownloading || sync.apkInstalling,
                lastError = sync.lastError
            )
        }
        maybeBindCachedTrack()
    }

    private fun maybeBindCachedTrack() {
        val current = _state.value.track
        if (_state.value.playing) return
        val file = repository.cachedTrackFile(current.genre) ?: return
        if (cacheBoundFor == current) return
        val s = _state.value
        player.prepare(current, s.pan, s.volume, autoplay = false, file = file)
        cacheBoundFor = current
        publish()
    }

    private fun publish() {
        _state.update {
            it.copy(
                playing = player.isPlaying,
                positionMs = player.position,
                durationMs = player.duration,
                waveform = player.waveform,
                midEnergy = player.midEnergy
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        player.release()
        super.onCleared()
    }
}
