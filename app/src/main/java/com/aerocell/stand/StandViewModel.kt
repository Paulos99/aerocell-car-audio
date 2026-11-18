package com.aerocell.stand

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerocell.stand.audio.StandPlayer
import com.aerocell.stand.audio.StandTrack
import com.aerocell.stand.kiosk.KioskController
import com.aerocell.stand.kiosk.KioskPolicy
import com.aerocell.stand.update.ApkInstaller
import com.aerocell.stand.update.ContentRepository
import com.aerocell.stand.update.IdleConfig
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
import java.io.File

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
    val lastError: String? = null,
    val idleActive: Boolean = false,
    val idleVideos: List<File> = emptyList(),
    val idleTimeoutSec: Int = IdleConfig.DEFAULT_TIMEOUT_SEC,
    val idleVideoVolume: Float = IdleConfig.DEFAULT_VIDEO_VOLUME,
    val kioskEnabled: Boolean = false,
    val kioskLockTaskDesired: Boolean = false
)

class StandViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ContentRepository =
        (application as StandApplication).contentRepository
    private val installer = ApkInstaller(application)
    private val kiosk = KioskController(application)
    private val player = StandPlayer(application)
    private val _state = MutableStateFlow(
        StandUiState(
            kioskEnabled = kiosk.isDeviceOwner(),
            kioskLockTaskDesired = kiosk.isDeviceOwner()
        )
    )
    val state: StateFlow<StandUiState> = _state
    private var ticker: Job? = null
    private var pendingInstall = false
    private var cacheBoundFor: StandTrack? = null
    @Volatile private var lastInteractionAt = SystemClock.elapsedRealtime()
    private var autoInstallAttemptedFor: Int? = null

    init {
        val s = _state.value
        player.prepare(StandTrack.Club, pan = s.pan, volume = s.volume, autoplay = false, file = repository.cachedTrackFile(StandTrack.Club.genre))
        if (repository.cachedTrackFile(StandTrack.Club.genre) != null) {
            cacheBoundFor = StandTrack.Club
        }
        refreshIdleContent()
        publish()
        viewModelScope.launch { idleWatchdog() }
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

    /** Любое касание экрана: сбрасывает таймер простоя. */
    fun onUserInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    /** Кнопка «Показать режим простоя» в служебном меню. */
    fun showIdleNow() {
        _state.update { it.copy(serviceMenuVisible = false) }
        enterIdle()
    }

    /** Первое касание в режиме простоя: только будит стенд. */
    fun wakeFromIdle() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        if (_state.value.idleActive) {
            _state.update { it.copy(idleActive = false) }
        }
    }

    private fun enterIdle() {
        if (_state.value.idleActive) return
        // На экране простоя музыка стенда не играет.
        if (player.isPlaying) player.stop()
        refreshIdleContent()
        _state.update {
            it.copy(
                idleActive = true,
                // После работы персонала киоск снова запирается, когда стенд уходит в простой.
                kioskLockTaskDesired = if (it.kioskEnabled) true else it.kioskLockTaskDesired
            )
        }
        publish()
    }

    private suspend fun idleWatchdog() {
        while (true) {
            delay(1_000)
            val s = _state.value
            val now = SystemClock.elapsedRealtime()
            // Пока играет музыка, открыто меню или идёт обновление, стенд не «простаивает».
            if (player.isPlaying || s.serviceMenuVisible || s.apkBusy) {
                lastInteractionAt = now
                continue
            }
            if (!s.idleActive && now - lastInteractionAt >= s.idleTimeoutSec * 1_000L) {
                enterIdle()
            }
            maybeAutoInstall()
        }
    }

    private fun maybeAutoInstall() {
        val sync = repository.state.value
        val s = _state.value
        if (!KioskPolicy.shouldAutoInstall(
                isDeviceOwner = kiosk.isDeviceOwner(),
                updateAvailable = s.updateAvailable,
                apkBusy = s.apkBusy,
                serviceMenuVisible = s.serviceMenuVisible,
                playing = player.isPlaying,
                idleActive = s.idleActive
            )
        ) return
        val versionCode = sync.updateVersionCode ?: return
        if (autoInstallAttemptedFor == versionCode) return
        autoInstallAttemptedFor = versionCode
        viewModelScope.launch { performApkUpdate(silent = true) }
    }

    private fun refreshIdleContent() {
        val config = repository.idleConfig()
        val videos = repository.cachedIdleVideos()
        _state.update {
            it.copy(
                idleVideos = if (it.idleVideos == videos) it.idleVideos else videos,
                idleTimeoutSec = config.timeoutSec,
                idleVideoVolume = config.videoVolume
            )
        }
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
        viewModelScope.launch { performApkUpdate(silent = false) }
    }

    fun exitKioskTemporarily(pin: String): Boolean {
        if (!kiosk.isDeviceOwner()) return false
        if (!KioskPolicy.pinMatches(pin, repository.kioskConfig().pin)) return false
        _state.update { it.copy(kioskLockTaskDesired = false, serviceMenuVisible = false) }
        return true
    }

    fun resumeKiosk() {
        if (!kiosk.isDeviceOwner()) return
        _state.update { it.copy(kioskEnabled = true, kioskLockTaskDesired = true) }
    }

    fun clearKiosk(pin: String): Boolean {
        if (!kiosk.isDeviceOwner()) return false
        if (!KioskPolicy.pinMatches(pin, repository.kioskConfig().pin)) return false
        return try {
            kiosk.clearDeviceOwner()
            _state.update {
                it.copy(
                    kioskEnabled = false,
                    kioskLockTaskDesired = false,
                    serviceMenuVisible = false
                )
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "failed to clear device owner", t)
            repository.reportError(t.message ?: "Не удалось снять режим киоска")
            false
        }
    }

    fun onResume() {
        if (pendingInstall && installer.canInstall()) {
            pendingInstall = false
            repository.markNeedInstallPermission(false)
            viewModelScope.launch { performApkUpdate(silent = false) }
        }
        maybeBindCachedTrack()
        val owner = kiosk.isDeviceOwner()
        _state.update {
            it.copy(
                kioskEnabled = owner,
                kioskLockTaskDesired = if (owner) it.kioskLockTaskDesired else false
            )
        }
    }

    fun installPermissionIntent(): Intent? {
        if (kiosk.isDeviceOwner()) return null
        return if (installer.canInstall()) null else installer.permissionSettingsIntent()
    }

    private suspend fun performApkUpdate(silent: Boolean) {
        val manifest = repository.loadCachedManifest()
        val apk = manifest?.apk ?: return
        if (apk.versionCode <= BuildConfig.VERSION_CODE) return
        if (!apk.applicationId.isNullOrBlank() && apk.applicationId != BuildConfig.APPLICATION_ID) return
        val allowSilent = silent && kiosk.isDeviceOwner()
        if (!allowSilent && !installer.canInstall()) {
            pendingInstall = true
            repository.markNeedInstallPermission(true)
            return
        }
        try {
            val file = withContext(Dispatchers.IO) { repository.downloadApk(apk) }
            val s = _state.value
            if (allowSilent && (
                    player.isPlaying ||
                        s.serviceMenuVisible ||
                        !s.idleActive
                    )
            ) {
                // Скачали, но демонстрация уже началась — поставим, когда стенд снова простаивает.
                autoInstallAttemptedFor = null
                return
            }
            repository.markInstalling()
            installer.install(file, silent = allowSilent)
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
        refreshIdleContent()
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

    companion object {
        private const val TAG = "AerocellKiosk"
    }
}
