package com.aerocell.stand.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRulesTest {
    @Test
    fun startsAndReturnsToMenu() {
        assertEquals(AppScreen.MENU, ScreenRules.START)
        assertEquals(AppScreen.MENU, ScreenRules.afterIdle())
    }

    @Test
    fun musicOnlyOnComparison() {
        assertTrue(ScreenRules.musicAllowed(AppScreen.COMPARISON))
        assertFalse(ScreenRules.musicAllowed(AppScreen.MENU))
    }

    @Test
    fun hintOnlyOnComparison() {
        assertTrue(ScreenRules.hintAllowed(AppScreen.COMPARISON, idle = false, webOpen = false))
        assertFalse(ScreenRules.hintAllowed(AppScreen.MENU, idle = false, webOpen = false))
        assertFalse(ScreenRules.hintAllowed(AppScreen.COMPARISON, idle = true, webOpen = false))
        assertFalse(ScreenRules.hintAllowed(AppScreen.COMPARISON, idle = false, webOpen = true))
    }

    @Test
    fun webOnlyFromMenu() {
        assertTrue(ScreenRules.webAllowed(AppScreen.MENU))
        assertFalse(ScreenRules.webAllowed(AppScreen.COMPARISON))
    }
}
