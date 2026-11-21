package com.aerocell.stand.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadGateTest {
    private fun shown(d: UploadGate.Decision) = d is UploadGate.Decision.Show

    @Test
    fun qrStaysUntilPhoneReallyUploads() {
        val g = UploadGate()
        // Первый опрос сразу после QR — только база, даже если в сессии уже есть имя файла.
        assertFalse(shown(g.onWaiting(null, 0)))
        // Имя файла без процента (телефон открыл страницу) — всё ещё QR.
        assertFalse(shown(g.onWaiting(null, 2_000)))
        assertFalse(shown(g.onWaiting(0, 4_000)))
        assertFalse(g.showing)
        // Процент пошёл — показываем экран загрузки.
        val d = g.onWaiting(12, 6_000)
        assertTrue(shown(d))
        assertEquals(0.12f, (d as UploadGate.Decision.Show).fraction!!, 1e-6f)
    }

    @Test
    fun staleProgressFromAbandonedSessionIsIgnored() {
        val g = UploadGate()
        // Старая брошенная загрузка оставила 40 % в сессии.
        assertFalse(shown(g.onWaiting(40, 0)))
        assertFalse(shown(g.onWaiting(40, 2_000)))
        assertFalse(shown(g.onWaiting(40, 30_000)))
        // Новый реальный рост — показываем.
        assertTrue(shown(g.onWaiting(55, 32_000)))
    }

    @Test
    fun stallAfterSixtySecondsWithoutProgress() {
        val g = UploadGate(stallMs = 60_000)
        g.onWaiting(null, 0)
        assertTrue(shown(g.onWaiting(5, 1_000)))
        assertFalse(g.isStalled(30_000))
        // Прогресс сдвинулся — таймер заново.
        g.onWaiting(20, 50_000)
        assertFalse(g.isStalled(100_000))
        // Тот же процент не считается движением.
        g.onWaiting(20, 105_000)
        assertTrue(g.isStalled(110_000))
    }

    @Test
    fun downloadStartAlwaysShowsAndActivityKeepsItAlive() {
        val g = UploadGate(stallMs = 60_000)
        g.onDownloadStarted(1_000)
        assertTrue(g.showing)
        g.onActivity(50_000)
        assertFalse(g.isStalled(100_000))
        assertTrue(g.isStalled(111_000))
    }

    @Test
    fun resetReturnsToQr() {
        val g = UploadGate()
        g.onWaiting(null, 0)
        g.onWaiting(30, 2_000)
        assertTrue(g.showing)
        g.reset()
        assertFalse(g.showing)
        assertFalse(g.isStalled(1_000_000))
        // После сброса первый опрос снова только база.
        assertFalse(shown(g.onWaiting(70, 3_000)))
    }

    @Test
    fun notShownNeverStalls() {
        val g = UploadGate()
        g.onWaiting(null, 0)
        assertFalse(g.isStalled(10_000_000))
    }
}
