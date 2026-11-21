package com.aerocell.stand.upload

import com.aerocell.stand.update.ContentManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnTrackResetTest {
    private val tenMin = 10 * 60_000L

    @Test
    fun resetsAfterTenMinutesWithoutTouches() {
        assertFalse(OwnTrackReset.shouldReset(true, lastTouchAt = 1_000L, now = 1_000L + tenMin - 1, timeoutMs = tenMin))
        assertTrue(OwnTrackReset.shouldReset(true, lastTouchAt = 1_000L, now = 1_000L + tenMin, timeoutMs = tenMin))
    }

    @Test
    fun noTrackNothingToReset() {
        assertFalse(OwnTrackReset.shouldReset(false, lastTouchAt = 1_000L, now = 1_000L + 2 * tenMin, timeoutMs = tenMin))
    }

    @Test
    fun unknownTouchTimeDoesNotReset() {
        assertFalse(OwnTrackReset.shouldReset(true, lastTouchAt = 0L, now = 5 * tenMin, timeoutMs = tenMin))
    }

    @Test
    fun touchRestartsCountdown() {
        val lastTouch = 1_000L + 9 * 60_000L
        assertFalse(OwnTrackReset.shouldReset(true, lastTouch, now = 1_000L + tenMin + 1, timeoutMs = tenMin))
    }

    private fun parse(config: String) = ContentManifestParser.parse(
        """{"contentVersion": 3, "files": [], "config": $config}"""
    ).config!!.ownTrack

    @Test
    fun defaultIsTenMinutes() {
        assertEquals(10, parse("""{"location": "sparta"}""").resetAfterMin)
        assertEquals(tenMin, OwnTrackConfig().resetAfterMs)
    }

    @Test
    fun readsResetAfterMin() {
        assertEquals(15, parse("""{"ownTrack": {"resetAfterMin": 15}}""").resetAfterMin)
    }

    @Test
    fun badValuesFallBack() {
        assertEquals(10, parse("""{"ownTrack": {"resetAfterMin": 0}}""").resetAfterMin)
        assertEquals(10, parse("""{"ownTrack": {"resetAfterMin": -3}}""").resetAfterMin)
        assertEquals(OwnTrackConfig.MAX_RESET_AFTER_MIN, parse("""{"ownTrack": {"resetAfterMin": 999999}}""").resetAfterMin)
    }
}
