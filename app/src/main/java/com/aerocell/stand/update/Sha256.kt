package com.aerocell.stand.update

import java.io.File
import java.security.MessageDigest

object Sha256 {
    fun of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun matches(file: File, expected: String): Boolean {
        if (!file.isFile || expected.isBlank()) return false
        return of(file).equals(expected.trim(), ignoreCase = true)
    }
}
