package com.aerocell.stand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.UpdateScreenPhase
import com.aerocell.stand.UpdateScreenState
import com.aerocell.stand.ui.theme.AeroBg
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroRedGlow
import com.aerocell.stand.ui.theme.AeroTrackDim

private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Ширина логотипа такая же, как у заставки windowBackground, чтобы стык после перезапуска был без сдвига. */
val UpdateLogoWidth = 360.dp

/**
 * Полноэкранный экран «Обновляем стенд». Проявляется за 400 мс, держится до перезапуска,
 * после перезапуска новая версия показывает «Готово · версия X» и плавно уходит.
 */
@Composable
fun UpdateScreenOverlay(screen: UpdateScreenState?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = screen != null,
        enter = fadeIn(tween(400, easing = EaseOutExpo)),
        exit = fadeOut(tween(600)),
        modifier = modifier
    ) {
        // Последнее состояние держим, пока идёт затухание.
        val last = remember { mutableHolder<UpdateScreenState>() }
        if (screen != null) last.value = screen
        last.value?.let { UpdateScreen(it) }
    }
}

private class Holder<T>(var value: T? = null)
private fun <T> mutableHolder() = Holder<T>()

@Composable
fun UpdateScreen(screen: UpdateScreenState, modifier: Modifier = Modifier) {
    val done = screen.phase == UpdateScreenPhase.DONE
    val infinite = rememberInfiniteTransition(label = "updateRings")
    val wave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)), RepeatMode.Reverse),
        label = "breath"
    )
    val ringsAlpha by animateFloatAsState(if (done) 0.35f else 1f, tween(600), label = "ringsAlpha")
    val progress by animateFloatAsState(
        targetValue = screen.progress ?: 0f,
        animationSpec = tween(300),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            // Мягкое красное свечение за логотипом, как на рабочем экране.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33FF1A1A), Color.Transparent),
                    center = c,
                    radius = size.minDimension * (0.55f + 0.05f * breath)
                ),
                radius = size.minDimension * 0.6f,
                center = c
            )
            // Три кольца-волны расходятся от логотипа, как звук от динамика.
            val minR = 170.dp.toPx()
            val maxR = size.minDimension * 0.62f
            for (i in 0 until 3) {
                val t = (wave + i / 3f) % 1f
                val eased = 1f - (1f - t) * (1f - t)
                val r = minR + (maxR - minR) * eased
                val a = (1f - t) * 0.55f * ringsAlpha
                drawCircle(
                    color = AeroRed.copy(alpha = a),
                    radius = r,
                    center = c,
                    style = Stroke(width = (2.5f - 1.5f * t).dp.toPx())
                )
            }
            drawCircle(
                color = AeroRed.copy(alpha = 0.18f * ringsAlpha),
                radius = minR - 8.dp.toPx(),
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Image(
            painter = painterResource(R.drawable.aerocell_qp_logo),
            contentDescription = "AEROCELL",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .width(UpdateLogoWidth)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        ) {
            Text(
                text = if (done) "Готово · версия ${screen.versionName}" else "Обновляем стенд",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (done) "Можно слушать дальше" else "Меньше минуты, и музыка вернётся",
                color = AeroMuted,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(22.dp))
            Canvas(
                modifier = Modifier
                    .width(UpdateLogoWidth)
                    .height(3.dp)
            ) {
                val h = size.height
                drawRoundRect(color = AeroTrackDim, size = size, cornerRadius = CornerRadius(h, h))
                val fill = if (done) 1f else progress.coerceIn(0f, 1f)
                if (fill > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(AeroRed, AeroRedGlow)),
                        size = Size(size.width * fill, h),
                        cornerRadius = CornerRadius(h, h)
                    )
                }
                if (!done && screen.phase == UpdateScreenPhase.INSTALLING) {
                    // После записи файла система ставит пакет: бегущий блик вместо процентов.
                    val x = size.width * wave
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                            startX = x - 60.dp.toPx(),
                            endX = x + 60.dp.toPx()
                        ),
                        size = size,
                        cornerRadius = CornerRadius(h, h)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (done) " " else "Версия ${screen.versionName}",
                color = AeroMuted.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}
