package com.aerocell.stand.upload

import java.io.File

/**
 * How a visitor file reaches the stand.
 * CloudUploadTransport is the default when a function URL is configured;
 * LocalHttpUploadTransport stays as a fallback.
 */
interface VisitorUploadTransport {
    fun start(listener: Listener)
    fun stop()

    /** Phone-facing page for the current one-time token. */
    fun currentLink(token: String): UploadLink

    /** Start or stop status polling. Default is a no-op (local HTTP does not poll). */
    fun onModalChanged(open: Boolean) {}

    interface Listener {
        fun onUploadStarted()
        fun onAccepted(tempFile: File, filename: String, extension: String)
        fun onUploadFinished()
    }
}

data class UploadLink(
    val pageUrl: String?,
    val available: Boolean,
    val failed: Boolean = false
)
