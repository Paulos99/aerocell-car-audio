package com.aerocell.stand.upload

import java.io.File

/**
 * How a visitor file reaches the stand.
 * Today this is a local HTTP page on the tablet's Wi-Fi;
 * a cloud implementation can replace it later without changing the player or the UI.
 */
interface VisitorUploadTransport {
    fun start(listener: Listener)
    fun stop()

    /** Phone-facing page for the current one-time token. */
    fun currentLink(token: String): UploadLink

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
