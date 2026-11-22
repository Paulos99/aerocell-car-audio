package com.aerocell.stand

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerocell.stand.audio.StandPlayer
import com.aerocell.stand.audio.StandTrack
import com.aerocell.stand.kiosk.KioskController
import com.aerocell.stand.onboarding.Onboarding
import com.aerocell.stand.onboarding.OnboardingState
import com.aerocell.stand.update.ApkInfo
import com.aerocell.stand.update.ApkInstaller
import com.aerocell.stand.update.CheckOrigin
import com.aerocell.stand.update.ContentRepository
import com.aerocell.stand.update.IdleConfig
import com.aerocell.stand.update.RestartOutcome
import com.aerocell.stand.update.Sha256
import com.aerocell.stand.update.SyncState
import com.aerocell.stand.update.UpdateButton
import com.aerocell.stand.update.UpdateFlagStore
import com.aerocell.stand.update.UpdateInputs
import com.aerocell.stand.update.UpdateLog
import com.aerocell.stand.update.UpdatePolicy
import com.aerocell.stand.upload.UploadManager
import com.aerocell.stand.upload.UploadUiState
import com.aerocell.stand.upload.UploadedTrack
import com.aerocell.stand.nav.AppScreen
import com.aerocell.stand.nav.ScreenRules
import com.aerocell.stand.web.LinksConfig
import com.aerocell.stand.web.WebTarget
import com.aerocell.stand.wifi.WifiController
import com.aerocell.stand.wifi.WifiNetwork
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
import java.util.Calendar

enum class UpdateScreenPhase { PREPARING, INSTALLING, DONE }

/** Полноэкранный экран «Обновляем стенд». */
data class UpdateScreenState(
    val phase: UpdateScreenPhase,
    val versionName: String,
    /** 0..1 — запись APK в сессию установки; null — без шкалы. */
    val progress: Float? = null
)

data class StandUiState(
    val track: StandTrack = StandTrack.Club,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val pan: Float = 0f,
    val volume: Float = 0.85f,
    val waveform: ByteArray = ByteArray(0),
    val midEnergy: Float = 0f,
    /** Интенсивность визуализатора 0, 50 или 100 %, настраивается в служебном меню. */
    val vizIntensity: Int = 100,
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
    val kioskLockTaskDesired: Boolean = false,
    /** Киоск на паузе только на время тихой установки; новая версия запрёт его снова. */
    val installLockPaused: Boolean = false,
    /** Плашка «Вышла версия … · Обновить» на главном экране. */
    val updateButton: UpdateButton = UpdateButton.NONE,
    /** Версия, которая скачана и проверена и ждёт установки. */
    val readyVersionName: String? = null,
    /** Нажали «Обновить», идёт установка с подтверждением системы. */
    val updateButtonBusy: Boolean = false,
    val updateScreen: UpdateScreenState? = null,
    /** Состояние обновления для служебного меню. */
    val updateStatus: String? = null,
    val lastCheckAtMs: Long? = null,
    val lastCheckOk: Boolean? = null,
    val nextCheckAtMs: Long? = null,
    val cloudVersionName: String? = null,
    val cloudVersionCode: Int? = null,
    val onboarding: OnboardingState = OnboardingState(),
    val wifi: WifiUiState = WifiUiState(),
    /** Главное меню или сравнение. Стартуем с меню, после простоя тоже возвращаемся в меню. */
    val screen: AppScreen = ScreenRules.START,
    /** Подписи и адреса плиток «Каталог» и «Калькулятор» из config.links. */
    val links: LinksConfig = LinksConfig(),
    /** Открытое окно с сайтом или null. Закрывается крестиком и при уходе в простой. */
    val web: WebPanelState? = null
)

/** Окно с сайтом поверх стенда. openId меняется при каждом открытии: так WebView создаётся заново. */
data class WebPanelState(
    val target: WebTarget,
    val title: String,
    val url: String,
    val allowedDomains: Set<String>,
    val openId: Long
)

data class WifiUiState(
    val open: Boolean = false,
    val scanning: Boolean = false,
    val networks: List<WifiNetwork> = emptyList(),
    val currentSsid: String? = null,
    val connectingTo: String? = null,
    val message: String? = null,
    val error: Boolean = false
)

/** APK новой версии, скачанный и проверенный по sha256. */
private data class ReadyApk(
    val apk: ApkInfo,
    val file: File,
    val origin: CheckOrigin,
    val readyAtElapsed: Long
)

class StandViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ContentRepository =
        (application as StandApplication).contentRepository
    private val installer = ApkInstaller(application)
    private val kiosk = KioskController(application)
    private val player = StandPlayer(application)
    private val vizPrefs = application.getSharedPreferences("viz", Context.MODE_PRIVATE)

    /** Уровень и удары бочки для визуализатора: UI читает их каждый кадр напрямую. */
    val audioFeed: com.aerocell.stand.audio.AudioFeed get() = player
    private val wifi = WifiController(application)
    private val _state = MutableStateFlow(
        StandUiState(
            kioskEnabled = kiosk.isDeviceOwner(),
            kioskLockTaskDesired = kiosk.isDeviceOwner()
        )
    )
    val state: StateFlow<StandUiState> = _state
    private val _upload = MutableStateFlow(UploadUiState())
    val upload: StateFlow<UploadUiState> = _upload
    private val uploads = UploadManager(
        context = application,
        onTrackReady = { playCustomFadingIn(it) },
        onSessionExpired = { onCustomSessionExpired() }
    )
    private var ticker: Job? = null
    private var resettingOwnTrack = false
    private var pendingInstall = false
    private var cacheBoundFor: StandTrack? = null
    @Volatile private var lastInteractionAt = SystemClock.elapsedRealtime()

    // --- обновления ---
    private val updateFlags = UpdateFlagStore(application)
    /** С запуска приложения экран никто не трогал: на старте можно ставить сразу. */
    @Volatile private var untouchedSinceStart = true
    private var readyApk: ReadyApk? = null
    private var downloadJob: Job? = null
    private var downloadError: String? = null
    @Volatile private var checking = false
    private var lastCheckFailed = false
    private var lastCheckAttemptElapsed: Long? = null
    /** Идёт установка; installingSilently — тихая или с подтверждением системы. */
    private var installing = false
    private var installingSilently = false
    private var installTimeout: Job? = null
    /** Сколько раз тихая установка версии уже падала и когда в последний раз. */
    private val silentFailures = mutableMapOf<Int, Int>()
    private var lastSilentFailAt = 0L
    private var lastLoggedReason: String? = null
    private var seenInstallFailures = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        uploads.onStateChanged = { _upload.value = it }
        _upload.value = uploads.ui
        val s = _state.value
        val clubFile = repository.cachedTrackFile(StandTrack.Club.genre)
        player.prepare(StandTrack.Club, pan = s.pan, volume = s.volume, autoplay = false, file = clubFile)
        if (clubFile != null) {
            cacheBoundFor = StandTrack.Club
        }
        refreshIdleContent()
        publish()
        viewModelScope.launch { idleWatchdog() }
        ticker = viewModelScope.launch {
            while (isActive) {
                // Пока свой трек играет, это считается активностью: сброс только после тишины без касаний.
                if (_upload.value.customSelected && player.isPlaying) uploads.touch()
                if (!resettingOwnTrack && uploads.shouldExpireSession()) {
                    resettingOwnTrack = true
                    viewModelScope.launch { resetOwnTrack() }
                }
                publish()
                delay(80)
            }
        }
        seenInstallFailures = repository.state.value.installFailures
        viewModelScope.launch {
            repository.state.collect { sync -> applySync(sync) }
        }
        _state.update {
            it.copy(
                onboarding = Onboarding.onStart(it.onboarding),
                vizIntensity = vizPrefs.getInt("intensity", 100)
            )
        }
        resumeUpdateScreenAfterRestart()
        viewModelScope.launch { updateScheduler() }
        registerNetworkCallback()
    }

    fun selectTrack(track: StandTrack) {
        uploads.markBuiltInSelected()
        val s = _state.value
        val file = repository.cachedTrackFile(track.genre)
        player.prepare(track, s.pan, s.volume, autoplay = ScreenRules.musicAllowed(s.screen), file = file)
        cacheBoundFor = if (file != null) track else null
        _state.update { it.copy(track = track) }
        publish()
    }

    fun selectCustomTrack() {
        val custom = uploads.selectCustom() ?: return
        playCustom(custom, autoplay = true)
    }

    fun openUpload() {
        uploads.openModal()
        onUserInteraction()
    }

    fun closeUpload() {
        uploads.closeModal()
        onUserInteraction()
    }

    /** «Закрыть» на экране загрузки своего трека. */
    fun cancelUpload() {
        uploads.cancelUpload()
        onUserInteraction()
    }

    /** Удаляет трек посетителя, когда стенд уходит в простой. */
    fun endVisitorSession() {
        uploads.closeModal()
        val wasCustom = _upload.value.customSelected
        uploads.clearUploaded(notify = false)
        if (wasCustom) {
            prepareBuiltIn(StandTrack.Club, autoplay = false)
        }
    }

    fun previousTrack() {
        if (_upload.value.customSelected) {
            selectTrack(StandTrack.entries.last())
            return
        }
        val current = _state.value.track
        val previous = StandTrack.previousOf(current)
        if (previous == StandTrack.entries.last() && _upload.value.customTrack != null) {
            selectCustomTrack()
        } else {
            selectTrack(previous)
        }
    }

    fun play() {
        uploads.touch()
        if (!ScreenRules.musicAllowed(_state.value.screen)) return
        player.play()
        publish()
    }

    fun pause() {
        uploads.touch()
        player.pause()
        publish()
    }

    fun stop() {
        uploads.touch()
        player.stop()
        publish()
    }

    fun seek(positionMs: Int) {
        uploads.touch()
        player.seek(positionMs)
        publish()
    }

    fun setPan(pan: Float) {
        uploads.touch()
        applyPan(pan)
    }

    fun setVolume(volume: Float) {
        uploads.touch()
        val next = volume.coerceIn(0f, 1f)
        player.applyVolume(next)
        _state.update { it.copy(volume = next) }
    }

    /** Любое касание экрана: сбрасывает таймер простоя. */
    fun onUserInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        uploads.touch()
        untouchedSinceStart = false
    }

    /** Кнопка «Показать режим простоя» в служебном меню. */
    fun showIdleNow() {
        _state.update { it.copy(serviceMenuVisible = false) }
        enterIdle()
    }

    /** Первое касание в режиме простоя: только будит стенд. */
    fun wakeFromIdle() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        untouchedSinceStart = false
        if (_state.value.idleActive) {
            _state.update { it.copy(idleActive = false, onboarding = Onboarding.onWake(it.onboarding)) }
        }
    }

    private fun enterIdle() {
        if (_state.value.idleActive) return
        endVisitorSession()
        // На экране простоя музыка стенда не играет.
        if (player.isPlaying) player.stop()
        refreshIdleContent()
        _state.update {
            it.copy(
                idleActive = true,
                onboarding = Onboarding.onEnterIdle(it.onboarding),
                wifi = WifiUiState(),
                // Окно с сайтом закрывается вместе с уходом в простой; куки и кэш чистит само окно.
                web = null,
                screen = ScreenRules.afterIdle(),
                // После работы персонала киоск снова запирается, когда стенд уходит в простой.
                kioskLockTaskDesired = if (it.kioskEnabled) true else it.kioskLockTaskDesired
            )
        }
        publish()
    }

    private fun visitorBusy(): Boolean {
        val upload = _upload.value
        return upload.modalOpen || upload.uploading
    }

    private suspend fun idleWatchdog() {
        while (true) {
            delay(1_000)
            val s = _state.value
            val now = SystemClock.elapsedRealtime()
            // Пока играет музыка, открыто меню или окно своего трека,
            // идёт загрузка файла или установка обновления, стенд не «простаивает».
            // Фоновое скачивание APK простою не мешает.
            if (player.isPlaying || s.serviceMenuVisible || installing || s.updateScreen != null || visitorBusy()) {
                lastInteractionAt = now
            } else if (Onboarding.blocksIdle(s.onboarding)) {
                // Помощь держит стенд, но сама закрывается после 2 минут без касаний.
                if (Onboarding.shouldAutoCloseHelp(s.onboarding, now - lastInteractionAt)) closeHelp()
            } else if (!s.idleActive && now - lastInteractionAt >= s.idleTimeoutSec * 1_000L) {
                enterIdle()
            }
            evaluateUpdate()
        }
    }

    private fun refreshIdleContent() {
        val config = repository.idleConfig()
        val videos = repository.cachedIdleVideos()
        _state.update {
            it.copy(
                idleVideos = if (it.idleVideos == videos) it.idleVideos else videos,
                idleTimeoutSec = config.timeoutSec,
                idleVideoVolume = config.videoVolume,
                links = repository.linksConfig()
            )
        }
    }

    // --- подсказка и помощь ---

    /** Любое касание во время подсказки: закрываем её, ползунок возвращаем в центр. */
    fun dismissHint() {
        val s = _state.value
        if (!s.onboarding.hintVisible) return
        _state.update { it.copy(onboarding = Onboarding.onTouch(it.onboarding)) }
        applyPan(0f)
    }

    /** Палец подсказки ведёт настоящий ползунок; если играет музыка, баланс реально меняется. */
    fun setPanFromHint(pan: Float) {
        if (!_state.value.onboarding.hintVisible) return
        applyPan(pan)
    }

    private fun applyPan(pan: Float) {
        val next = pan.coerceIn(-1f, 1f)
        player.applyPan(next)
        _state.update { it.copy(pan = next) }
    }

    fun openHelp() {
        onUserInteraction()
        _state.update { it.copy(onboarding = Onboarding.openHelp(it.onboarding)) }
    }

    fun closeHelp() {
        _state.update { it.copy(onboarding = Onboarding.closeHelp(it.onboarding)) }
    }

    // --- окно с сайтом STP и калькулятором ---

    private var webOpenSeq = 0L

    fun openWeb(target: WebTarget) {
        onUserInteraction()
        if (!ScreenRules.webAllowed(_state.value.screen)) return
        val links = repository.linksConfig()
        val link = links.link(target)
        webOpenSeq += 1
        _state.update {
            it.copy(
                links = links,
                onboarding = if (it.onboarding.helpOpen) Onboarding.closeHelp(it.onboarding) else it.onboarding,
                web = WebPanelState(
                    target = target,
                    title = link.label,
                    url = link.url,
                    allowedDomains = links.allowedDomains(),
                    openId = webOpenSeq
                )
            )
        }
    }

    fun closeWeb() {
        onUserInteraction()
        _state.update { it.copy(web = null) }
    }

    // --- главное меню ---

    private var backFadeJob: Job? = null

    /** Плитка «Сравнение». Если музыка ещё гаснет после «назад», возвращаем ей громкость. */
    fun openComparison() {
        onUserInteraction()
        backFadeJob?.let {
            it.cancel()
            backFadeJob = null
            player.applyVolume(_state.value.volume)
        }
        _state.update { it.copy(screen = AppScreen.COMPARISON, web = null) }
        publish()
    }

    /** Стрелка «назад»: музыка плавно гаснет, экран сворачивается в плитку меню. */
    fun backToMenu() {
        onUserInteraction()
        if (_state.value.screen == AppScreen.MENU) return
        uploads.closeModal()
        _state.update {
            it.copy(
                screen = AppScreen.MENU,
                onboarding = if (it.onboarding.helpOpen) Onboarding.closeHelp(it.onboarding) else it.onboarding
            )
        }
        if (player.isPlaying) {
            backFadeJob?.cancel()
            backFadeJob = viewModelScope.launch {
                fadeOutMusic()
                backFadeJob = null
            }
        }
        publish()
    }

    // --- Wi-Fi из служебного меню ---

    fun openWifi() {
        _state.update { it.copy(wifi = WifiUiState(open = true, currentSsid = wifi.currentSsid())) }
        scanWifi()
    }

    fun closeWifi() {
        _state.update { it.copy(wifi = WifiUiState()) }
    }

    fun scanWifi() {
        if (_state.value.wifi.scanning) return
        _state.update { it.copy(wifi = it.wifi.copy(scanning = true, message = null, error = false)) }
        viewModelScope.launch {
            try {
                val list = wifi.scan()
                _state.update {
                    it.copy(
                        wifi = it.wifi.copy(
                            scanning = false,
                            networks = list,
                            currentSsid = wifi.currentSsid(),
                            message = if (list.isEmpty()) "Сетей не нашлось. Обновите список" else null
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "wifi scan failed", t)
                _state.update {
                    it.copy(wifi = it.wifi.copy(scanning = false, message = "Список сетей недоступен: ${t.message}", error = true))
                }
            }
        }
    }

    fun connectWifi(network: WifiNetwork, password: String) {
        _state.update { it.copy(wifi = it.wifi.copy(connectingTo = network.ssid, message = "Подключаюсь к ${network.ssid}…", error = false)) }
        viewModelScope.launch {
            val error = wifi.connect(network, password)
            _state.update {
                it.copy(
                    wifi = it.wifi.copy(
                        connectingTo = null,
                        currentSsid = wifi.currentSsid(),
                        message = error ?: "Подключено к ${network.ssid}",
                        error = error != null
                    )
                )
            }
            if (error == null) runCheck(CheckOrigin.MANUAL)
        }
    }

    /** Запасной путь: системная панель Wi-Fi. В киоске на это время снимаем блокировку. */
    fun wifiSettingsIntent(): Intent {
        _state.update { it.copy(kioskLockTaskDesired = false, wifi = WifiUiState(), serviceMenuVisible = false) }
        return wifi.settingsIntent()
    }

    fun onLogoLongPress() {
        _state.update { it.copy(serviceMenuVisible = true) }
    }

    /** Ползунок интенсивности визуализатора в служебном меню: 0, 50 или 100 %. */
    fun setVizIntensity(percent: Int) {
        val v = when {
            percent <= 25 -> 0
            percent <= 75 -> 50
            else -> 100
        }
        vizPrefs.edit().putInt("intensity", v).apply()
        _state.update { it.copy(vizIntensity = v) }
    }

    fun dismissServiceMenu() {
        _state.update { it.copy(serviceMenuVisible = false, wifi = WifiUiState()) }
    }

    /** Запасной путь: «Проверить обновления» в служебном меню. Медиа тоже качаем по любой сети. */
    fun checkUpdates() {
        viewModelScope.launch { runCheck(CheckOrigin.MANUAL, mediaAnyNetwork = true) }
    }

    /** Кнопка «Обновить» на главном экране или в служебном меню. */
    fun startApkUpdate() {
        viewModelScope.launch {
            if (installing) {
                reportUpdateProblem("Обновление уже ставится")
                return@launch
            }
            val ready = readyApk ?: prefetchApk(CheckOrigin.MANUAL)
            if (ready == null) {
                reportUpdateProblem(
                    downloadError?.let { "Не удалось скачать обновление: $it" }
                        ?: "В облаке нет версии новее ${BuildConfig.VERSION_NAME}"
                )
                return@launch
            }
            // Ручное нажатие ставит сразу, не дожидаясь простоя.
            val silentPossible = kiosk.isDeviceOwner() &&
                (silentFailures[ready.apk.versionCode] ?: 0) < MAX_SILENT_ATTEMPTS
            if (silentPossible) installSilently(ready) else installWithSystem(ready)
        }
    }

    /** «Выйти из киоска» в служебном меню: блокировка снимается до следующего простоя. */
    fun exitKioskTemporarily(): Boolean {
        if (!kiosk.isDeviceOwner()) return false
        _state.update { it.copy(kioskLockTaskDesired = false, serviceMenuVisible = false) }
        return true
    }

    fun resumeKiosk() {
        if (!kiosk.isDeviceOwner()) return
        _state.update { it.copy(kioskEnabled = true, kioskLockTaskDesired = true) }
    }

    /** «Снять режим киоска» после подтверждения «Вы уверены?». */
    fun clearKiosk(): Boolean {
        if (!kiosk.isDeviceOwner()) return false
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
            readyApk?.let { ready -> viewModelScope.launch { installWithSystem(ready) } }
        }
        maybeBindCachedTrack()
        val owner = kiosk.isDeviceOwner()
        _state.update {
            it.copy(
                kioskEnabled = owner,
                kioskLockTaskDesired = if (owner) it.kioskLockTaskDesired else false
            )
        }
        uploads.refreshNetwork()
    }

    fun installPermissionIntent(): Intent? {
        if (kiosk.isDeviceOwner()) return null
        return if (installer.canInstall()) null else installer.permissionSettingsIntent()
    }

    // ---------------------------------------------------------------------
    // Обновления: проверка облака, скачивание заранее, установка без рывка
    // ---------------------------------------------------------------------

    /** Проверка при запуске, затем по таймеру из config.update.checkIntervalMin. */
    private suspend fun updateScheduler() {
        runCheck(CheckOrigin.STARTUP)
        while (true) {
            delay(SCHEDULER_TICK_MS)
            val due = UpdatePolicy.isCheckDue(
                nowMs = SystemClock.elapsedRealtime(),
                lastAttemptMs = lastCheckAttemptElapsed,
                config = repository.updateConfig()
            )
            if (due && !installing) runCheck(CheckOrigin.BACKGROUND)
        }
    }

    private suspend fun runCheck(origin: CheckOrigin, mediaAnyNetwork: Boolean = false) {
        if (checking) return
        checking = true
        lastCheckAttemptElapsed = SystemClock.elapsedRealtime()
        try {
            // Манифест и APK идут по любой сети; config и контент обновляются как раньше.
            val ok = repository.refresh(enqueueWorker = true, mediaAnyNetwork = mediaAnyNetwork)
            lastCheckFailed = !ok
            prefetchApk(origin)
        } finally {
            checking = false
            publishNextCheck()
        }
    }

    private fun publishNextCheck() {
        val last = repository.state.value.lastCheckAtMs
        val interval = UpdatePolicy.checkIntervalMs(repository.updateConfig())
        _state.update { it.copy(nextCheckAtMs = last?.plus(interval)) }
    }

    /**
     * Если в облаке версия новее, заранее качаем APK и сверяем sha256.
     * Возвращает готовый к установке APK или null.
     */
    private suspend fun prefetchApk(origin: CheckOrigin): ReadyApk? {
        val apk = repository.loadCachedManifest()?.apk
        if (apk == null ||
            apk.versionCode <= BuildConfig.VERSION_CODE ||
            (!apk.applicationId.isNullOrBlank() && apk.applicationId != BuildConfig.APPLICATION_ID)
        ) {
            readyApk = null
            _state.update { it.copy(readyVersionName = null, updateButton = UpdateButton.NONE) }
            return null
        }
        readyApk?.let { ready ->
            if (ready.apk.versionCode == apk.versionCode && ready.apk.sha256 == apk.sha256 && ready.file.isFile) {
                return ready
            }
        }
        downloadJob?.let { job ->
            if (job.isActive) {
                job.join()
                return readyApk?.takeIf { it.apk.versionCode == apk.versionCode }
            }
        }
        val job = viewModelScope.launch {
            try {
                downloadError = null
                val file = withContext(Dispatchers.IO) { repository.downloadApk(apk) }
                readyApk = ReadyApk(apk, file, origin, SystemClock.elapsedRealtime())
                _state.update { it.copy(readyVersionName = apk.versionName) }
                Log.i(TAG, "update ${apk.versionName} downloaded and verified")
            } catch (t: Throwable) {
                downloadError = t.message ?: "не удалось скачать"
                Log.e(TAG, "apk download failed", t)
                // Повторим при следующей проверке или когда вернётся сеть.
                lastCheckFailed = true
            }
        }
        downloadJob = job
        job.join()
        return readyApk?.takeIf { it.apk.versionCode == apk.versionCode }
    }

    /** Раз в секунду из idleWatchdog: решаем, ставить ли обновление и показывать ли кнопку. */
    private fun evaluateUpdate() {
        val s = _state.value
        val ready = readyApk
        val now = SystemClock.elapsedRealtime()
        val config = repository.updateConfig()
        val calendar = Calendar.getInstance()
        val failures = ready?.let { silentFailures[it.apk.versionCode] } ?: 0
        val decision = UpdatePolicy.decide(
            UpdateInputs(
                ready = ready != null,
                origin = ready?.origin ?: CheckOrigin.BACKGROUND,
                deviceOwner = kiosk.isDeviceOwner(),
                silentFailed = failures >= MAX_SILENT_ATTEMPTS,
                installing = installing,
                playing = player.isPlaying,
                visitorBusy = visitorBusy() || s.onboarding.helpOpen || s.web != null,
                serviceMenuOpen = s.serviceMenuVisible || s.wifi.open,
                untouchedSinceStart = untouchedSinceStart,
                idleActive = s.idleActive,
                sinceInteractionMs = now - lastInteractionAt,
                readyForMs = ready?.let { now - it.readyAtElapsed } ?: 0L,
                minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            ),
            config
        )
        val sync = repository.state.value
        // После упавшей попытки ждём RETRY_BACKOFF_MS, потом пробуем снова, до MAX_SILENT_ATTEMPTS раз.
        val backoffLeftMs = if (failures > 0) RETRY_BACKOFF_MS - (now - lastSilentFailAt) else 0L
        val reason = when {
            ready == null || installing -> null
            decision.installSilentlyNow && backoffLeftMs > 0 -> "повтор через ${(backoffLeftMs / 60_000L) + 1} мин"
            else -> decision.waitingReason
        }
        if (ready != null && reason != lastLoggedReason) {
            lastLoggedReason = reason
            UpdateLog.add("${ready.apk.versionName} ждёт установки: ${reason ?: "стенд свободен, ставлю"}")
        }
        val status = when {
            installing -> "ставится"
            ready != null && reason != null ->
                "${ready.apk.versionName} скачана, $reason"
            ready != null -> "${ready.apk.versionName} скачана"
            sync.apkDownloading -> "скачивается ${((sync.apkProgress ?: 0f) * 100).toInt()} %"
            downloadError != null -> "ошибка: $downloadError"
            sync.cloudVersionCode != null && sync.cloudVersionCode <= BuildConfig.VERSION_CODE -> "стоит актуальная версия"
            else -> "нет данных"
        }
        _state.update {
            it.copy(
                updateButton = if (it.updateButtonBusy) it.updateButton else decision.button,
                updateStatus = status
            )
        }
        if (decision.installSilentlyNow && ready != null && backoffLeftMs <= 0) {
            viewModelScope.launch { installSilently(ready) }
        }
    }

    /**
     * Тихая установка (приложение — владелец устройства): музыка плавно уходит в тишину,
     * экран «Обновляем стенд» проявляется за ~400 мс, ставим флаг и коммитим сессию.
     * После MY_PACKAGE_REPLACED новая версия поднимается с тем же экраном.
     */
    private suspend fun installSilently(ready: ReadyApk) {
        if (installing) return
        installing = true
        installingSilently = true
        _state.update {
            it.copy(
                serviceMenuVisible = false,
                updateButton = UpdateButton.NONE,
                updateScreen = UpdateScreenState(UpdateScreenPhase.PREPARING, ready.apk.versionName, 0f)
            )
        }
        uploads.closeModal()
        fadeOutMusic()
        delay(SCREEN_FADE_IN_MS)
        try {
            val intact = withContext(Dispatchers.IO) { Sha256.matches(ready.file, ready.apk.sha256) }
            if (!intact) {
                readyApk = null
                throw IllegalStateException("файл обновления повреждён, скачаю заново")
            }
            val attempt = (silentFailures[ready.apk.versionCode] ?: 0) + 1
            val note = kiosk.prepareForInstall()
            UpdateLog.add("ставлю ${ready.apk.versionName} тихо, попытка $attempt; $note")
            // Киоск на паузе до перезапуска: в режиме блокировки HyperOS отменяет установку.
            _state.update { it.copy(installLockPaused = true) }
            delay(LOCK_PAUSE_SETTLE_MS)
            updateFlags.set(ready.apk.versionCode, ready.apk.versionName, System.currentTimeMillis())
            repository.markInstalling()
            withContext(Dispatchers.IO) {
                installer.install(ready.file, silent = true) { p ->
                    _state.update { st ->
                        st.copy(updateScreen = st.updateScreen?.copy(progress = p))
                    }
                }
            }
            UpdateLog.add("сессия установки ${ready.apk.versionName} отправлена системе")
            _state.update {
                it.copy(updateScreen = it.updateScreen?.copy(phase = UpdateScreenPhase.INSTALLING, progress = 1f))
            }
            // Если система так и не перезапустила приложение, возвращаем рабочий экран.
            installTimeout = viewModelScope.launch {
                delay(UpdateFlagStore.TIMEOUT_MS)
                onInstallFailed("Обновление не завершилось за ${UpdateFlagStore.TIMEOUT_MS / 1000} с")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "silent install failed", t)
            UpdateLog.error("тихая установка", t)
            repository.reportInstallFailed(t.message ?: "Не удалось установить обновление")
            onInstallFailed(t.message ?: "Не удалось установить обновление")
        }
    }

    /** Установка с подтверждением системы: приложение не владелец устройства или тихая упала. */
    private suspend fun installWithSystem(ready: ReadyApk) {
        if (installing) return
        if (!installer.canInstall()) {
            pendingInstall = true
            repository.markNeedInstallPermission(true)
            reportUpdateProblem("Разрешите приложению установку обновлений, затем нажмите «Обновить» ещё раз")
            return
        }
        installing = true
        installingSilently = false
        // Окно подтверждения принадлежит другому пакету: в режиме блокировки киоска система его не покажет.
        _state.update { it.copy(updateButtonBusy = true, serviceMenuVisible = false, kioskLockTaskDesired = false) }
        fadeOutMusic()
        try {
            repository.markInstalling()
            withContext(Dispatchers.IO) { installer.install(ready.file, silent = false) }
            installTimeout = viewModelScope.launch {
                delay(UpdateFlagStore.TIMEOUT_MS)
                onInstallFailed("Установку не подтвердили")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "system install failed", t)
            repository.reportInstallFailed(t.message ?: "Не удалось установить обновление")
            onInstallFailed(t.message ?: "Не удалось установить обновление")
        }
    }

    /** Кнопка всегда либо ставит, либо объясняет, почему нет: текст в служебном меню и в логе. */
    private fun reportUpdateProblem(message: String) {
        Log.w(TAG, "update: $message")
        repository.reportError(message)
        _state.update { it.copy(lastError = message, updateStatus = message) }
    }

    private fun onInstallFailed(message: String) {
        if (!installing) return
        installTimeout?.cancel()
        installTimeout = null
        val wasSilent = installingSilently
        val flagCode = updateFlags.read()?.targetVersionCode
        installing = false
        installingSilently = false
        updateFlags.clear()
        repository.clearInstalling()
        if (wasSilent) {
            // Тихо не вышло: повторим позже, после MAX_SILENT_ATTEMPTS — кнопка с подтверждением.
            val code = readyApk?.apk?.versionCode ?: flagCode
            if (code != null) silentFailures[code] = (silentFailures[code] ?: 0) + 1
            lastSilentFailAt = SystemClock.elapsedRealtime()
            lastLoggedReason = null
        }
        UpdateLog.add("установка не удалась: $message")
        // Возвращаем киоск: MainActivity запрёт экран снова.
        _state.update { it.copy(installLockPaused = false) }
        Log.e(TAG, "install failed: $message")
        _state.update { it.copy(updateScreen = null, updateButtonBusy = false, lastError = message) }
    }

    /** Экран обновления после перезапуска: «Готово · версия X» или ожидание с таймаутом. */
    private fun resumeUpdateScreenAfterRestart() {
        val flag = updateFlags.read()
        val now = System.currentTimeMillis()
        when (
            UpdatePolicy.resumeAfterRestart(
                flagTargetVersionCode = flag?.targetVersionCode,
                flagStartedAtMs = flag?.startedAtMs,
                currentVersionCode = BuildConfig.VERSION_CODE,
                nowMs = now,
                timeoutMs = UpdateFlagStore.TIMEOUT_MS
            )
        ) {
            RestartOutcome.NONE -> Unit
            RestartOutcome.SHOW_DONE -> {
                UpdateLog.add("обновление до ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) установлено, стенд перезапущен")
                updateFlags.clear()
                _state.update {
                    it.copy(updateScreen = UpdateScreenState(UpdateScreenPhase.DONE, BuildConfig.VERSION_NAME, 1f))
                }
                viewModelScope.launch {
                    delay(DONE_HOLD_MS)
                    _state.update { it.copy(updateScreen = null) }
                }
            }
            RestartOutcome.KEEP_WAITING -> {
                val f = flag ?: return
                installing = true
                installingSilently = true
                _state.update {
                    it.copy(updateScreen = UpdateScreenState(UpdateScreenPhase.INSTALLING, f.targetVersionName, 1f))
                }
                installTimeout = viewModelScope.launch {
                    delay((f.startedAtMs + UpdateFlagStore.TIMEOUT_MS - now).coerceAtLeast(1_000L))
                    onInstallFailed("Обновление ${f.targetVersionName} не встало")
                }
            }
            RestartOutcome.EXPIRED -> {
                UpdateLog.add("прошлое обновление до ${flag?.targetVersionName} не встало")
                updateFlags.clear()
                repository.reportError("Прошлое обновление не встало")
            }
        }
    }

    /** Плавно уводим музыку в тишину перед экраном обновления. */
    private suspend fun fadeOutMusic() {
        if (!player.isPlaying) return
        val start = _state.value.volume
        val steps = 12
        for (i in 1..steps) {
            player.applyVolume(start * (1f - i / steps.toFloat()))
            delay(MUSIC_FADE_MS / steps)
        }
        player.stop()
        player.applyVolume(start)
        publish()
    }

    private fun registerNetworkCallback() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModelScope.launch {
                    // Сеть вернулась, а прошлая проверка упала: проверяем снова, не дожидаясь таймера.
                    delay(NETWORK_SETTLE_MS)
                    if (UpdatePolicy.shouldCheckOnNetworkRestore(lastCheckFailed, checking) && !installing) {
                        runCheck(CheckOrigin.BACKGROUND)
                    }
                }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (t: Throwable) {
            Log.e(TAG, "network callback failed", t)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {
        }
        networkCallback = null
    }

    private fun applySync(sync: SyncState) {
        uploads.resetAfterMs = repository.ownTrackConfig().resetAfterMs
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
                lastError = sync.lastError,
                lastCheckAtMs = sync.lastCheckAtMs,
                lastCheckOk = sync.lastCheckOk,
                cloudVersionName = sync.cloudVersionName,
                cloudVersionCode = sync.cloudVersionCode
            )
        }
        if (sync.installFailures != seenInstallFailures) {
            seenInstallFailures = sync.installFailures
            if (installing) onInstallFailed(sync.lastError ?: "Не удалось установить обновление")
        }
        refreshIdleContent()
        maybeBindCachedTrack()
    }

    private fun maybeBindCachedTrack() {
        if (_upload.value.customSelected) return
        val current = _state.value.track
        if (_state.value.playing) return
        val file = repository.cachedTrackFile(current.genre) ?: return
        if (cacheBoundFor == current) return
        val s = _state.value
        player.prepare(current, s.pan, s.volume, autoplay = false, file = file)
        cacheBoundFor = current
        publish()
    }

    private fun playCustom(track: UploadedTrack, autoplay: Boolean) {
        val s = _state.value
        val ok = player.prepareFromFile(track.file, s.pan, s.volume, autoplay)
        if (!ok) return
        cacheBoundFor = null
        uploads.selectCustom()
        publish()
    }

    /** После экрана загрузки музыка входит мягко, без щелчка. */
    private fun playCustomFadingIn(track: UploadedTrack) {
        // Трек догрузился, а посетитель уже ушёл в меню: готовим его без звука.
        if (!ScreenRules.musicAllowed(_state.value.screen)) {
            playCustom(track, autoplay = false)
            return
        }
        val target = _state.value.volume
        val ok = player.prepareFromFile(track.file, _state.value.pan, 0f, autoplay = true)
        if (!ok) return
        cacheBoundFor = null
        uploads.selectCustom()
        publish()
        viewModelScope.launch {
            val steps = 10
            for (i in 1..steps) {
                delay(CUSTOM_FADE_IN_MS / steps)
                if (_state.value.volume != target) break
                player.applyVolume(target * i / steps.toFloat())
            }
            player.applyVolume(_state.value.volume)
        }
    }

    /**
     * Сброс своего трека по неактивности: если трек играет, плавно гасим его,
     * потом удаляем файл и возвращаем кнопку «Свой трек» в исходное состояние.
     */
    private suspend fun resetOwnTrack() {
        try {
            if (_upload.value.customSelected && player.isPlaying) fadeOutMusic()
            // Пока гасили звук, кто-то мог коснуться экрана: тогда трек оставляем.
            if (!uploads.shouldExpireSession()) return
            val wasCustom = _upload.value.customSelected
            uploads.closeModal()
            uploads.clearUploaded(notify = false)
            if (wasCustom) prepareBuiltIn(StandTrack.Club, autoplay = false)
            Log.i(TAG, "own track reset after inactivity")
        } finally {
            resettingOwnTrack = false
        }
    }

    private fun onCustomSessionExpired() {
        prepareBuiltIn(StandTrack.Club, autoplay = false)
    }

    private fun prepareBuiltIn(track: StandTrack, autoplay: Boolean) {
        val s = _state.value
        val file = repository.cachedTrackFile(track.genre)
        player.prepare(track, s.pan, s.volume, autoplay = autoplay, file = file)
        cacheBoundFor = if (file != null) track else null
        _state.update { it.copy(track = track) }
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
        unregisterNetworkCallback()
        uploads.stop()
        player.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AerocellKiosk"
        private const val CUSTOM_FADE_IN_MS = 450L
        private const val SCHEDULER_TICK_MS = 30_000L
        private const val MAX_SILENT_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 5 * 60_000L
        private const val LOCK_PAUSE_SETTLE_MS = 600L
        private const val SCREEN_FADE_IN_MS = 450L
        private const val MUSIC_FADE_MS = 600L
        private const val DONE_HOLD_MS = 1_800L
        private const val NETWORK_SETTLE_MS = 3_000L
    }
}
