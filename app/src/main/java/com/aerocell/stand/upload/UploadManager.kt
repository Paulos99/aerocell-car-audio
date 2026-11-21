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

    private val listener = object : VisitorUploadTransport.Listener {
        override fun onUploadStarted() {
            if (track != null) {
                lastActivityAt = SystemClock.elapsedRealtime()
            }
            main.removeCallbacks(clearStage)
            if (this@UploadManager.transport.reportsPhoneUpload) {
                // Облако: файл уже там, планшет начинает его скачивать.
                publish(uploading = true, stage = UploadStage.PREPARING, stageProgress = 0f)
            } else {
                publish(uploading = true, stage = UploadStage.UPLOADING, stageProgress = null)
            }
        }

        override fun onPhoneUploading(filename: String?, percent: Int?) {
            if (ui.stage == UploadStage.PREPARING || ui.stage == UploadStage.DONE) return
            main.removeCallbacks(clearStage)
            val next = percent?.coerceIn(0, 100)?.div(100f)
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
        }

        override fun onDownloadProgress(fraction: Float) {
            val next = fraction.coerceIn(0f, 1f)
            val prev = if (ui.stage == UploadStage.PREPARING) ui.stageProgress ?: 0f else 0f
            publish(uploading = true, stage = UploadStage.PREPARING, stageProgress = maxOf(prev, next))
        }

        override fun onUploadFailed() {
            publish(uploading = false, stage = UploadStage.FAILED, stageProgress = null)
            main.removeCallbacks(clearStage)
            main.postDelayed(clearStage, FAILED_HOLD_MS)
        }

        override fun onAccepted(tempFile: File, filename: String, extension: String) {
            handleAccepted(tempFile, filename, extension)
        }

        override fun onUploadFinished() {
            if (ui.uploading) publish(uploading = false)
        }
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
    }
}
