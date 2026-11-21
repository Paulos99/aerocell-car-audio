package com.aerocell.stand.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    @Test
    fun startShowsHintOnce() {
        val s = Onboarding.onStart(OnboardingState())
        assertTrue(s.hintVisible)
        val after = Onboarding.onTouch(s)
        assertEquals(StandMode.COMPARE, after.mode)
        assertTrue(after.hintSeen)
    }

    @Test
    fun idleResetsAndWakeShowsHintAgain() {
        val seen = OnboardingState(mode = StandMode.COMPARE, hintSeen = true, helpOpen = true)
        val idle = Onboarding.onEnterIdle(seen)
        assertEquals(StandMode.IDLE, idle.mode)
        assertFalse(idle.helpOpen)
        assertTrue(Onboarding.onWake(idle).hintVisible)
    }

    @Test
    fun helpBlocksIdleAndAutoCloses() {
        val open = Onboarding.openHelp(Onboarding.onStart(OnboardingState()))
        assertTrue(open.helpOpen)
        assertFalse(open.hintVisible)
        assertTrue(Onboarding.blocksIdle(open))
        assertFalse(Onboarding.shouldAutoCloseHelp(open, Onboarding.HELP_AUTO_CLOSE_MS - 1))
        assertTrue(Onboarding.shouldAutoCloseHelp(open, Onboarding.HELP_AUTO_CLOSE_MS))
        assertFalse(Onboarding.closeHelp(open).helpOpen)
    }

    @Test
    fun helpNotFromIdle() {
        val idle = Onboarding.onEnterIdle(OnboardingState())
        assertEquals(idle, Onboarding.openHelp(idle))
    }
}
