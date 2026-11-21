package com.aerocell.stand.upload

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates a cloud session, shows a public QR page, polls for a completed upload
 * and downloads the file with a presigned URL. No S3 keys live in the app.
 */
class CloudUploadTransport(
    private val tempDir: File,
    private val api: CloudUploadApi,
    private val pollIntervalMs: Long = 2_000L,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "aerocell-cloud-upload").apply { isDaemon = true }
    },
    private val autoSchedule: Boolean = true
) : VisitorUploadTransport {

    constructor(
        tempDir: File,
        functionBaseUrl: String,
        standSecret: String,
        pollIntervalMs: Long = 2_000L
    ) : this(
        tempDir = tempDir,
        api = CloudUploadApiClient(functionBaseUrl, standSecret),
        pollIntervalMs = pollIntervalMs
    )

    private var listener: VisitorUploadTransport.Listener? = null
    private var session: CloudSession? = null
    private var failed: Boolean = false
    private val modalOpen = AtomicBoolean(false)
    private val polling = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)
    private var future: ScheduledFuture<*>? = null

    override fun start(listener: VisitorUploadTransport.Listener) {
        this.listener = listener
        tempDir.mkdirs()
    }

    override fun stop() {
        stopPolling()
        modalOpen.set(false)
        val current = session
        session = null
        failed = false
        listener = null
        if (current != null) {
            // In tests autoSchedule is false and tick() is called by hand;
            // delete must also be synchronous so assertions can observe it.
            if (autoSchedule) {
                scheduler.execute {
                    runCatching { api.deleteSession(current.sessionId) }
                }
            } else {
                runCatching { api.deleteSession(current.sessionId) }
            }
        }
    }

    override fun currentLink(token: String): UploadLink {
        val existing = session
        if (existing != null && !failed) {
            return UploadLink(pageUrl = existing.pageUrl, available = true, failed = false)
        }
        return try {
            val created = api.createSession()
            session = created
            failed = false
            UploadLink(pageUrl = created.pageUrl, available = true, failed = false)
        } catch (_: Exception) {
            failed = true
            session = null
            UploadLink(pageUrl = null, available = false, failed = true)
        }
    }

    override fun onModalChanged(open: Boolean) {
        modalOpen.set(open)
        if (open) startPolling() else stopPolling()
    }

    private fun startPolling() {
        if (!polling.compareAndSet(false, true)) return
        if (!autoSchedule) return
        future = scheduler.scheduleWithFixedDelay(
            { runCatching { tick() } },
            pollIntervalMs,
            pollIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopPolling() {
        polling.set(false)
        future?.cancel(false)
        future = null
    }

    /** Visible for tests: one poll iteration. */
    fun tick() {
        if (!polling.get() && !modalOpen.get()) return
        val current = session ?: return
        if (downloading.get()) return
        val status = try {
            api.status(current.sessionId)
        } catch (_: Exception) {
            return
        }
        when (status.status) {
            "ready" -> accept(current, status)
            "expired", "missing" -> {
                session = null
                failed = status.status == "missing"
            }
        }
    }

    private fun accept(current: CloudSession, status: CloudStatus) {
        if (!downloading.compareAndSet(false, true)) return
        val listener = listener
        listener?.onUploadStarted()
        val ext = (status.extension ?: "mp3").lowercase()
        val name = status.filename ?: "трек.$ext"
        val dest = File(tempDir, "cloud-${current.sessionId}.$ext")
        try {
            tempDir.mkdirs()
            api.downloadTo(current.sessionId, dest)
            if (!dest.exists() || dest.length() <= 0L) {
                throw IOException("empty download")
            }
            session = null
            runCatching { api.deleteSession(current.sessionId) }
            listener?.onAccepted(dest, name, ext)
        } catch (_: Exception) {
            runCatching { dest.delete() }
        } finally {
            downloading.set(false)
            listener?.onUploadFinished()
        }
    }
}
