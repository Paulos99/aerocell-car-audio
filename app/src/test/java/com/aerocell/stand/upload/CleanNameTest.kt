package com.aerocell.stand.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanNameTest {
    @Test
    fun nullLikeNamesAreAbsent() {
        assertNull(cleanName(null))
        assertNull(cleanName(""))
        assertNull(cleanName("   "))
        assertNull(cleanName("null"))
        assertNull(cleanName("NULL"))
        assertNull(cleanName("undefined"))
    }

    @Test
    fun realNameKept() {
        assertEquals("Мой трек.mp3", cleanName("  Мой трек.mp3 "))
    }
}
