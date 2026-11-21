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

data class UploadUiState(
    val modalOpen: Boolean = false,
    val pageUrl: String? = null,
    val customTrack: UploadedTrack? = null,
    val customSelected: Boolean = false,
    val justUploaded: Boolean = false,
    val uploading: Boolean = false,
    val serverFailed: Boolean = false
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
            publish(uploading = true)
        }

        override fun onAccepted(tempFile: File, filename: String, extension: String) {
            handleAccepted(tempFile, filename, extension)
        }

        override fun onUploadFinished() {
            if (ui.uploading) publish(uploading = false)
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

    fun markPlaying(playing: Boolean) {
        if (playing && ui.customSelected && track != null) {
            lastActivityAt = SystemClock.elapsedRealtime()
        }
    }

    fun shouldExpireSession(now: Long = SystemClock.elapsedRealtime()): Boolean {
        val started = lastActivityAt
        if (track == null || started == 0L) return false
        return now - started >= UploadConfig.SESSION_TIMEOUT_MS
    }

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
            serverFailed = false
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
            customTrack = uploaded,
            customSelected = true,
            justUploaded = true,
            uploading = false,
            pageUrl = link.pageUrl,
            serverFailed = link.failed
        )
        main.post { onTrackReady(uploaded) }
    }

    private fun publish(
        modalOpen: Boolean = ui.modalOpen,
        pageUrl: String? = ui.pageUrl,
        customTrack: UploadedTrack? = track,
        customSelected: Boolean = ui.customSelected,
        justUploaded: Boolean = ui.justUploaded,
        uploading: Boolean = ui.uploading,
        serverFailed: Boolean = ui.serverFailed
    ) {
        val next = UploadUiState(
            modalOpen = modalOpen,
            pageUrl = pageUrl,
            customTrack = customTrack,
            customSelected = customSelected,
            justUploaded = justUploaded,
            uploading = uploading,
            serverFailed = serverFailed
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
}
