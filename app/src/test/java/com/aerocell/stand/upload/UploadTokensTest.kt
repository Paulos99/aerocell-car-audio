package com.aerocell.stand.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadTokensTest {
    @Test
    fun generate_hasExpectedLengthAndAlphabet() {
        val token = UploadTokens.generate()
        assertTrue(UploadTokens.isWellFormed(token))
        assertEquals(UploadConfig.TOKEN_LENGTH, token.length)
    }

    @Test
    fun generate_isUnique() {
        val tokens = (1..40).map { UploadTokens.generate() }.toSet()
        assertEquals(40, tokens.size)
    }

    @Test
    fun isWellFormed_rejectsBadValues() {
        val good = UploadTokens.generate()
        assertFalse(UploadTokens.isWellFormed(""))
        assertFalse(UploadTokens.isWellFormed("short"))
        assertFalse(UploadTokens.isWellFormed(good + "x"))
        assertFalse(UploadTokens.isWellFormed(good.dropLast(1) + "-"))
        assertFalse(UploadTokens.isWellFormed(good.dropLast(1) + "/"))
        assertFalse(UploadTokens.isWellFormed("../" + good.drop(3)))
    }

    @Test
    fun matches_acceptsSameTokenOnly() {
        val token = UploadTokens.generate()
        assertTrue(UploadTokens.matches(token, token))
        assertFalse(UploadTokens.matches(token, UploadTokens.generate().also { assertNotEquals(token, it) }))
        assertFalse(UploadTokens.matches(token, token.lowercase().let { alt ->
            if (alt == token) token.uppercase() else alt
        }.let { changed ->
            if (changed == token) token.dropLast(1) + if (token.last() == 'A') 'B' else 'A' else changed
        }))
        assertFalse(UploadTokens.matches(token, "x".repeat(token.length)))
        assertFalse(UploadTokens.matches(token, token.drop(1)))
    }
}
