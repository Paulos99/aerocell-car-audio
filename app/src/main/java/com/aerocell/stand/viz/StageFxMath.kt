package com.aerocell.stand.viz

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Доля громкости каждого короба по положению баланса, как просил Павел:
 * L = 1 при крайнем L, 0.5 посередине, 0 при крайнем R; R — зеркально.
 * pan: -1 = только AEROCELL (L), +1 = только короб без обработки (R).
 */
object BalanceShare {
    fun left(pan: Float): Float = ((1f - pan) / 2f).coerceIn(0f, 1f)
    fun right(pan: Float): Float = 1f - left(pan)
}

/** Сглаживание без перелёта: экспонента к цели, как пружина с критическим затуханием. */
object Smooth {
    fun step(current: Float, target: Float, dtSec: Float, responseSec: Float): Float {
        val tau = max(0.04f, responseSec * 0.45f)
        val k = 1f - exp(-dtSec / tau)
        return current + (target - current) * k
    }
}

/**
 * Детектор удара бочки по энергии низов (40–160 Гц).
 * Порог плавающий: средний уровень × [ratio] + небольшой запас; между ударами не меньше [minIntervalMs].
 * Сильные удары (выше порога в [strongRatio] раз) ограничены: не больше [maxStrongPerSec] в секунду.
 * Работает на одном потоке, без выделений памяти.
 */
class KickDetector(
    private val minIntervalMs: Long = 120L,
    private val ratio: Float = 1.35f,
    private val floor: Float = 0.06f,
    private val strongRatio: Float = 1.25f,
    private val maxStrongPerSec: Int = 3
) {
    private var average = 0f
    private var lastKickAt = Long.MIN_VALUE / 2
    private var primed = false
    private val strongTimes = LongArray(maxStrongPerSec) { Long.MIN_VALUE / 2 }
    private var strongIdx = 0

    /** Последний удар был сильным. */
    var lastStrong: Boolean = false
        private set

    /** Сила последнего удара 0..1 относительно порога. */
    var lastPower: Float = 0f
        private set

    val threshold: Float get() = max(floor, average * ratio)

    /** true, если в этом отсчёте удар. */
    fun feed(bass: Float, nowMs: Long): Boolean {
        val b = bass.coerceAtLeast(0f)
        if (!primed) {
            average = b
            primed = true
            return false
        }
        val th = threshold
        var kick = false
        if (b > th && nowMs - lastKickAt >= minIntervalMs) {
            kick = true
            lastKickAt = nowMs
            val strongCandidate = b > th * strongRatio
            // Сильный, только если за последнюю секунду их было меньше maxStrongPerSec.
            val oldest = strongTimes[strongIdx]
            lastStrong = strongCandidate && nowMs - oldest >= 1000L
            if (lastStrong) {
                strongTimes[strongIdx] = nowMs
                strongIdx = (strongIdx + 1) % strongTimes.size
            }
            lastPower = ((b - th) / max(th, 1e-3f)).coerceIn(0f, 1f).let { 0.5f + 0.5f * it }
        }
        // Плавающий порог: быстрее растёт, медленнее падает.
        val a = if (b > average) 0.18f else 0.06f
        average += (b - average) * a
        return kick
    }

    fun reset() {
        primed = false
        average = 0f
        lastKickAt = Long.MIN_VALUE / 2
        for (i in strongTimes.indices) strongTimes[i] = Long.MIN_VALUE / 2
        strongIdx = 0
        lastStrong = false
        lastPower = 0f
    }
}

/** Огибающая удара: подъём [ATTACK], спад [DECAY], между ударами фон [BASE]. */
object KickEnvelope {
    const val ATTACK = 0.05f
    const val DECAY = 0.32f
    const val BASE = 0.08f

    fun at(sinceSec: Float, strong: Boolean): Float {
        if (sinceSec < 0f || sinceSec > ATTACK + DECAY) return BASE
        if (sinceSec <= ATTACK) {
            val u = sinceSec / ATTACK
            return BASE + (1f - BASE) * (1f - (1f - u) * (1f - u))
        }
        val u = (sinceSec - ATTACK) / DECAY
        val peak = if (strong) 1f else 0.62f
        return BASE + (peak - BASE) * (1f - u).pow(1.55f)
    }
}

/**
 * Ударные волны от динамика AEROCELL. Фиксированный пул на [MAX] волн, без выделений.
 * Все размеры в пикселях макета 2320×1440; [pack] переводит их в пиксели экрана.
 */
class Shockwaves {
    companion object {
        const val MAX = 4
        const val LIFE_SEC = 1.05f
        /** Ниже этой доли L волн нет совсем. */
        const val MIN_DRIVE = 0.18f
        /** С этой доли L появляются волны и на слабых ударах. */
        const val WEAK_DRIVE = 0.45f
        /** С этой доли на сильном ударе идёт вторая волна. */
        const val DOUBLE_DRIVE = 0.62f
    }

    private val start = FloatArray(MAX) { -100f }
    private val pow = FloatArray(MAX)
    private val drive = FloatArray(MAX)
    private var next = 0

    /** Сколько волн живо после последнего [pack]. */
    var active: Int = 0
        private set

    /** Удар бочки: решаем, сколько волн пустить. drive = доля L × уровень × интенсивность. */
    fun onKick(nowSec: Float, strong: Boolean, kickPower: Float, drive: Float) {
        if (drive < MIN_DRIVE) return
        if (!strong && drive <= WEAK_DRIVE) return
        val p = (if (strong) 1f else 0.55f) * drive * kickPower.coerceIn(0.5f, 1f)
        spawn(nowSec, p, drive)
        if (strong && drive > DOUBLE_DRIVE) spawn(nowSec + 0.11f, p * 0.72f, drive)
    }

    private fun spawn(at: Float, p: Float, d: Float) {
        start[next] = at
        pow[next] = p
        drive[next] = d
        next = (next + 1) % MAX
    }

    /**
     * Пишет живые волны в [out] (MAX × float4: радиус, ширина, сила, блик) в пикселях экрана.
     * Возвращает число волн.
     */
    fun pack(nowSec: Float, scale: Float, out: FloatArray): Int {
        var n = 0
        for (i in 0 until MAX) {
            val age = nowSec - start[i]
            if (age < 0f || age > LIFE_SEC) continue
            val u = age / LIFE_SEC
            val fade = 1f - u.pow(1.15f)
            val p = pow[i]
            val r = 200f + (780f + 300f * drive[i]) * age
            val width = 16f + 14f * p
            val strength = (24f + 30f * p) * fade
            val rim = (0.28f + 0.38f * p) * fade
            val o = n * 4
            out[o] = r * scale
            out[o + 1] = width * scale
            out[o + 2] = strength * scale
            out[o + 3] = rim
            n++
        }
        for (j in n * 4 until out.size) out[j] = 0f
        active = n
        return n
    }

    fun clear() {
        for (i in 0 until MAX) start[i] = -100f
        active = 0
    }
}
