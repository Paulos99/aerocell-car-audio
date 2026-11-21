package com.aerocell.stand.onboarding

/** Одна машина состояний стенда: простой → подсказка → сравнение. */
enum class StandMode { IDLE, HINT, COMPARE }

data class OnboardingState(
    val mode: StandMode = StandMode.COMPARE,
    /** Подсказку уже видели в этом «визите»; сбрасывается при входе в простой. */
    val hintSeen: Boolean = false,
    /** Открыта панель помощи «?». */
    val helpOpen: Boolean = false
) {
    val hintVisible: Boolean get() = mode == StandMode.HINT
}

/**
 * Чистая логика подсказки и помощи, без Android, покрыта тестами.
 * Таймер простоя во время подсказки идёт как обычно; панель помощи простой держит,
 * но сама закрывается после [HELP_AUTO_CLOSE_MS] без касаний.
 */
object Onboarding {
    const val HELP_AUTO_CLOSE_MS = 120_000L

    /** Запуск приложения: сразу показываем подсказку. */
    fun onStart(state: OnboardingState): OnboardingState =
        state.copy(mode = if (state.hintSeen) StandMode.COMPARE else StandMode.HINT, helpOpen = false)

    /** Стенд ушёл в простой: пришёл, скорее всего, новый человек. */
    fun onEnterIdle(state: OnboardingState): OnboardingState =
        OnboardingState(mode = StandMode.IDLE, hintSeen = false, helpOpen = false)

    /** Касание в простое будит стенд и ведёт к подсказке, а не прямо к сравнению. */
    fun onWake(state: OnboardingState): OnboardingState {
        if (state.mode != StandMode.IDLE) return state
        return state.copy(mode = if (state.hintSeen) StandMode.COMPARE else StandMode.HINT)
    }

    /** Любое касание во время подсказки закрывает её. */
    fun onTouch(state: OnboardingState): OnboardingState {
        if (state.mode != StandMode.HINT) return state
        return state.copy(mode = StandMode.COMPARE, hintSeen = true)
    }

    fun openHelp(state: OnboardingState): OnboardingState {
        if (state.mode == StandMode.IDLE) return state
        return state.copy(mode = StandMode.COMPARE, hintSeen = true, helpOpen = true)
    }

    fun closeHelp(state: OnboardingState): OnboardingState = state.copy(helpOpen = false)

    /** Пока открыта помощь, простой и тихая установка не стартуют. */
    fun blocksIdle(state: OnboardingState): Boolean = state.helpOpen

    fun shouldAutoCloseHelp(state: OnboardingState, sinceInteractionMs: Long): Boolean =
        state.helpOpen && sinceInteractionMs >= HELP_AUTO_CLOSE_MS
}

/**
 * Траектория пальца в подсказке: влево → вправо → центр, пауза, не больше [MAX_CYCLES] циклов.
 * Значения — положение ползунка от -1 (AEROCELL) до 1 (без обработки).
 */
object HintGesture {
    const val MAX_CYCLES = 3
    const val PAUSE_MS = 800L
    val WAYPOINTS = listOf(-0.85f, 0.85f, 0f)
}
