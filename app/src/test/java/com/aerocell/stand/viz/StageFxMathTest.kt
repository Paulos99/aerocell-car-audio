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

    /** Тихий фон низов 0.2 с лёгкой рябью, чтобы у статистики была дисперсия. */
    private fun warm(d: KickDetector, from: Long, frames: Int = 30): Long {
        var t = from
        repeat(frames) { d.feed(if (it % 2 == 0) 0.20f else 0.22f, t); t += 50 }
        return t
    }

    @Test
    fun kickRespectsMinInterval() {
        val d = KickDetector(minIntervalMs = 120)
        var t = warm(d, 0)
        assertTrue(d.feed(0.7f, t))
        d.feed(0.2f, t + 30)
        assertFalse(d.feed(0.8f, t + 60))
        d.feed(0.2f, t + 100)
        assertTrue(d.feed(0.9f, t + 200))
    }

    @Test
    fun steadyLoudBassDoesNotTrigger() {
        val d = KickDetector()
        var kicks = 0
        var t = 0L
        repeat(200) {
            if (d.feed(0.8f, t)) kicks++
            t += 50
        }
        assertEquals(0, kicks)
    }

    @Test
    fun quietLowEndWithSmallRiseIsIgnored() {
        // Как в классике: низы слабые, приросты ниже порога 0.12 и энергия ниже 0.15.
        val d = KickDetector()
        var kicks = 0
        var t = 0L
        repeat(600) {
            val e = 0.04f + 0.08f * ((it * 7) % 11) / 10f
            if (d.feed(e, t)) kicks++
            t += 50
        }
        assertEquals(0, kicks)
    }

    @Test
    fun regularKicksAreCaught() {
        // 128 BPM: удар каждые ~470 мс, кадры по 50 мс.
        val d = KickDetector()
        var t = warm(d, 0)
        var kicks = 0
        var next = t
        repeat(30 * 20) {
            val e = if (t >= next) { next += 470; 0.6f } else 0.2f
            if (d.feed(e, t)) kicks++
            t += 50
        }
        assertTrue("kicks=$kicks", kicks in 55..66)
    }

    @Test
    fun atMostThreeStrongPerSecond() {
        val d = KickDetector(minIntervalMs = 120)
        var t = warm(d, 0)
        var strong = 0
        val start = t
        while (t < start + 1000) {
            d.feed(0.2f, t)
            t += 10
            if (d.feed(1.5f, t) && d.lastStrong) strong++
            t += 120
        }
        assertTrue("strong=$strong", strong <= 3)
    }

    private fun sine(hz: Double, size: Int = 1024, rate: Int = 48000, amp: Double = 0.9): ByteArray =
        ByteArray(size) { n -> (128 + 127 * amp * kotlin.math.sin(2 * Math.PI * hz * n / rate)).toInt().coerceIn(0, 255).toByte() }

    @Test
    fun bassBandSeesLowsAndIgnoresMids() {
        val band = BassBand(1024, 48000)
        val low = band.energy(sine(60.0))
        val mid = band.energy(sine(600.0))
        val high = band.energy(sine(3000.0))
        assertTrue("low=$low", low > 0.25f)
        assertTrue("mid=$mid", mid < 0.02f)
        assertTrue("high=$high", high < 0.01f)
        assertEquals(0f, band.energy(ByteArray(1024) { 128.toByte() }), 1e-6f)
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
        // Посередине (доля L 0.5) и обычный удар даёт волну.
        w.onKick(0.05f, strong = false, kickPower = 1f, drive = 0.5f)
        assertEquals(2, w.pack(0.1f, 1f, out))
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
