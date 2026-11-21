package com.aerocell.stand.upload

/**
 * Когда показывать экран загрузки своего трека. Чистая логика без Android, покрыта тестом.
 *
 * Правила:
 *  - пока телефон реально не начал грузить, на экране остаётся QR. Сессия в облаке может
 *    уже знать имя файла (телефон открыл страницу раньше и бросил), поэтому имя само по себе
 *    ничего не значит; первый опрос после открытия QR только запоминает базу;
 *  - старт загрузки = процент > 0 и больше базы, или файл уже готов и планшет его качает;
 *  - если прогресс не меняется [stallMs], загрузка считается зависшей: назад к QR с новой сессией.
 */
class UploadGate(private val stallMs: Long = STALL_MS) {
    companion object {
        const val STALL_MS = 60_000L
    }

    sealed interface Decision {
        /** Оставить QR. */
        data object KeepQr : Decision
        /** Показать экран загрузки с долей 0..1 (или null, если доли нет). */
        data class Show(val fraction: Float?) : Decision
    }

    private var baselineSet = false
    private var baseline: Int? = null
    private var lastPercent = -1
    private var lastChangeAt = 0L

    /** Экран загрузки сейчас показан. */
    var showing: Boolean = false
        private set

    /** Новый QR: всё с нуля. */
    @Synchronized
    fun reset() {
        baselineSet = false
        baseline = null
        lastPercent = -1
        lastChangeAt = 0L
        showing = false
    }

    /** Ответ status «waiting» (с именем файла или без) и процент из облака. */
    @Synchronized
    fun onWaiting(percent: Int?, nowMs: Long): Decision {
        val p = percent?.takeIf { it in 0..100 }
        if (!baselineSet) {
            baselineSet = true
            baseline = p
            return if (showing) Decision.Show(lastPercent.takeIf { it >= 0 }?.div(100f)) else Decision.KeepQr
        }
        val base = baseline
        val started = p != null && p > 0 && (base == null || p > base)
        if (started) {
            if (p != lastPercent) {
                lastPercent = p!!
                lastChangeAt = nowMs
            }
            if (!showing) {
                showing = true
                if (lastChangeAt == 0L) lastChangeAt = nowMs
            }
        }
        return if (showing) Decision.Show(lastPercent.takeIf { it >= 0 }?.div(100f)) else Decision.KeepQr
    }

    /** Файл в облаке готов, планшет начал скачивать: это всегда настоящая загрузка. */
    @Synchronized
    fun onDownloadStarted(nowMs: Long) {
        baselineSet = true
        showing = true
        lastChangeAt = nowMs
    }

    /** Любое продвижение скачивания на планшете. */
    @Synchronized
    fun onActivity(nowMs: Long) {
        if (showing) lastChangeAt = nowMs
    }

    /** true, если экран загрузки показан, а прогресса нет дольше [stallMs]. */
    @Synchronized
    fun isStalled(nowMs: Long): Boolean = showing && nowMs - lastChangeAt >= stallMs
}
