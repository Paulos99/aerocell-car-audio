package com.aerocell.stand.upload

import com.aerocell.stand.BuildConfig
import java.io.File

object UploadTransports {
    fun create(tempDir: File, currentToken: () -> String): VisitorUploadTransport {
        val url = runCatching { BuildConfig.UPLOAD_FUNCTION_URL }.getOrDefault("")
        val secret = runCatching { BuildConfig.UPLOAD_STAND_SECRET }.getOrDefault("")
        val useCloud = runCatching { BuildConfig.UPLOAD_USE_CLOUD }.getOrDefault(false)
        return if (useCloud && url.isNotBlank() && secret.isNotBlank()) {
            CloudUploadTransport(
                tempDir = tempDir,
                functionBaseUrl = url,
                standSecret = secret
            )
        } else {
            LocalHttpUploadTransport(tempDir = tempDir, currentToken = currentToken)
        }
    }
}
