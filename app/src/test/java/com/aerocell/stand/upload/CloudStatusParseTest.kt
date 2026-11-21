package com.aerocell.stand.upload

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudStatusParseTest {
    @Test
    fun jsonNullAndEmptyNamesAreAbsent() {
        val body = JSONObject("""{"status":"waiting","filename":null,"extension":"","x":"null"}""")
        assertNull(optText(body, "filename"))
        assertNull(optText(body, "extension"))
        assertNull(optText(body, "x"))
        assertNull(optText(body, "missing"))
        assertEquals("song.mp3", optText(JSONObject("""{"filename":" song.mp3 "}"""), "filename"))
    }
}
