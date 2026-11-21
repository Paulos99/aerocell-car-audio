package com.aerocell.stand.viz

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.aerocell.stand.audio.AudioFeed
import kotlin.math.max

/**
 * Состояние визуализатора на кадр. Поля — Compose-состояния, их читают только
 * в фазах layout/draw (graphicsLayer, drawBehind, Canvas), поэтому кадр не вызывает перекомпоновку.
 */
@Stable
class StageFx {
    /** Масштаб коробов (пружина без перелёта). */
    var scaleL by mutableFloatStateOf(1f)
    var scaleR by mutableFloatStateOf(1f)
    /** Заполнение индикатора в плашке L, 0..1. */
    var meterL by mutableFloatStateOf(0f)
    /** Огибающая удара с учётом уровня, 0..1. */
    var env by mutableFloatStateOf(0f)
    /** Свечение за коробами, уже с долей канала и интенсивностью. */
    var glowL by mutableFloatStateOf(0f)
    var glowR by mutableFloatStateOf(0f)
    /** Прибавка красного фона за AEROCELL. */
    var bgBoost by mutableFloatStateOf(0.06f)
    /** Ореол бегунка баланса. */
    var halo by mutableFloatStateOf(0f)
    /** Меняется каждый кадр, пока живы волны: перерисовывает слой с шейдером. */
    var waveTick by mutableIntStateOf(0)

    /** Центр динамика AEROCELL и радиус короба в пикселях корня. */
    var cone: Rect? = null

    internal val waves = Shockwaves()
    internal val packed = FloatArray(Shockwaves.MAX * 4)
    internal var waveCount = 0
    // Если шейдер не собрался на этом устройстве, просто работаем без искажения.
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) runCatching { ShockShader() }.getOrNull() else null

    /** RenderEffect для фонового слоя или null, если волн нет (или Android < 13). */
    fun renderEffect(): androidx.compose.ui.graphics.RenderEffect? {
        @Suppress("UNUSED_VARIABLE") val tick = waveTick
        if (waveCount == 0 || Build.VERSION.SDK_INT < 33) return null
        val c = cone ?: return null
        val sh = shader as? ShockShader ?: return null
        return runCatching { sh.effect(c, packed, waveCount) }.getOrNull()
    }
}

@RequiresApi(33)
private class ShockShader {
    private val shader = RuntimeShader(SHOCKWAVE_AGSL)

    fun effect(cone: Rect, waves: FloatArray, count: Int): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("center", cone.center.x, cone.center.y)
        shader.setFloatUniform("boxR", cone.width / 2f)
        shader.setFloatUniform("count", count.toFloat())
        shader.setFloatUniform("waves", waves)
        // Эффект снимает значения униформ при создании, поэтому пересоздаём его, пока идут волны.
        return RenderEffect.createRuntimeShaderEffect(shader, "scene").asComposeRenderEffect()
    }
}

/** Порт WebGL-прохода из прототипа v3: стеклянная линза по фронту волны, лёгкий цветной край. */
private const val SHOCKWAVE_AGSL = """
uniform shader scene;
uniform float2 center;
uniform float boxR;
uniform float count;
uniform float4 waves[4];

half4 main(float2 px) {
    float2 dir = px - center;
    float d = length(dir);
    float2 n = dir / max(d, 1.0);
    float2 offset = float2(0.0);
    float rim = 0.0;
    for (int i = 0; i < 4; i++) {
        float on = step(float(i) + 0.5, count);
        float4 w = waves[i];
        float r = w.x;
        float wd = max(w.y, 1.0);
        float x = (d - r) / wd;
        float bump = exp(-x * x);
        float x2 = (d - r + wd * 0.85) / (wd * 1.15);
        float trough = exp(-x2 * x2) * -0.20;
        offset += n * (bump + trough) * w.z * on;
        rim += exp(-x * x * 2.6) * w.w * on;
    }
    float mask = smoothstep(boxR * 0.92, boxR + 48.0, d);
    offset *= mask;
    rim *= mask;
    // Выборка смещается от центра наружу, как в прототипе.
    half4 g = scene.eval(px + offset);
    half r1 = scene.eval(px + offset * float2(1.08, 1.05)).r;
    half b1 = scene.eval(px + offset * float2(0.92, 0.95)).b;
    half3 col = half3(r1, g.g, b1);
    col += half3(1.0, 0.95, 0.92) * half(rim * 0.55);
    col += half3(1.0, 0.22, 0.14) * half(rim * 0.14);
    return half4(col, g.a);
}
"""

/**
 * Крутит визуализатор каждый кадр. Ничего не выделяет в кадре: только числа и заранее созданные массивы.
 * intensity: 0..1 из служебного меню.
 */
@Composable
fun rememberStageFx(
    feed: AudioFeed?,
    playing: Boolean,
    pan: Float,
    intensity: Float
): StageFx {
    val fx = remember { StageFx() }
    val playingNow = rememberUpdatedState(playing)
    val panNow = rememberUpdatedState(pan)
    val intensityNow = rememberUpdatedState(intensity)
    val feedNow = rememberUpdatedState(feed)
    LaunchedEffect(fx) {
        var lastNanos = 0L
        var lastSeq = feedNow.value?.kickSeq ?: 0
        var kickAtSec = -100f
        var kickStrong = false
        val startNanos = System.nanoTime()
        while (true) {
            withFrameNanos { frame ->
                val dt = if (lastNanos == 0L) 1f / 60f else ((frame - lastNanos) / 1e9f).coerceIn(0.001f, 0.05f)
                lastNanos = frame
                val now = (System.nanoTime() - startNanos) / 1e9f
                val f = feedNow.value
                val on = playingNow.value && f != null
                val i = intensityNow.value.coerceIn(0f, 1f)
                val shareL = BalanceShare.left(panNow.value)
                val shareR = 1f - shareL
                val level = if (on) f!!.level else 0f

                if (f != null && f.kickSeq != lastSeq) {
                    lastSeq = f.kickSeq
                    if (on) {
                        // Удар мог прийти пару десятков мс назад: учитываем это в огибающей.
                        val lagSec = ((SystemClock.uptimeMillis() - f.kickAtMs).coerceIn(0L, 200L)) / 1000f
                        kickAtSec = now - lagSec
                        kickStrong = f.kickStrong
                        // Волны только от AEROCELL, по доле L.
                        val drive = shareL * i * (0.6f + 0.4f * level)
                        fx.waves.onKick(now, f.kickStrong, f.kickPower, drive)
                    }
                }
                val kickEnv = if (on) KickEnvelope.at(now - kickAtSec, kickStrong) else 0f
                // Тихие треки без бочки тоже чуть дышат от уровня.
                val env = max(kickEnv, 0.35f * level)
                fx.env = Smooth.step(fx.env, env, dt, 0.12f)

                val attackL = env > 0.4f && shareL >= 0.5f
                val attackR = env > 0.4f && shareR > 0.5f
                // Пульс линейно по доле канала: у крайнего L до 1.06, посередине оба до 1.03.
                val pulse = env * (0.5f + 0.5f * level) * i
                fx.scaleL = Smooth.step(fx.scaleL, 1f + 0.06f * shareL * pulse, dt, if (attackL) 0.10f else 0.36f)
                fx.scaleR = Smooth.step(fx.scaleR, 1f + 0.06f * shareR * pulse, dt, if (attackR) 0.10f else 0.36f)
                fx.meterL = Smooth.step(fx.meterL, shareL * level * (0.35f + 0.65f * env) * i, dt, 0.22f)
                fx.glowL = Smooth.step(fx.glowL, (if (on) 0.45f + 0.55f * env else 0.35f) * shareL * (0.4f + 0.6f * i), dt, 0.2f)
                fx.glowR = Smooth.step(fx.glowR, (if (on) 0.30f + 0.20f * env else 0.25f) * shareR * (0.4f + 0.6f * i), dt, 0.25f)
                fx.bgBoost = 0.06f + 0.09f * fx.env * shareL * i
                fx.halo = 0.35f + 0.65f * fx.env * max(shareL, shareR) * i

                if (i <= 0f) fx.waves.clear()
                val c = fx.cone
                val scale = if (c != null) c.width / 2f / BOX_R_REF else 1f
                val n = fx.waves.pack(now, scale, fx.packed)
                if (n > 0 || fx.waveCount > 0) {
                    fx.waveCount = n
                    fx.waveTick++
                }
            }
        }
    }
    return fx
}

/** Радиус короба AEROCELL в прототипе (px макета 2320×1440), от него масштабируются волны. */
private const val BOX_R_REF = 0.46f * 1024f * 0.69f
