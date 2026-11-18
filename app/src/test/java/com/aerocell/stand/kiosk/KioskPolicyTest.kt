package com.aerocell.stand.kiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskPolicyTest {
    @Test
    fun autoInstallOnlyWhenOwnerIdleAndNotInDemo() {
        assertTrue(
            KioskPolicy.shouldAutoInstall(
                isDeviceOwner = true,
                updateAvailable = true,
                apkBusy = false,
                serviceMenuVisible = false,
                playing = false,
                idleActive = true
            )
        )
    }

    @Test
    fun autoInstallSkippedDuringDemo() {
        assertFalse(
            KioskPolicy.shouldAutoInstall(
                isDeviceOwner = true,
                updateAvailable = true,
                apkBusy = false,
                serviceMenuVisible = false,
                playing = true,
                idleActive = false
            )
        )
    }

    @Test
    fun autoInstallSkippedWithoutDeviceOwner() {
        assertFalse(
            KioskPolicy.shouldAutoInstall(
                isDeviceOwner = false,
                updateAvailable = true,
                apkBusy = false,
                serviceMenuVisible = false,
                playing = false,
                idleActive = true
            )
        )
    }

    @Test
    fun autoInstallSkippedWhenBusyOrMenuOpen() {
        assertFalse(
            KioskPolicy.shouldAutoInstall(
                isDeviceOwner = true,
                updateAvailable = true,
                apkBusy = true,
                serviceMenuVisible = false,
                playing = false,
                idleActive = true
            )
        )
        assertFalse(
            KioskPolicy.shouldAutoInstall(
                isDeviceOwner = true,
                updateAvailable = true,
                apkBusy = false,
                serviceMenuVisible = true,
                playing = false,
                idleActive = true
            )
        )
    }

    @Test
    fun pinMatchesTrimmedInput() {
        assertTrue(KioskPolicy.pinMatches(" 2580 ", "2580"))
        assertFalse(KioskPolicy.pinMatches("0000", "2580"))
        assertFalse(KioskPolicy.pinMatches("2580", "  "))
    }

    @Test
    fun resolvePinFallsBackWhenBlank() {
        assertEquals(KioskPolicy.DEFAULT_PIN, KioskPolicy.resolvePin(null))
        assertEquals(KioskPolicy.DEFAULT_PIN, KioskPolicy.resolvePin("   "))
        assertEquals("1470", KioskPolicy.resolvePin(" 1470 "))
    }
}
