package com.aerocell.stand.update

import org.junit.Assert.assertEquals
import org.junit.Test

class IdleConfigParserTest {
    private fun parse(config: String) = ContentManifestParser.parse(
        """{"contentVersion": 3, "files": [], "config": $config}"""
    ).config!!.idle

    @Test
    fun readsIdleSettings() {
        val idle = parse("""{"idle": {"timeoutSec": 30, "videoVolume": 0.25}}""")
        assertEquals(30, idle.timeoutSec)
        assertEquals(0.25f, idle.videoVolume, 0.0001f)
    }

    @Test
    fun defaultsWhenMissing() {
        val idle = parse("""{"location": "sparta"}""")
        assertEquals(IdleConfig.DEFAULT_TIMEOUT_SEC, idle.timeoutSec)
        assertEquals(IdleConfig.DEFAULT_VIDEO_VOLUME, idle.videoVolume, 0.0001f)
    }

    @Test
    fun clampsBadValues() {
        val idle = parse("""{"idle": {"timeoutSec": 2, "videoVolume": 7}}""")
        assertEquals(IdleConfig.MIN_TIMEOUT_SEC, idle.timeoutSec)
        assertEquals(1f, idle.videoVolume, 0.0001f)
    }

}
