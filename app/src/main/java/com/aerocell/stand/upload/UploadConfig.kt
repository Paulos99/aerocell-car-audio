package com.aerocell.stand.upload

object UploadConfig {
    const val PORT = 8733
    const val MAX_BYTES = 50L * 1024 * 1024
    const val TOKEN_LENGTH = 20
    val ALLOWED_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg")
    val ALLOWED_MIME = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/x-m4a",
        "audio/aac",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/flac",
        "audio/x-flac",
        "audio/ogg",
        "audio/vorbis",
        "application/ogg"
    )
}
