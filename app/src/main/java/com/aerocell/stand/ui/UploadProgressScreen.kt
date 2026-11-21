package com.aerocell.stand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.upload.UploadStage
import com.aerocell.stand.upload.UploadUiState
import com.aerocell.stand.ui.theme.AeroRed

private val StepLabels = listOf("Ждём файл", "Загружается", "Готовим", "Готово")
private val Todo = Color.White.copy(alpha = 0.3f)
private val Track = Color(0xFF3A3A3A)

/** Номер активного шага 0..3 для этапа загрузки. */
internal fun activeStep(stage: UploadStage): Int = when (stage) {
    UploadStage.NONE -> 0
    UploadStage.UPLOADING -> 1
    UploadStage.PREPARING -> 2
    UploadStage.DONE -> 3
    UploadStage.FAILED -> 1
}

/** Полноэкранный экран «Свой трек»: логотип, имя трека, четыре шага и полоса. */
@Composable
fun UploadProgressOverlay(state: UploadUiState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.stage != UploadStage.NONE,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(380)),
        modifier = modifier
    ) {
        val last = remember { Holder2<UploadUiState>() }
        if (state.stage != UploadStage.NONE) last.value = state
        last.value?.let { UploadProgressScreen(it) }
    }
}

private class Holder2<T>(var value: T? = null)

@Composable
private fun UploadProgressScreen(state: UploadUiState) {
    val failed = state.stage == UploadStage.FAILED
    val done = state.stage == UploadStage.DONE
    val active = activeStep(state.stage)
    val infinite = rememberInfiniteTransition(label = "upload")
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val shimmer by infinite.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer"
    )
    // Прогресс внутри активного шага: плавно и только вперёд.
    val stageValue = state.stageProgress
    val smooth by animateFloatAsState(
        targetValue = stageValue ?: 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "stageProgress"
    )
    // Финал: логотип чуть уменьшается перед уходом в чёрный.
    val logoScale by animateFloatAsState(
        targetValue = if (done) 0.94f else 1f,
        animationSpec = tween(600, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(AeroRed.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.41f),
                        radius = size.width * 0.5f
                    )
                )
            }
            // Экран загрузки касания не пропускает к стенду под ним.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.aerocell_qp_logo),
                contentDescription = "AEROCELL",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(360.dp)
                    .graphicsLayer {
                        val b = if (done || failed) 0f else breath
                        val s = (1f + 0.03f * b) * logoScale
                        scaleX = s
                        scaleY = s
                        alpha = 0.85f + 0.15f * b
                    }
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = "СВОЙ ТРЕК",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.3.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (failed) "Не получилось загрузить" else state.stageTrackName ?: "Файл с телефона",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 80.dp)
            )
            Spacer(Modifier.height(44.dp))
            Row(modifier = Modifier.width(760.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StepLabels.forEachIndexed { i, label ->
                    StepItem(
                        label = label,
                        status = when {
                            i < active || (done && i == active) -> StepStatus.DONE
                            i == active -> if (failed) StepStatus.FAILED else StepStatus.ACTIVE
                            else -> StepStatus.TODO
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.width(760.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    Segment(
                        fill = when {
                            i < active || done -> 1f
                            i == active && !failed -> smooth
                            else -> 0f
                        },
                        white = i < active || done,
                        running = i == active && !done && !failed,
                        indeterminate = i == active && stageValue == null,
                        shimmer = shimmer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                text = when {
                    failed -> "Попробуйте ещё раз: QR снова на экране"
                    done -> "Включаем"
                    else -> "Музыка начнётся сама"
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 15.sp
            )
        }
    }
}

private enum class StepStatus { DONE, ACTIVE, TODO, FAILED }

@Composable
private fun StepItem(label: String, status: StepStatus, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = when (status) {
            StepStatus.DONE -> Color.White
            StepStatus.ACTIVE, StepStatus.FAILED -> AeroRed
            StepStatus.TODO -> Todo
        },
        animationSpec = tween(280),
        label = "stepColor"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    when (status) {
                        StepStatus.DONE -> Modifier.background(Color.White)
                        StepStatus.ACTIVE, StepStatus.FAILED -> Modifier.border(2.dp, AeroRed, CircleShape)
                        StepStatus.TODO -> Modifier.border(1.5.dp, Todo, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                StepStatus.DONE -> CheckMark()
                StepStatus.ACTIVE -> Box(Modifier.size(10.dp).clip(CircleShape).background(AeroRed))
                StepStatus.FAILED -> Text("!", color = AeroRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                StepStatus.TODO -> Unit
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CheckMark() {
    // Галочка дорисовывается за 300 мс.
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val pts = listOf(Offset(w * 0.21f, h * 0.52f), Offset(w * 0.38f, h * 0.70f), Offset(w * 0.79f, h * 0.29f))
        val first = 0.4f
        val p = draw.value
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            if (p <= first) {
                val t = p / first
                lineTo(pts[0].x + (pts[1].x - pts[0].x) * t, pts[0].y + (pts[1].y - pts[0].y) * t)
            } else {
                lineTo(pts[1].x, pts[1].y)
                val t = (p - first) / (1f - first)
                lineTo(pts[1].x + (pts[2].x - pts[1].x) * t, pts[1].y + (pts[2].y - pts[1].y) * t)
            }
        }
        drawPath(path, Color.Black, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun Segment(
    fill: Float,
    white: Boolean,
    running: Boolean,
    indeterminate: Boolean,
    shimmer: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.height(4.dp).clip(RoundedCornerShape(4.dp))) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Track, cornerRadius = r)
        if (white) {
            drawRoundRect(Color.White, cornerRadius = r)
            return@Canvas
        }
        if (running && fill > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFFB00000), Color(0xFFFF3B3B)), endX = size.width * fill),
                size = Size(size.width * fill, size.height),
                cornerRadius = r
            )
        }
        if (running && indeterminate) {
            // Процент неизвестен: вместо выдуманного счётчика по полосе бежит блик.
            val w = 70.dp.toPx()
            val x = shimmer * size.width - w / 2f
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                    startX = x,
                    endX = x + w
                ),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height)
            )
        }
    }
}
