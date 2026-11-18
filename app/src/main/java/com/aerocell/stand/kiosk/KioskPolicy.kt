package com.aerocell.stand.kiosk

object KioskPolicy {
    fun shouldAutoInstall(
        isDeviceOwner: Boolean,
        updateAvailable: Boolean,
        apkBusy: Boolean,
        serviceMenuVisible: Boolean,
        playing: Boolean,
        idleActive: Boolean
    ): Boolean {
        return isDeviceOwner &&
            updateAvailable &&
            !apkBusy &&
            !serviceMenuVisible &&
            !playing &&
            idleActive
    }

    fun pinMatches(input: String, expected: String): Boolean {
        val want = expected.trim()
        if (want.isEmpty()) return false
        return input.trim() == want
    }

    fun resolvePin(configured: String?): String {
        val pin = configured?.trim().orEmpty()
        return pin.ifEmpty { DEFAULT_PIN }
    }

    const val DEFAULT_PIN = "2580"
}
