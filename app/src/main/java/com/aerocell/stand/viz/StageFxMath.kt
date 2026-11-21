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
 * Энергия низов 30–150 Гц из кадра Visualizer (8-бит waveform, 128 = ноль).
 * Считаем сами: окно Ханна + ДПФ ровно на частотах 30, 40 … 150 Гц.
 * Встроенный FFT Visualizer считает без окна и в 8 битах, поэтому средние частоты
 * скрипок и гитар протекают в нижние бины, и на классике волн было больше, чем на клубе.
 * Таблицы считаются один раз на размер кадра и частоту дискретизации; в кадре без выделений.
 * Результат: RMS амплитуд по частотам, полная синусоида внутри полосы даёт около 1.
 */
class BassBand(val size: Int, val sampleRateHz: Int) {
    companion object {
        const val LOW_HZ = 30
        const val HIGH_HZ = 150
        const val STEP_HZ = 10
    }

    private val freqs = (LOW_HZ..HIGH_HZ step STEP_HZ).toList()
    private val cosT = FloatArray(freqs.size * size)
    private val sinT = FloatArray(freqs.size * size)
    private val norm: Float

    init {
        var wsum = 0.0
        for (n in 0 until size) {
            val w = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * n / (size - 1))
            wsum += w
            for (f in freqs.indices) {
                val ph = 2.0 * Math.PI * freqs[f] * n / sampleRateHz
                cosT[f * size + n] = (w * kotlin.math.cos(ph)).toFloat()
                sinT[f * size + n] = (w * kotlin.math.sin(ph)).toFloat()
            }
        }
        norm = (2.0 / wsum).toFloat()
    }

    fun energy(waveform: ByteArray): Float {
        if (waveform.size < size) return 0f
        var acc = 0.0
        for (f in freqs.indices) {
            var re = 0f
            var im = 0f
            val o = f * size
            for (n in 0 until size) {
                val x = ((waveform[n].toInt() and 0xFF) - 128) / 128f
                re += x * cosT[o + n]
                im += x * sinT[o + n]
            }
            val m = kotlin.math.sqrt(re * re + im * im) * norm
            acc += (m * m).toDouble()
        }
        return kotlin.math.sqrt(acc / freqs.size).toFloat()
    }
}

/**
 * Удар по низам: положительный прирост энергии 30–150 Гц (spectral flux) выше плавающего порога
 * «среднее + k·σ» прироста, но не ниже [floor]; сама энергия низов не ниже [minEnergy].
 * Между ударами не меньше [minIntervalMs]; сильных (прирост выше порога в [strongRatio] раз)
 * не больше [maxStrongPerSec] в секунду. Пороги подобраны на трёх треках стенда
 * (кадры 1024 отсчёта, 20 кадров в секунду). Один поток, без выделений.
 */
class KickDetector(
    private val minIntervalMs: Long = 120L,
    private val k: Float = 1.5f,
    private val floor: Float = 0.12f,
    private val minEnergy: Float = 0.15f,
    private val alpha: Float = 0.05f,
    private val strongRatio: Float = 1.6f,
    private val maxStrongPerSec: Int = 3,
    private val warmupFrames: Int = 10
) {
    private var prev = 0f
    private var primed = false
    private var mean = 0f
    private var variance = 0f
    private var frames = 0
    private var lastKickAt = Long.MIN_VALUE / 2
    private val strongTimes = LongArray(maxStrongPerSec) { Long.MIN_VALUE / 2 }
    private var strongIdx = 0

    var lastStrong: Boolean = false
        private set

    /** Сила последнего удара 0.35..1 по тому, насколько прирост превысил порог. */
    var lastPower: Float = 0f
        private set

    /** Последний прирост энергии низов (для отладки). */
    var lastFlux: Float = 0f
        private set

    val threshold: Float get() = max(floor, mean + k * kotlin.math.sqrt(variance))

    fun feed(bassEnergy: Float, nowMs: Long): Boolean {
        val e = bassEnergy.coerceAtLeast(0f)
        if (!primed) {
            prev = e
            primed = true
            return false
        }
        val flux = max(0f, e - prev)
        prev = e
        lastFlux = flux
        val th = threshold
        var kick = false
        if (frames >= warmupFrames && flux > th && e >= minEnergy && nowMs - lastKickAt >= minIntervalMs) {
            kick = true
            lastKickAt = nowMs
            val oldest = strongTimes[strongIdx]
            lastStrong = flux > th * strongRatio && nowMs - oldest >= 1000L
            if (lastStrong) {
                strongTimes[strongIdx] = nowMs
                strongIdx = (strongIdx + 1) % strongTimes.size
            }
            lastPower = 0.35f + 0.65f * ((flux - th) / max(th, 1e-3f)).coerceIn(0f, 1f)
        }
        // Статистика прироста обновляется после решения, чтобы сам удар не поднимал себе порог.
        val d = flux - mean
        mean += alpha * d
        variance = (1f - alpha) * (variance + alpha * d * d)
        frames++
        return kick
    }

    fun reset() {
        primed = false
        prev = 0f
        mean = 0f
        variance = 0f
        frames = 0
        lastKickAt = Long.MIN_VALUE / 2
        for (i in strongTimes.indices) strongTimes[i] = Long.MIN_VALUE / 2
        strongIdx = 0
        lastStrong = false
        lastPower = 0f
        lastFlux = 0f
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

    /** Удар по низам: решаем, сколько волн пустить. drive = доля L × интенсивность. */
    fun onKick(nowSec: Float, strong: Boolean, kickPower: Float, drive: Float) {
        if (drive < MIN_DRIVE) return
        if (!strong && drive < WEAK_DRIVE) return
        // Сила волны = сила удара по низам × доля L (× интенсивность).
        val p = (if (strong) 1f else 0.7f) * drive * kickPower.coerceIn(0.35f, 1f)
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
