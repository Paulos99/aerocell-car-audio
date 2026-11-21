package com.aerocell.stand.upload

import java.security.SecureRandom

object UploadTokens {
    private val random = SecureRandom()
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun generate(length: Int = UploadConfig.TOKEN_LENGTH): String {
        require(length >= 8) { "token too short" }
        val chars = CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars)
    }

    fun isWellFormed(token: String, length: Int = UploadConfig.TOKEN_LENGTH): Boolean {
        if (token.length != length) return false
        return token.all { it in ALPHABET }
    }

    fun matches(expected: String, actual: String): Boolean {
        if (expected.length != actual.length) return false
        if (!isWellFormed(expected) || !isWellFormed(actual)) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor actual[i].code)
        }
        return diff == 0
    }
}
