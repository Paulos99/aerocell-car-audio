package com.aerocell.stand.upload

sealed class UploadReject {
    data object BadToken : UploadReject()
    data object BadType : UploadReject()
    data object TooLarge : UploadReject()
    data object Empty : UploadReject()
}

sealed class UploadDecision {
    data class Accept(val filename: String, val extension: String) : UploadDecision()
    data class Reject(val reason: UploadReject) : UploadDecision()
}

object UploadValidation {
    fun extensionOf(filename: String): String? {
        val base = filename.substringAfterLast('/', filename).substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.lastIndex) return null
        return base.substring(dot + 1).lowercase()
    }

    fun displayName(filename: String): String {
        val base = filename.substringAfterLast('/', filename).substringAfterLast('\\').trim()
        return base.ifBlank { "трек" }
    }

    fun decide(tokenOk: Boolean, filename: String?, mime: String?, sizeBytes: Long): UploadDecision {
        if (!tokenOk) return UploadDecision.Reject(UploadReject.BadToken)
        if (sizeBytes < 0L) return UploadDecision.Reject(UploadReject.Empty)
        if (sizeBytes == 0L) return UploadDecision.Reject(UploadReject.Empty)
        if (sizeBytes > UploadConfig.MAX_BYTES) return UploadDecision.Reject(UploadReject.TooLarge)

        val name = filename?.trim().orEmpty()
        val ext = if (name.isNotEmpty()) extensionOf(name) else null
        val mimeNorm = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()

        val typeOk = when {
            ext != null -> ext in UploadConfig.ALLOWED_EXTENSIONS
            mimeNorm.isNotEmpty() && mimeNorm != "application/octet-stream" ->
                mimeNorm in UploadConfig.ALLOWED_MIME
            else -> false
        }
        if (!typeOk) return UploadDecision.Reject(UploadReject.BadType)

        val safeName = displayName(name.ifBlank { "track.${ext ?: mimeToExt(mimeNorm) ?: "mp3"}" })
        val safeExt = ext ?: extensionOf(safeName) ?: mimeToExt(mimeNorm) ?: "mp3"
        return UploadDecision.Accept(safeName, safeExt)
    }

    private fun mimeToExt(mime: String): String? = when (mime) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/aac" -> "aac"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/ogg", "audio/vorbis", "application/ogg" -> "ogg"
        else -> null
    }
}
