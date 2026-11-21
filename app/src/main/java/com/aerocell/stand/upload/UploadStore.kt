package com.aerocell.stand.upload

import java.io.File

class UploadStore(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun clear() {
        val files = dir.listFiles() ?: return
        for (file in files) {
            runCatching { file.deleteRecursively() }
        }
    }

    fun save(tempFile: File, extension: String): File {
        dir.mkdirs()
        clear()
        val ext = extension.lowercase().ifBlank { "mp3" }
        val dest = File(dir, "custom.${ext}")
        if (dest.exists()) dest.delete()
        if (!tempFile.renameTo(dest)) {
            tempFile.copyTo(dest, overwrite = true)
            tempFile.delete()
        }
        return dest
    }
}
