package com.aerocell.stand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.onboarding.HintGesture
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroPanelSoft
import com.aerocell.stand.ui.theme.AeroRed
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Плавный ease-in-out для пальца подсказки. */
private val HintEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.9f), offset = Offset(0f, 2f), blurRadius = 18f)

/**
 * Кнопка «?» в самом углу: кружок 32 dp без красной обводки,
 * а зона касания 56 dp за счёт прозрачного поля вокруг.
 */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AeroPanel.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "?", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Подсказка поверх рабочего экрана. Затемнение ~70 % везде, кроме ряда баланса.
 * Палец пружинно ведёт настоящий ползунок: влево → вправо → центр, пауза 0,8 с, до 3 циклов.
 * Касания подсказка не ловит: их разбирает MainActivity (закрыть, а по ползунку — сразу двигать).
 */
@Composable
fun HintOverlay(
    visible: Boolean,
    balanceRowBounds: Rect?,
    faderBounds: Rect?,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && faderBounds != null,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        val fader = faderBounds ?: return@AnimatedVisibility
        val row = balanceRowBounds ?: fader
        val density = LocalDensity.current
        val pan = remember { Animatable(0f) }
        var ripple by remember { mutableFloatStateOf(0f) }
        val latestPan = rememberUpdatedState(onPan)

        LaunchedEffect(Unit) {
            delay(500)
            repeat(HintGesture.MAX_CYCLES) {
                ripple = 1f
                for (target in HintGesture.WAYPOINTS) {
                    pan.animateTo(
                        target,
                        tween(HintGesture.legMs(pan.value, target), easing = HintEase)
                    ) { latestPan.value(value) }
                    delay(HintGesture.END_PAUSE_MS)
                }
                ripple = 0f
                delay(HintGesture.PAUSE_MS)
            }
            // После трёх циклов подсказка остаётся статичной: палец ждёт на левом краю.
            val home = HintGesture.WAYPOINTS.first()
            pan.animateTo(home, tween(HintGesture.legMs(pan.value, home), easing = HintEase)) { latestPan.value(value) }
            ripple = 1f
        }

        val thumbX = fader.left + (pan.value + 1f) / 2f * fader.width
        val thumbY = fader.center.y

        Box(modifier = Modifier.fillMaxSize()) {
            // Затемнение с «лучом» на ряду баланса.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(Color.Black.copy(alpha = 0.70f))
                val rx = size.width * 0.54f
                val ry = row.height * 0.95f + 24.dp.toPx()
                scale(scaleX = rx / ry, scaleY = 1f, pivot = row.center) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.Black,
                            0.72f to Color.Black,
                            1f to Color.Transparent,
                            center = row.center,
                            radius = ry
                        ),
                        radius = ry,
                        center = row.center,
                        blendMode = BlendMode.DstOut
                    )
                }
                // Красная рамка ползунка.
                val pad = 6.dp.toPx()
                drawRoundRect(
                    color = AeroRed.copy(alpha = 0.85f),
                    topLeft = Offset(fader.left - pad, fader.top - pad),
                    size = androidx.compose.ui.geometry.Size(fader.width + pad * 2, fader.height + pad * 2),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Рябь касания под пальцем.
                if (ripple > 0f) {
                    val radii = listOf(16.dp, 26.dp, 38.dp)
                    val alphas = listOf(0.7f, 0.35f, 0.16f)
                    radii.forEachIndexed { i, r ->
                        drawCircle(
                            color = Color.White.copy(alpha = alphas[i]),
                            radius = r.toPx(),
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = (if (i == 0) 2.dp else 1.5.dp).toPx())
                        )
                    }
                }
            }

            val titleBottom = with(density) { (fader.top - 74.dp.toPx()).toDp() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 80.dp)
                    .offset { IntOffset(0, with(density) { (titleBottom - 82.dp).toPx().roundToInt() }) }
            ) {
                Text(
                    text = "Двигайте ползунок и слушайте разницу",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.6).sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = TextShadow)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Коснитесь экрана, чтобы начать",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 15.sp,
                    style = TextStyle(shadow = TextShadow)
                )
            }

            // Подписи у краёв самого ползунка.
            val edgeY = (fader.top - with(density) { 30.dp.toPx() }).roundToInt()
            Text(
                text = "С AEROCELL",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                style = TextStyle(shadow = TextShadow),
                modifier = Modifier.offset { IntOffset(fader.left.roundToInt(), edgeY) }
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, edgeY) }
                    .width(with(density) { fader.right.toDp() })
            ) {
                Text(
                    text = "Без обработки",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    style = TextStyle(shadow = TextShadow),
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Text(
                text = "AEROCELL",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.8.sp,
                modifier = Modifier.padding(start = 32.dp, top = 28.dp)
            )

            // Палец Phosphor HandPointing: кончик (112,16 из 256) стоит на ручке ползунка.
            val fingerSize = 96.dp
            val tipX = with(density) { (fingerSize * (112f / 256f)).toPx() }
            val tipY = with(density) { (fingerSize * (16f / 256f)).toPx() }
            Image(
                painter = painterResource(R.drawable.ic_hand_pointing),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset((thumbX - tipX).roundToInt(), (thumbY - tipY).roundToInt()) }
                    .size(fingerSize)
                    .graphicsLayer {
                        rotationZ = -48f
                        transformOrigin = TransformOrigin(112f / 256f, 16f / 256f)
                        shadowElevation = 8.dp.toPx()
                    }
            )
        }
    }
}

private data class HelpItem(val icon: Int, val text: String)

private val HelpItems = listOf(
    HelpItem(R.drawable.ic_help_boxes, "На стенде два одинаковых короба: слева с AEROCELL, справа без обработки."),
    HelpItem(R.drawable.ic_help_slider, "Двигайте ползунок влево и вправо и слушайте, как меняется звук."),
    HelpItem(R.drawable.ic_help_music, "Выберите трек: CLUB, ROCK или CLASSIC. Запустить и остановить музыку можно кнопками внизу."),
    HelpItem(R.drawable.ic_help_volume, "Громкость меняется ползунком справа внизу."),
    HelpItem(R.drawable.ic_help_qr, "Чтобы включить свою музыку, нажмите «Свой трек» и наведите камеру телефона на QR-код. На телефоне нужен только интернет.")
)

/** Панель «Как пользоваться стендом». Только клиентская часть, без служебного меню. */
@Composable
fun HelpPanel(visible: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        ) {
            Text(
                text = "AEROCELL",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.8.sp,
                modifier = Modifier.padding(start = 32.dp, top = 28.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(760.dp)
                    .shadow(24.dp, RoundedCornerShape(16.dp))
                    .background(AeroPanel, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    // Касания внутри панели не закрывают её.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 32.dp, vertical = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Как пользоваться стендом",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 8.dp, y = (-8).dp)
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_help_close),
                            contentDescription = "Закрыть",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 22.dp)
                        .width(56.dp)
                        .height(2.dp)
                        .background(AeroRed)
                )
                HelpItems.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AeroPanelSoft, RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(painter = painterResource(item.icon), contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = item.text,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 17.sp,
                            lineHeight = 23.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (i != HelpItems.lastIndex) Spacer(Modifier.height(16.dp))
                }
                Box(
                    modifier = Modifier
                        .padding(top = 26.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(240.dp)
                        .height(56.dp)
                        .background(AeroRed, RoundedCornerShape(10.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Понятно", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
