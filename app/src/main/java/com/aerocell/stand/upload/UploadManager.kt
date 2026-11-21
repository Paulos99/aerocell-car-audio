package com.aerocell.stand.upload

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors

data class UploadedTrack(
    val file: File,
    val originalName: String
)

/** Этапы экрана загрузки своего трека на планшете. */
enum class UploadStage { NONE, UPLOADING, PREPARING, DONE, FAILED }

data class UploadUiState(
    val modalOpen: Boolean = false,
    val pageUrl: String? = null,
    val customTrack: UploadedTrack? = null,
    val customSelected: Boolean = false,
    val justUploaded: Boolean = false,
    val uploading: Boolean = false,
    val serverFailed: Boolean = false,
    val stage: UploadStage = UploadStage.NONE,
    /** Имя файла с телефона, как только облако его сообщило. */
    val stageTrackName: String? = null,
    /** Доля 0..1 внутри текущего этапа; null — неизвестно, рисуем бегущий блик. */
    val stageProgress: Float? = null
) {
    val uploadUrl: String? get() = pageUrl
    val busy: Boolean get() = modalOpen || uploading
}

class UploadManager(
    context: Context,
    private val onTrackReady: (UploadedTrack) -> Unit,
    private val onSessionExpired: () -> Unit = {},
    transport: VisitorUploadTransport? = null
) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aerocell-upload-io").apply { isDaemon = true }
    }
    private val store = UploadStore(File(context.filesDir, "uploads"))
    private val tempDir = File(context.cacheDir, "upload-tmp")
    private var token: String = UploadTokens.generate()
    private var lastActivityAt = 0L

    /** Через сколько без касаний выгружать трек посетителя (config.ownTrack.resetAfterMin). */
    @Volatile
    var resetAfterMs: Long = OwnTrackConfig().resetAfterMs
    private var track: UploadedTrack? = null
    private val transport: VisitorUploadTransport = transport ?: UploadTransports.create(
        tempDir = tempDir,
        currentToken = { token }
    )

    private val gate = UploadGate()

    /** После «Закрыть» на экране загрузки поздние ответы старой сессии игнорируем до нового QR. */
    @Volatile
    private var cancelled = false

    private fun now() = SystemClock.elapsedRealtime()

    private val listener = object : VisitorUploadTransport.Listener {
        override fun onUploadStarted() {
            if (cancelled) return
            if (track != null) {
                lastActivityAt = now()
            }
            main.removeCallbacks(clearStage)
            if (this@UploadManager.transport.reportsPhoneUpload) {
                // Облако: файл уже там, планшет начинает его скачивать.
                gate.onDownloadStarted(now())
                publish(uploading = true, stage = UploadStage.PREPARING, stageProgress = 0f)
            } else {
                gate.onDownloadStarted(now())
                publish(uploading = true, stage = UploadStage.UPLOADING, stageProgress = null)
            }
            startWatchdog()
        }

        override fun onPhoneUploading(filename: String?, percent: Int?) {
            if (cancelled) return
            if (ui.stage == UploadStage.PREPARING || ui.stage == UploadStage.DONE) return
            // QR не убираем, пока телефон по-настоящему не начал грузить (процент пошёл).
            val decision = gate.onWaiting(percent, now())
            if (decision !is UploadGate.Decision.Show) return
            main.removeCallbacks(clearStage)
            val next = decision.fraction
            val prev = if (ui.stage == UploadStage.UPLOADING) ui.stageProgress else null
            // Прогресс не идёт назад.
            val progress = when {
                next == null -> prev
                prev == null -> next
                else -> maxOf(prev, next)
            }
            publish(
                uploading = true,
                stage = UploadStage.UPLOADING,
                stageTrackName = filename ?: ui.stageTrackName,
                stageProgress = progress
            )
            startWatchdog()
        }

        override fun onDownloadProgress(fraction: Float) {
            if (cancelled) return
            val next = fraction.coerceIn(0f, 1f)
            val prev = if (ui.stage == UploadStage.PREPARING) ui.stageProgress ?: 0f else 0f
            if (next > prev) gate.onActivity(now())
            publish(uploading = true, stage = UploadStage.PREPARING, stageProgress = maxOf(prev, next))
        }

        override fun onUploadFailed() {
            if (cancelled) return
            failToQr()
        }

        override fun onAccepted(tempFile: File, filename: String, extension: String) {
            if (cancelled) {
                runCatching { tempFile.delete() }
                return
            }
            stopWatchdog()
            handleAccepted(tempFile, filename, extension)
        }

        override fun onUploadFinished() {
            if (ui.uploading && ui.stage != UploadStage.UPLOADING) publish(uploading = false)
        }
    }

    /** Раз в 5 с проверяем, не зависла ли загрузка. */
    private val watchdog = object : Runnable {
        override fun run() {
            val stage = ui.stage
            if (stage != UploadStage.UPLOADING && stage != UploadStage.PREPARING) return
            if (gate.isStalled(now())) {
                failToQr()
                return
            }
            main.postDelayed(this, WATCHDOG_MS)
        }
    }

    private fun startWatchdog() {
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun stopWatchdog() {
        main.removeCallbacks(watchdog)
    }

    /**
     * Ошибка или зависание: коротко показываем «не получилось», потом снова QR с новой сессией.
     * Старая сессия в облаке сбрасывается, чтобы её хвосты не открыли экран загрузки заново.
     */
    private fun failToQr() {
        stopWatchdog()
        gate.reset()
        runCatching { transport.resetSession() }
        token = UploadTokens.generate()
        publish(uploading = false, stage = UploadStage.FAILED, stageProgress = null, pageUrl = null)
        main.removeCallbacks(clearStage)
        main.postDelayed(clearStage, FAILED_HOLD_MS)
        refreshNetwork()
    }

    /** Кнопка «Закрыть» на экране загрузки: всегда возвращает на главный экран. */
    fun cancelUpload() {
        cancelled = true
        stopWatchdog()
        main.removeCallbacks(clearStage)
        gate.reset()
        transport.onModalChanged(false)
        runCatching { transport.resetSession() }
        token = UploadTokens.generate()
        publish(
            modalOpen = false,
            uploading = false,
            pageUrl = null,
            stage = UploadStage.NONE,
            stageTrackName = null,
            stageProgress = null
        )
        refreshNetwork()
    }

    private val clearStage = Runnable {
        if (ui.stage == UploadStage.DONE || ui.stage == UploadStage.FAILED) {
            publish(stage = UploadStage.NONE, stageTrackName = null, stageProgress = null)
        }
    }

    init {
        store.clear()
    }

    @Volatile
    var ui: UploadUiState = UploadUiState()
        private set

    var onStateChanged: ((UploadUiState) -> Unit)? = null

    fun startSession() {
        store.clear()
        tempDir.mkdirs()
        token = UploadTokens.generate()
        ensureTransport()
        refreshNetwork()
        lastActivityAt = 0L
        publish(modalOpen = ui.modalOpen, justUploaded = false, uploading = false)
    }

    fun ensureTransport() {
        transport.start(listener)
    }

    fun openModal() {
        cancelled = false
        gate.reset()
        val started = runCatching { ensureTransport() }.isSuccess
        touch()
        transport.onModalChanged(true)
        publish(
            modalOpen = true,
            pageUrl = if (started) ui.pageUrl else null,
            justUploaded = false,
            serverFailed = !started
        )
        if (started) refreshNetwork()
    }

    fun closeModal() {
        transport.onModalChanged(false)
        publish(modalOpen = false, justUploaded = false)
    }

    fun refreshNetwork() {
        io.execute {
            val link = runCatching { transport.currentLink(token) }
                .getOrElse { UploadLink(pageUrl = null, available = false, failed = true) }
            publish(pageUrl = link.pageUrl, serverFailed = link.failed)
        }
    }

    fun touch() {
        if (track != null) {
            lastActivityAt = SystemClock.elapsedRealtime()
        }
    }

    fun shouldExpireSession(now: Long = SystemClock.elapsedRealtime()): Boolean =
        OwnTrackReset.shouldReset(
            hasTrack = track != null,
            lastTouchAt = lastActivityAt,
            now = now,
            timeoutMs = resetAfterMs
        )

    fun expireSessionIfNeeded(): Boolean {
        if (!shouldExpireSession()) return false
        clearUploaded(notify = true)
        return true
    }

    fun clearUploaded(notify: Boolean = false) {
        val had = track != null
        stopWatchdog()
        gate.reset()
        store.clear()
        track = null
        token = UploadTokens.generate()
        lastActivityAt = 0L
        publish(
            customTrack = null,
            customSelected = false,
            justUploaded = false,
            uploading = false,
            pageUrl = null,
            serverFailed = false,
            stage = UploadStage.NONE,
            stageTrackName = null,
            stageProgress = null
        )
        refreshNetwork()
        if (had && notify) {
            main.post { onSessionExpired() }
        }
    }

    fun stop() {
        clearUploaded(notify = false)
        transport.stop()
    }

    fun selectCustom(): UploadedTrack? {
        val current = track ?: return null
        touch()
        publish(customSelected = true, modalOpen = ui.modalOpen)
        return current
    }

    fun markBuiltInSelected() {
        if (ui.customSelected) {
            publish(customSelected = false)
        }
        touch()
    }

    private fun handleAccepted(tempFile: File, filename: String, extension: String) {
        val saved = store.save(tempFile, extension)
        token = UploadTokens.generate()
        val uploaded = UploadedTrack(saved, filename)
        track = uploaded
        lastActivityAt = SystemClock.elapsedRealtime()
        val link = runCatching { transport.currentLink(token) }
            .getOrElse { UploadLink(pageUrl = null, available = false, failed = true) }
        publish(
            modalOpen = false,
            customTrack = uploaded,
            customSelected = true,
            justUploaded = true,
            uploading = false,
            pageUrl = link.pageUrl,
            serverFailed = link.failed,
            stage = UploadStage.DONE,
            stageTrackName = ui.stageTrackName ?: filename,
            stageProgress = 1f
        )
        transport.onModalChanged(false)
        // Галочка и плавный уход в чёрный, потом музыка.
        main.postDelayed({ if (track === uploaded) onTrackReady(uploaded) }, DONE_HOLD_MS)
        main.removeCallbacks(clearStage)
        main.postDelayed(clearStage, DONE_HOLD_MS)
    }

    private fun publish(
        modalOpen: Boolean = ui.modalOpen,
        pageUrl: String? = ui.pageUrl,
        customTrack: UploadedTrack? = track,
        customSelected: Boolean = ui.customSelected,
        justUploaded: Boolean = ui.justUploaded,
        uploading: Boolean = ui.uploading,
        serverFailed: Boolean = ui.serverFailed,
        stage: UploadStage = ui.stage,
        stageTrackName: String? = ui.stageTrackName,
        stageProgress: Float? = ui.stageProgress
    ) {
        val next = UploadUiState(
            modalOpen = modalOpen,
            pageUrl = pageUrl,
            customTrack = customTrack,
            customSelected = customSelected,
            justUploaded = justUploaded,
            uploading = uploading,
            serverFailed = serverFailed,
            stage = stage,
            stageTrackName = stageTrackName,
            stageProgress = stageProgress
        )
        ui = next
        val listener = onStateChanged
        if (listener != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                listener(next)
            } else {
                main.post { listener(next) }
            }
        }
    }

    companion object {
        const val DONE_HOLD_MS = 1_300L
        const val FAILED_HOLD_MS = 3_500L
        const val WATCHDOG_MS = 5_000L
    }
}
