package com.aerocell.stand.update

import com.aerocell.stand.kiosk.KioskPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class KioskConfigParserTest {
    private fun parse(config: String) = ContentManifestParser.parse(
        """{"contentVersion": 5, "files": [], "config": $config}"""
    ).config!!.kiosk

    @Test
    fun readsKioskPin() {
        val kiosk = parse("""{"kiosk": {"pin": "1470"}}""")
        assertEquals("1470", kiosk.pin)
    }

    @Test
    fun defaultsWhenMissing() {
        val kiosk = parse("""{"location": "sparta"}""")
        assertEquals(KioskPolicy.DEFAULT_PIN, kiosk.pin)
    }

    @Test
    fun blankPinFallsBackToDefault() {
        val kiosk = parse("""{"kiosk": {"pin": "  "}}""")
        assertEquals(KioskPolicy.DEFAULT_PIN, kiosk.pin)
    }
}
