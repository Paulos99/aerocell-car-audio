package com.aerocell.stand.update

/**
 * Окно тихих часов из config.update.quietHours, например "22:00-08:00".
 * Минуты от полуночи; окно может переходить через полночь.
 */
data class QuietHours(val startMinute: Int, val endMinute: Int) {
    fun contains(minuteOfDay: Int): Boolean {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return when {
            startMinute == endMinute -> true
            startMinute < endMinute -> m in startMinute until endMinute
            else -> m >= startMinute || m < endMinute
        }
    }

    override fun toString(): String = "${fmt(startMinute)}-${fmt(endMinute)}"

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
        private val PATTERN = Regex("""^\s*(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})\s*$""")

        /** null, если строка пустая или записана неверно: тогда окно не действует. */
        fun parse(raw: String?): QuietHours? {
            if (raw.isNullOrBlank()) return null
            val m = PATTERN.matchEntire(raw) ?: return null
            val (h1, m1, h2, m2) = m.destructured
            val start = toMinute(h1.toInt(), m1.toInt()) ?: return null
            val end = toMinute(h2.toInt(), m2.toInt()) ?: return null
            return QuietHours(start, end)
        }

        private fun toMinute(h: Int, m: Int): Int? {
            if (h !in 0..24 || m !in 0..59) return null
            if (h == 24 && m != 0) return null
            return (h * 60 + m) % MINUTES_PER_DAY
        }

        private fun fmt(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    }
}

/** Настройки обновлений из config.update манифеста. */
data class UpdateConfig(
    val checkIntervalMin: Int = DEFAULT_CHECK_INTERVAL_MIN,
    val installAfterIdleSec: Int = DEFAULT_INSTALL_AFTER_IDLE_SEC,
    val quietHours: QuietHours? = null,
    val buttonAfterMin: Int = DEFAULT_BUTTON_AFTER_MIN
) {
    companion object {
        const val DEFAULT_CHECK_INTERVAL_MIN = 120
        const val MIN_CHECK_INTERVAL_MIN = 15
        const val DEFAULT_INSTALL_AFTER_IDLE_SEC = 120
        const val MIN_INSTALL_AFTER_IDLE_SEC = 10
        const val DEFAULT_BUTTON_AFTER_MIN = 30
        const val MIN_BUTTON_AFTER_MIN = 1
    }
}

/** Откуда пришла проверка, которая нашла обновление. */
enum class CheckOrigin { STARTUP, BACKGROUND, MANUAL }

/** Что делать с кнопкой «Обновить» на главном экране. */
enum class UpdateButton {
    /** Кнопки нет. */
    NONE,
    /** Кнопка ставит тихо, без вопросов системы. */
    SILENT,
    /** Тихо нельзя: кнопка запускает обычную установку с подтверждением. */
    SYSTEM
}

/** Снимок стенда, по которому решаем, ставить ли обновление прямо сейчас. */
data class UpdateInputs(
    /** APK новой версии скачан и sha256 сошёлся. */
    val ready: Boolean,
    /** Откуда пришла проверка, нашедшая это обновление. */
    val origin: CheckOrigin,
    /** Приложение — владелец устройства, тихая установка возможна. */
    val deviceOwner: Boolean,
    /** Тихая установка этой версии уже падала. */
    val silentFailed: Boolean,
    /** Установка уже идёт. */
    val installing: Boolean,
    val playing: Boolean,
    /** Открыто окно своего трека или идёт загрузка файла с телефона. */
    val visitorBusy: Boolean,
    val serviceMenuOpen: Boolean,
    /** С запуска приложения экран ещё никто не трогал. */
    val untouchedSinceStart: Boolean,
    /** Стенд уже в режиме простоя (ролики): значит, касаний не было давно. */
    val idleActive: Boolean = false,
    /** Сколько прошло с последнего касания или звука, мс. */
    val sinceInteractionMs: Long,
    /** Сколько обновление уже ждёт после скачивания, мс. */
    val readyForMs: Long,
    /** Минута от полуночи по местному времени. */
    val minuteOfDay: Int
)

data class UpdateDecision(
    val installSilentlyNow: Boolean,
    val button: UpdateButton,
    val waitingReason: String?
) {
    companion object {
        val NOTHING = UpdateDecision(false, UpdateButton.NONE, null)
    }
}

/**
 * Решает, когда ставить обновление. Чистая логика без Android, покрыта тестами.
 *
 * - На старте (никто ещё не трогал экран) ставим сразу, как только APK готов.
 * - В работе ставим тихо, когда стенд свободен: музыка не играет, окна закрыты,
 *   касаний не было installAfterIdleSec секунд и, если задано, сейчас тихие часы.
 * - Тихо нельзя (нет device owner или установка уже падала) — кнопка с подтверждением.
 * - Стенд занят дольше buttonAfterMin минут — кнопка, которая ставит тихо.
 */
object UpdatePolicy {
    fun decide(input: UpdateInputs, config: UpdateConfig): UpdateDecision {
        if (!input.ready || input.installing) return UpdateDecision.NOTHING
        if (!input.deviceOwner || input.silentFailed) {
            return UpdateDecision(false, UpdateButton.SYSTEM, "тихая установка недоступна")
        }
        val busy = input.playing || input.visitorBusy || input.serviceMenuOpen
        if (!busy && input.origin == CheckOrigin.STARTUP && input.untouchedSinceStart) {
            return UpdateDecision(true, UpdateButton.NONE, null)
        }
        val idleEnough = input.idleActive || input.sinceInteractionMs >= config.installAfterIdleSec * 1_000L
        val inWindow = config.quietHours?.contains(input.minuteOfDay) ?: true
        if (!busy && idleEnough && inWindow) {
            return UpdateDecision(true, UpdateButton.NONE, null)
        }
        val reason = when {
            input.playing -> "играет музыка"
            input.visitorBusy -> "открыт свой трек"
            input.serviceMenuOpen -> "открыто служебное меню"
            !idleEnough -> "ждёт ${config.installAfterIdleSec} с без касаний"
            else -> "ждёт тихих часов ${config.quietHours}"
        }
        val waitedTooLong = input.readyForMs >= config.buttonAfterMin * 60_000L
        return UpdateDecision(false, if (waitedTooLong) UpdateButton.SILENT else UpdateButton.NONE, reason)
    }

    /** Интервал фоновой проверки облака, мс. */
    fun checkIntervalMs(config: UpdateConfig): Long =
        config.checkIntervalMin.coerceAtLeast(UpdateConfig.MIN_CHECK_INTERVAL_MIN) * 60_000L

    /** Пора ли проверить облако по таймеру. */
    fun isCheckDue(nowMs: Long, lastAttemptMs: Long?, config: UpdateConfig): Boolean {
        if (lastAttemptMs == null) return true
        return nowMs - lastAttemptMs >= checkIntervalMs(config)
    }

    /** Сеть вернулась: перепроверяем, только если прошлая попытка упала. */
    fun shouldCheckOnNetworkRestore(lastCheckFailed: Boolean, checking: Boolean): Boolean =
        lastCheckFailed && !checking

    /** Экран «Обновляем стенд» после перезапуска: как с ним поступить. */
    fun resumeAfterRestart(
        flagTargetVersionCode: Int?,
        flagStartedAtMs: Long?,
        currentVersionCode: Int,
        nowMs: Long,
        timeoutMs: Long
    ): RestartOutcome {
        if (flagTargetVersionCode == null || flagStartedAtMs == null) return RestartOutcome.NONE
        if (currentVersionCode >= flagTargetVersionCode) return RestartOutcome.SHOW_DONE
        if (nowMs - flagStartedAtMs in 0 until timeoutMs) return RestartOutcome.KEEP_WAITING
        return RestartOutcome.EXPIRED
    }
}

enum class RestartOutcome {
    /** Флага нет — обычный запуск. */
    NONE,
    /** Новая версия встала: показываем «Готово» и уходим в рабочий экран. */
    SHOW_DONE,
    /** Флаг свежий, а версия старая: держим экран до таймаута. */
    KEEP_WAITING,
    /** Флаг протух: установка не удалась, возвращаем рабочий экран. */
    EXPIRED
}
