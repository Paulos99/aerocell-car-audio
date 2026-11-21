package com.aerocell.stand.upload

import org.json.JSONObject

/** Настройки своего трека из config.ownTrack манифеста. */
data class OwnTrackConfig(
    val resetAfterMin: Int = DEFAULT_RESET_AFTER_MIN
) {
    val resetAfterMs: Long get() = resetAfterMin * 60_000L

    companion object {
        const val DEFAULT_RESET_AFTER_MIN = 10
        const val MIN_RESET_AFTER_MIN = 1
        const val MAX_RESET_AFTER_MIN = 24 * 60

        fun parse(obj: JSONObject?): OwnTrackConfig {
            if (obj == null) return OwnTrackConfig()
            val raw = obj.optInt("resetAfterMin", DEFAULT_RESET_AFTER_MIN)
            val value = raw.takeIf { it > 0 }
                ?.coerceIn(MIN_RESET_AFTER_MIN, MAX_RESET_AFTER_MIN)
                ?: DEFAULT_RESET_AFTER_MIN
            return OwnTrackConfig(resetAfterMin = value)
        }
    }
}

/**
 * Когда выгружать трек посетителя. Считаем только касания экрана:
 * если трек играет, а к стенду никто не подходит, он всё равно уйдёт,
 * чтобы следующий гость не услышал чужую музыку.
 */
object OwnTrackReset {
    fun shouldReset(hasTrack: Boolean, lastTouchAt: Long, now: Long, timeoutMs: Long): Boolean {
        if (!hasTrack || lastTouchAt <= 0L || timeoutMs <= 0L) return false
        return now - lastTouchAt >= timeoutMs
    }
}
