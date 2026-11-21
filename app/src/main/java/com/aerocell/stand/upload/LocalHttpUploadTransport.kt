package com.aerocell.stand.upload

import java.io.File
import java.io.IOException

/**
 * Serves the upload page on the tablet itself.
 * Visitors still need to reach this address on the local network.
 */
class LocalHttpUploadTransport(
    private val tempDir: File,
    private val currentToken: () -> String,
    private val findAddress: () -> String? = { LocalIpv4.findLanAddress() },
    private val preferredPort: Int = UploadConfig.PORT
) : VisitorUploadTransport {
    private var server: UploadServer? = null
    private var boundPort: Int = preferredPort
    private var failed: Boolean = false

    override fun start(listener: VisitorUploadTransport.Listener) {
        if (server != null) return
        tempDir.mkdirs()
        var lastError: IOException? = null
        for (port in preferredPort until preferredPort + 10) {
            val candidate = UploadServer(
                port = port,
                tempDir = tempDir,
                currentToken = currentToken,
                onStarted = { listener.onUploadStarted() },
                onAccepted = { file, name, ext -> listener.onAccepted(file, name, ext) },
                onFinished = { listener.onUploadFinished() }
            )
            try {
                candidate.start(SOCKET_TIMEOUT, false)
                server = candidate
                boundPort = port
                failed = false
                lastError = null
                break
            } catch (e: IOException) {
                lastError = e
                runCatching { candidate.stop() }
            }
        }
        if (server == null) {
            failed = true
            if (lastError != null) throw lastError
        }
    }

    override fun stop() {
        runCatching { server?.stop() }
        server = null
        failed = false
    }

    override fun currentLink(token: String): UploadLink {
        if (failed) return UploadLink(pageUrl = null, available = false, failed = true)
        val ip = findAddress() ?: return UploadLink(pageUrl = null, available = false)
        return UploadLink(
            pageUrl = "http://$ip:$boundPort/u/$token",
            available = server != null,
            failed = false
        )
    }

    companion object {
        private const val SOCKET_TIMEOUT = 15_000
    }
}
