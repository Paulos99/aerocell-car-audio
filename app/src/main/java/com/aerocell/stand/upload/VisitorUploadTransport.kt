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

    /** Забыть текущую сессию: следующий currentLink создаст новую (новый QR). */
    fun resetSession() {}

    /** true, если транспорт отдельно сообщает, что телефон уже грузит файл (облако). */
    val reportsPhoneUpload: Boolean get() = false

    interface Listener {
        fun onUploadStarted()
        fun onAccepted(tempFile: File, filename: String, extension: String)
        fun onUploadFinished()

        /** Телефон начал загрузку в облако; имя файла и процент, если облако их отдаёт. */
        fun onPhoneUploading(filename: String?, percent: Int?) {}

        /** Планшет скачивает готовый файл: настоящая доля 0..1. */
        fun onDownloadProgress(fraction: Float) {}

        /** Загрузка сорвалась. */
        fun onUploadFailed() {}
    }
}

data class UploadLink(
    val pageUrl: String?,
    val available: Boolean,
    val failed: Boolean = false
)
