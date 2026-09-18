package com.aerocell.stand.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.StandUiState
import com.aerocell.stand.audio.StandTrack
import com.aerocell.stand.ui.theme.AeroBg
import com.aerocell.stand.ui.theme.AeroCyan
import com.aerocell.stand.ui.theme.AeroGreen
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroRedDark
import kotlin.math.sin

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun StandScreen(
    state: StandUiState,
    onSelectTrack: (StandTrack) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
    onPan: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AeroBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Header()
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GenreRail(
                selected = state.track,
                playing = state.playing,
                onSelect = onSelectTrack,
                modifier = Modifier
                    .width(196.dp)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WaveStage(
                    playing = state.playing,
                    waveform = state.waveform,
                    pan = state.pan,
                    clockMs = state.positionMs,
                    onTogglePlay = { if (state.playing) onPause() else onPlay() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                SeekRow(
                    state = state,
                    onSeek = onSeek,
                    onStop = onStop
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BalanceHero(
            pan = state.pan,
            onPan = onPan,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.stp_logo),
            contentDescription = "StP",
            modifier = Modifier.height(44.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AEROCELL",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 6.sp
            )
            Text(
                text = "СТЕНД СТЕРЕОБАЛАНСА",
                color = AeroMuted,
                fontSize = 12.sp,
                letterSpacing = 2.4.sp
            )
        }
        Text(
            text = "жанр запускает трек сразу",
            color = AeroMuted.copy(alpha = 0.7f),
            fontSize = 12.sp,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun GenreRail(
    selected: StandTrack,
    playing: Boolean,
    onSelect: (StandTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StandTrack.entries.forEach { track ->
            val active = track == selected
            GenreCard(
                track = track,
                active = active,
                playing = active && playing,
                onClick = { onSelect(track) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GenreCard(
    track: StandTrack,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(
                if (active) Brush.verticalGradient(listOf(AeroRed, AeroRedDark))
                else Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF101010)))
            )
            .border(
                width = if (active) 0.dp else 1.5.dp,
                color = Color.White.copy(alpha = if (active) 0f else 0.78f),
                shape = CardShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = track.icon(),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title.uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = 1.2.sp
        )
        Text(
            text = track.subtitle,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            playing -> AeroGreen.copy(alpha = pulse)
                            active -> AeroGreen.copy(alpha = 0.55f)
                            else -> Color(0xFF4A4A4A)
                        }
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (playing) "ИГРАЕТ" else if (active) "ВЫБРАН" else "СТАРТ",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

private fun StandTrack.icon(): ImageVector = when (this) {
    StandTrack.Club -> Icons.Filled.Nightlife
    StandTrack.Classical -> Icons.Filled.Piano
    StandTrack.Rock -> Icons.Filled.ElectricBolt
}

@Composable
private fun WaveStage(
    playing: Boolean,
    waveform: ByteArray,
    pan: Float,
    clockMs: Int,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(Color(0xFF050505))
            .border(1.dp, AeroRed.copy(alpha = 0.32f), CardShape)
            .clickable(onClick = onTogglePlay)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0x42B71C1C), Color.Transparent, Color(0x2EB71C1C))
                )
            )
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(30, 255, 255, 255)
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                textSize = w * 0.13f
                letterSpacing = 0.2f
            }
            drawContext.canvas.nativeCanvas.drawText(
                "AEROCELL",
                w / 2f,
                h / 2f + paint.textSize / 3.4f,
                paint
            )
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 2f
            )
            val leftAmp = 0.2f + maxOf(0f, -pan) * 0.12f
            val rightAmp = 0.2f + maxOf(0f, pan) * 0.12f
            drawWave(
                samples = waveSamples(waveform, playing, clockMs, channel = 0),
                midY = h * 0.34f,
                amplitude = h * leftAmp,
                color = Color.White,
                glow = AeroCyan
            )
            drawWave(
                samples = waveSamples(waveform, playing, clockMs, channel = 1),
                midY = h * 0.68f,
                amplitude = h * rightAmp,
                color = Color(0xFFFFD0CC),
                glow = AeroRed
            )
        }
        if (!playing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(AeroRed)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Играть",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Пауза",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun waveSamples(
    waveform: ByteArray,
    playing: Boolean,
    clockMs: Int,
    channel: Int
): FloatArray {
    val n = 108
    val out = FloatArray(n)
    if (playing && waveform.size >= n) {
        val step = waveform.size / n.toFloat()
        for (i in 0 until n) {
            val idx = ((i * step).toInt() + channel * 8) % waveform.size
            out[i] = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
        }
        return out
    }
    val t = clockMs / 1000f + channel * 0.35f
    for (i in 0 until n) {
        val x = i / (n - 1f)
        out[i] = (
            sin((x * 3.4 + t * 0.55) * Math.PI) * 0.2 +
                sin((x * 7.1 + t * 0.28) * Math.PI) * 0.07
            ).toFloat()
    }
    return out
}

private fun DrawScope.drawWave(
    samples: FloatArray,
    midY: Float,
    amplitude: Float,
    color: Color,
    glow: Color
) {
    if (samples.isEmpty()) return
    val path = Path()
    val last = samples.lastIndex.coerceAtLeast(1)
    samples.forEachIndexed { i, sample ->
        val x = size.width * i / last
        val y = midY + sample * amplitude
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, glow.copy(alpha = 0.38f), style = Stroke(width = 12f, cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
}

@Composable
private fun SeekRow(
    state: StandUiState,
    onSeek: (Int) -> Unit,
    onStop: () -> Unit
) {
    val duration = state.durationMs.coerceAtLeast(0)
    val progress = if (duration > 0) state.positionMs.toFloat() / duration else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AeroPanel)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = fmt(state.positionMs),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp)
        )
        Fader(
            value = progress.coerceIn(0f, 1f),
            range = 0f..1f,
            onChange = { if (duration > 0) onSeek((it * duration).toInt()) },
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .padding(horizontal = 10.dp),
            trackBrush = Brush.horizontalGradient(listOf(AeroRedDark, AeroRed)),
            showCenter = false
        )
        Text(
            text = fmt(duration),
            color = AeroMuted,
            fontSize = 14.sp,
            modifier = Modifier.width(48.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Стоп",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BalanceHero(
    pan: Float,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        pan < -0.08f -> "ЛЕВЫЙ КАНАЛ"
        pan > 0.08f -> "ПРАВЫЙ КАНАЛ"
        else -> "ЦЕНТР"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(AeroPanel)
            .border(1.5.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = "СТЕРЕО БАЛАНС  ·  $label",
            color = AeroMuted,
            fontSize = 12.sp,
            letterSpacing = 2.2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelBadge("L", active = pan < -0.08f, color = AeroCyan)
            Fader(
                value = pan,
                range = -1f..1f,
                onChange = onPan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                trackBrush = Brush.horizontalGradient(listOf(AeroCyan, Color.White, AeroRed)),
                showCenter = true
            )
            ChannelBadge("R", active = pan > 0.08f, color = AeroRed)
        }
    }
}

@Composable
private fun ChannelBadge(text: String, active: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (active) color else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) Color.White else AeroMuted,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp
        )
    }
}

@Composable
private fun Fader(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackBrush: Brush,
    showCenter: Boolean
) {
    val latest = rememberUpdatedState(onChange)
    Box(
        modifier = modifier.pointerInput(range) {
            fun emit(x: Float) {
                val t = (x / size.width.toFloat()).coerceIn(0f, 1f)
                val next = range.start + t * (range.endInclusive - range.start)
                latest.value(next)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                emit(down.position.x)
                drag(down.id) { change ->
                    emit(change.position.x)
                    change.consume()
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = 10.dp.toPx()
            val y = size.height / 2f
            val trackTop = y - trackH / 2f
            val span = range.endInclusive - range.start
            val t = if (span == 0f) 0.5f else ((value - range.start) / span).coerceIn(0f, 1f)
            val thumbX = t * size.width
            drawRoundRect(
                brush = trackBrush,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
            )
            if (showCenter) {
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(size.width / 2f, y - 18.dp.toPx()),
                    end = Offset(size.width / 2f, y + 18.dp.toPx()),
                    strokeWidth = 2f
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = 22.dp.toPx(),
                center = Offset(thumbX, y)
            )
            drawCircle(
                color = Color.White,
                radius = 13.dp.toPx(),
                center = Offset(thumbX, y)
            )
            drawCircle(
                color = AeroRed,
                radius = 13.dp.toPx(),
                center = Offset(thumbX, y),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

private fun fmt(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
