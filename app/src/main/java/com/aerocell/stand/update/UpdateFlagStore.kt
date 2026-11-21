package com.aerocell.stand.update

import android.content.Context

/**
 * Флаг «идёт обновление» в SharedPreferences. Его ставит старая версия прямо перед
 * commit сессии PackageInstaller, а читает уже новая после MY_PACKAGE_REPLACED,
 * чтобы продолжить тот же экран «Обновляем стенд» без рывка.
 */
class UpdateFlagStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Flag(val targetVersionCode: Int, val targetVersionName: String, val startedAtMs: Long)

    fun read(): Flag? {
        val code = prefs.getInt(KEY_TARGET_CODE, -1)
        val started = prefs.getLong(KEY_STARTED_AT, -1L)
        if (code <= 0 || started <= 0L) return null
        return Flag(code, prefs.getString(KEY_TARGET_NAME, "").orEmpty(), started)
    }

    /** commit(), а не apply(): процесс может умереть сразу после установки. */
    fun set(targetVersionCode: Int, targetVersionName: String, startedAtMs: Long) {
        prefs.edit()
            .putInt(KEY_TARGET_CODE, targetVersionCode)
            .putString(KEY_TARGET_NAME, targetVersionName)
            .putLong(KEY_STARTED_AT, startedAtMs)
            .commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS = "aerocell_update"
        private const val KEY_TARGET_CODE = "target_version_code"
        private const val KEY_TARGET_NAME = "target_version_name"
        private const val KEY_STARTED_AT = "started_at"

        /** Сколько держим экран обновления, если новая версия так и не стартовала. */
        const val TIMEOUT_MS = 120_000L
    }
}
