package com.aerocell.stand.viz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageFxMathTest {
    @Test
    fun shareIsLinearAndHalfInTheMiddle() {
        assertEquals(1f, BalanceShare.left(-1f), 1e-6f)
        assertEquals(0.5f, BalanceShare.left(0f), 1e-6f)
        assertEquals(0f, BalanceShare.left(1f), 1e-6f)
        assertEquals(0.5f, BalanceShare.right(0f), 1e-6f)
        assertEquals(0.75f, BalanceShare.left(-0.5f), 1e-6f)
        assertEquals(1f, BalanceShare.left(-0.3f) + BalanceShare.right(-0.3f), 1e-6f)
    }

    @Test
    fun smoothNeverOvershoots() {
        var x = 1f
        repeat(200) {
            x = Smooth.step(x, 1.06f, 1f / 60f, 0.1f)
            assertTrue(x <= 1.06f)
        }
        assertEquals(1.06f, x, 1e-3f)
    }

    @Test
    fun kickRespectsMinInterval() {
        val d = KickDetector(minIntervalMs = 120)
        d.feed(0.1f, 0)
        repeat(20) { d.feed(0.1f, 10L + it) }
        assertTrue(d.feed(1f, 100))
        assertFalse(d.feed(1.2f, 150))
        assertTrue(d.feed(1.5f, 230))
    }

    @Test
    fun adaptiveThresholdIgnoresSteadyLoudness() {
        val d = KickDetector()
        var kicks = 0
        var t = 0L
        repeat(200) {
            if (d.feed(0.8f, t)) kicks++
            t += 50
        }
        // Ровный громкий бас без ударов не должен срабатывать постоянно.
        assertTrue(kicks <= 1)
    }

    @Test
    fun atMostThreeStrongPerSecond() {
        val d = KickDetector(minIntervalMs = 120)
        var t = 0L
        repeat(10) { d.feed(0.05f, t); t += 50 }
        var strong = 0
        val start = t
        while (t < start + 1000) {
            d.feed(0.05f, t)
            t += 10
            if (d.feed(2f, t) && d.lastStrong) strong++
            t += 120
        }
        assertTrue("strong=$strong", strong <= 3)
    }

    @Test
    fun envelopeShape() {
        assertEquals(KickEnvelope.BASE, KickEnvelope.at(-1f, true), 1e-6f)
        assertEquals(1f, KickEnvelope.at(KickEnvelope.ATTACK, true), 1e-4f)
        assertTrue(KickEnvelope.at(0.2f, true) < 1f)
        assertTrue(KickEnvelope.at(0.2f, false) < KickEnvelope.at(0.2f, true))
        assertEquals(KickEnvelope.BASE, KickEnvelope.at(1f, true), 1e-6f)
    }

    @Test
    fun wavesOnlyWithEnoughLeftShare() {
        val w = Shockwaves()
        val out = FloatArray(16)
        w.onKick(0f, strong = true, kickPower = 1f, drive = 0.1f)
        assertEquals(0, w.pack(0.1f, 1f, out))
        w.onKick(0f, strong = false, kickPower = 1f, drive = 0.4f)
        assertEquals(0, w.pack(0.1f, 1f, out))
        w.onKick(0f, strong = true, kickPower = 1f, drive = 0.5f)
        assertEquals(1, w.pack(0.1f, 1f, out))
        assertTrue(out[0] > 200f)
    }

    @Test
    fun strongKickAtFullLeftGivesTwoWavesThatExpire() {
        val w = Shockwaves()
        val out = FloatArray(16)
        w.onKick(0f, strong = true, kickPower = 1f, drive = 1f)
        assertEquals(2, w.pack(0.2f, 1f, out))
        assertEquals(0, w.pack(2f, 1f, out))
        assertEquals(0f, out[0], 0f)
    }

    @Test
    fun poolNeverExceedsMax() {
        val w = Shockwaves()
        val out = FloatArray(16)
        for (k in 0 until 10) w.onKick(k * 0.01f, true, 1f, 1f)
        assertTrue(w.pack(0.2f, 1f, out) <= Shockwaves.MAX)
    }
}
