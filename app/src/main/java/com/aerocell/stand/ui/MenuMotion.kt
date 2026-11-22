package com.aerocell.stand.ui

import kotlin.math.max

/** Прямоугольник в пикселях окна. Свой класс, чтобы математику переходов проверять обычными тестами. */
data class MotionRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Движение главного меню: пружина почти без перелёта, ~350–450 мс до покоя.
 * Все переходы — Animatable, поэтому любое новое касание разворачивает движение с текущего места.
 */
object MenuMotion {
    /** Затухание и жёсткость пружины перехода (ζ≈0.82, ω≈17 рад/с). */
    const val DAMPING = 0.82f
    const val STIFFNESS = 300f
    /** Нажатие на плитку: масштаб 0,96; на круглые кнопки: 0,9. */
    const val TILE_PRESSED_SCALE = 0.96f
    const val ROUND_PRESSED_SCALE = 0.9f
    /** Меню под открытым сравнением чуть уменьшается и темнеет. */
    const val MENU_BEHIND_SCALE = 0.95f
    const val MENU_BEHIND_DIM = 0.6f
    /** Затемнение под окном с сайтом. */
    const val SCRIM_ALPHA = 0.72f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun lerp(a: MotionRect, b: MotionRect, t: Float): MotionRect = MotionRect(
        lerp(a.left, b.left, t),
        lerp(a.top, b.top, t),
        lerp(a.right, b.right, t),
        lerp(a.bottom, b.bottom, t)
    )

    /** Во сколько раз уменьшить полный экран, чтобы он закрыл прямоугольник целиком (как object-fit: cover). */
    fun coverScale(rect: MotionRect, fullWidth: Float, fullHeight: Float): Float {
        if (fullWidth <= 0f || fullHeight <= 0f) return 1f
        return max(rect.width / fullWidth, rect.height / fullHeight)
    }

    /** Содержимое проявляется в первой половине перехода, чтобы плитка не «мигала» пустым прямоугольником. */
    fun contentAlpha(progress: Float, speed: Float = 2.2f): Float = (progress * speed).coerceIn(0f, 1f)

    /** Переход идёт, пока прогресс не дошёл до цели. */
    fun isMoving(progress: Float, target: Float): Boolean = kotlin.math.abs(progress - target) > 0.001f
}
