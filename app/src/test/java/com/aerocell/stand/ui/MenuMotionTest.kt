package com.aerocell.stand.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuMotionTest {
    private val full = MotionRect(0f, 0f, 2560f, 1600f)
    private val tile = MotionRect(80f, 280f, 1654f, 1520f)

    @Test
    fun lerpEndpoints() {
        assertEquals(tile, MenuMotion.lerp(tile, full, 0f))
        assertEquals(full, MenuMotion.lerp(tile, full, 1f))
        val mid = MenuMotion.lerp(tile, full, 0.5f)
        assertEquals(40f, mid.left, 0.001f)
        assertEquals(2107f, mid.right, 0.001f)
    }

    @Test
    fun coverScaleFillsRect() {
        val s = MenuMotion.coverScale(tile, full.width, full.height)
        assertTrue(full.width * s >= tile.width - 0.01f)
        assertTrue(full.height * s >= tile.height - 0.01f)
        assertEquals(1f, MenuMotion.coverScale(full, full.width, full.height), 0.0001f)
        assertEquals(1f, MenuMotion.coverScale(tile, 0f, 0f), 0.0001f)
    }

    @Test
    fun contentAlphaRisesEarlyAndClamps() {
        assertEquals(0f, MenuMotion.contentAlpha(0f), 0f)
        assertEquals(1f, MenuMotion.contentAlpha(0.5f), 0f)
        assertEquals(1f, MenuMotion.contentAlpha(1.2f), 0f)
    }

    @Test
    fun movingUntilTarget() {
        assertTrue(MenuMotion.isMoving(0.4f, 1f))
        assertFalse(MenuMotion.isMoving(1f, 1f))
        assertFalse(MenuMotion.isMoving(0.0005f, 0f))
    }

    @Test
    fun springSettlesInPlannedWindow() {
        // Пружина Compose: ω = sqrt(stiffness), время до 1 % ≈ 4.6 / (ζω).
        val omega = kotlin.math.sqrt(MenuMotion.STIFFNESS)
        val settleMs = 4.6f / (MenuMotion.DAMPING * omega) * 1000f
        assertTrue("settle $settleMs ms", settleMs in 250f..450f)
        assertTrue(MenuMotion.TILE_PRESSED_SCALE in 0.95f..0.97f)
    }
}
