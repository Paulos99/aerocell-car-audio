package com.aerocell.stand.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val cfg = UpdateConfig()

    private fun input(
        ready: Boolean = true,
        origin: CheckOrigin = CheckOrigin.BACKGROUND,
        deviceOwner: Boolean = true,
        silentFailed: Boolean = false,
        installing: Boolean = false,
        playing: Boolean = false,
        visitorBusy: Boolean = false,
        serviceMenuOpen: Boolean = false,
        untouched: Boolean = false,
        idleActive: Boolean = false,
        sinceInteractionMs: Long = 0L,
        readyForMs: Long = 0L,
        minuteOfDay: Int = 12 * 60
    ) = UpdateInputs(ready, origin, deviceOwner, silentFailed, installing, playing, visitorBusy,
        serviceMenuOpen, untouched, idleActive, sinceInteractionMs, readyForMs, minuteOfDay)

    @Test
    fun notReadyDoesNothing() {
        assertEquals(UpdateDecision.NOTHING, UpdatePolicy.decide(input(ready = false), cfg))
        assertEquals(UpdateDecision.NOTHING, UpdatePolicy.decide(input(installing = true), cfg))
    }

    @Test
    fun startupUntouchedInstallsRightAway() {
        val d = UpdatePolicy.decide(input(origin = CheckOrigin.STARTUP, untouched = true), cfg)
        assertTrue(d.installSilentlyNow)
    }

    @Test
    fun idleTwoMinutesInstalls() {
        assertFalse(UpdatePolicy.decide(input(sinceInteractionMs = 119_000), cfg).installSilentlyNow)
        assertTrue(UpdatePolicy.decide(input(sinceInteractionMs = 120_000), cfg).installSilentlyNow)
    }

    @Test
    fun idleScreenInstallsRightAway() {
        // Ролики простоя уже идут: ждать ещё installAfterIdleSec не нужно.
        assertTrue(UpdatePolicy.decide(input(idleActive = true, sinceInteractionMs = 5_000), cfg).installSilentlyNow)
        // Но играющая музыка или открытое меню всё равно держат установку.
        assertFalse(UpdatePolicy.decide(input(idleActive = true, serviceMenuOpen = true), cfg).installSilentlyNow)
    }

    @Test
    fun nightInstallsWithoutQuietHours() {
        val d = UpdatePolicy.decide(input(sinceInteractionMs = 600_000, minuteOfDay = 3 * 60), cfg)
        assertTrue(d.installSilentlyNow)
    }

    @Test
    fun busyStandWaits() {
        val d = UpdatePolicy.decide(input(playing = true, sinceInteractionMs = 600_000), cfg)
        assertFalse(d.installSilentlyNow)
        assertEquals(UpdateButton.NONE, d.button)
        assertEquals("играет музыка", d.waitingReason)
    }

    @Test
    fun buttonAfterWaitingTooLong() {
        val d = UpdatePolicy.decide(input(playing = true, readyForMs = 30 * 60_000L), cfg)
        assertEquals(UpdateButton.SILENT, d.button)
    }

    @Test
    fun noDeviceOwnerShowsSystemButton() {
        assertEquals(UpdateButton.SYSTEM, UpdatePolicy.decide(input(deviceOwner = false), cfg).button)
        assertEquals(UpdateButton.SYSTEM, UpdatePolicy.decide(input(silentFailed = true), cfg).button)
    }

    @Test
    fun quietHoursRespected() {
        val night = cfg.copy(quietHours = QuietHours.parse("22:00-08:00"))
        assertFalse(UpdatePolicy.decide(input(sinceInteractionMs = 600_000, minuteOfDay = 12 * 60), night).installSilentlyNow)
        assertTrue(UpdatePolicy.decide(input(sinceInteractionMs = 600_000, minuteOfDay = 23 * 60), night).installSilentlyNow)
        assertTrue(UpdatePolicy.decide(input(sinceInteractionMs = 600_000, minuteOfDay = 3 * 60), night).installSilentlyNow)
    }

    @Test
    fun quietHoursParsing() {
        assertNull(QuietHours.parse("bad"))
        assertNull(QuietHours.parse(null))
        assertEquals("22:00-08:00", QuietHours.parse("22:00 - 08:00").toString())
    }

    @Test
    fun checkSchedule() {
        assertTrue(UpdatePolicy.isCheckDue(0L, null, cfg))
        assertFalse(UpdatePolicy.isCheckDue(119 * 60_000L, 0L, cfg))
        assertTrue(UpdatePolicy.isCheckDue(120 * 60_000L, 0L, cfg))
        assertEquals(15 * 60_000L, UpdatePolicy.checkIntervalMs(cfg.copy(checkIntervalMin = 1)))
    }

    @Test
    fun networkRestore() {
        assertTrue(UpdatePolicy.shouldCheckOnNetworkRestore(lastCheckFailed = true, checking = false))
        assertFalse(UpdatePolicy.shouldCheckOnNetworkRestore(lastCheckFailed = false, checking = false))
    }

    @Test
    fun restartOutcome() {
        assertEquals(RestartOutcome.NONE, UpdatePolicy.resumeAfterRestart(null, null, 8, 0, 1000))
        assertEquals(RestartOutcome.SHOW_DONE, UpdatePolicy.resumeAfterRestart(8, 0, 8, 10, 1000))
        assertEquals(RestartOutcome.KEEP_WAITING, UpdatePolicy.resumeAfterRestart(9, 0, 8, 10, 1000))
        assertEquals(RestartOutcome.EXPIRED, UpdatePolicy.resumeAfterRestart(9, 0, 8, 5000, 1000))
    }

    private fun parse(config: String) = ContentManifestParser.parse(
        """{"contentVersion": 3, "files": [], "config": $config}"""
    ).config!!.update

    @Test
    fun parsesUpdateConfig() {
        val d = parse("""{"location": "x"}""")
        assertEquals(120, d.checkIntervalMin)
        assertEquals(120, d.installAfterIdleSec)
        assertNull(d.quietHours)
        val c = parse("""{"update": {"checkIntervalMin": 5, "installAfterIdleSec": 300, "quietHours": "23:00-07:00", "buttonAfterMin": 45}}""")
        assertEquals(UpdateConfig.MIN_CHECK_INTERVAL_MIN, c.checkIntervalMin)
        assertEquals(300, c.installAfterIdleSec)
        assertEquals("23:00-07:00", c.quietHours.toString())
        assertEquals(45, c.buttonAfterMin)
    }
}
