package com.aerocell.stand.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.StandUiState
import com.aerocell.stand.audio.StandTrack
import com.aerocell.stand.ui.theme.AeroBg
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroPanelSoft
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroRedDark
import com.aerocell.stand.ui.theme.AeroRedGlow
import com.aerocell.stand.ui.theme.AeroSilver
import com.aerocell.stand.ui.theme.AeroTrackDim
import kotlin.math.sin

@Composable
fun StandScreen(
    state: StandUiState,
    onSelectTrack: (StandTrack) -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPan: (Float) -> Unit,
    onVolume: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AeroBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33FF1A1A), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.minDimension * 0.72f
                )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Header()
            Spacer(Modifier.height(6.dp))
            ComparisonStage(
                playing = state.playing,
                waveform = state.waveform,
                clockMs = state.positionMs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            BalanceRow(
                pan = state.pan,
                onPan = onPan,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            BottomBar(
                state = state,
                onSelectTrack = onSelectTrack,
                onPrevious = onPrevious,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
                onVolume = onVolume
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ТИШЕ  ДВИЖЕНИЕ  ДАЛЬШЕ",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ХОРОШИЕ АВТОМОБИЛИ\nЛУЧШИЕ ЛЮДИ",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1.2f)
        ) {
            Image(
                painter = painterResource(R.drawable.stp_logo),
                contentDescription = "StP",
                modifier = Modifier.height(36.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "AEROCELL",
                color = AeroRedGlow,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.shadow(12.dp, spotColor = AeroRed, ambientColor = AeroRed)
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "АКУСТИЧЕСКИЙ КОМФОРТ\nВ ДВИЖЕНИИ ВЕЗДЕ",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ComparisonStage(
    playing: Boolean,
    waveform: ByteArray,
    clockMs: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            painter = painterResource(R.drawable.speaker_left),
            contentDescription = "Короб AEROCELL",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 4.dp),
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "СРАВНЕНИЕ",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 6.sp
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 10.dp)
                    .width(56.dp)
                    .height(2.dp)
                    .background(AeroRed)
            )
            SingleWave(
                playing = playing,
                waveform = waveform,
                clockMs = clockMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
            )
        }
        Image(
            painter = painterResource(R.drawable.speaker_right),
            contentDescription = "Короб без обработки",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 4.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SingleWave(
    playing: Boolean,
    waveform: ByteArray,
    clockMs: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val samples = waveSamples(waveform, playing, clockMs)
        drawWaveBold(samples)
    }
}

private fun waveSamples(waveform: ByteArray, playing: Boolean, clockMs: Int): FloatArray {
    val n = 120
    val out = FloatArray(n)
    if (playing && waveform.size >= n) {
        val step = waveform.size / n.toFloat()
        for (i in 0 until n) {
            val idx = (i * step).toInt() % waveform.size
            out[i] = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
        }
        return out
    }
    val t = clockMs / 900f
    for (i in 0 until n) {
        val x = i / (n - 1f)
        val envelope = 0.35f + 0.65f * sin(x * Math.PI).toFloat()
        out[i] = (
            sin((x * 8.5 + t * 1.8) * Math.PI) * 0.55 +
                sin((x * 17.0 + t * 2.4) * Math.PI) * 0.28 +
                sin((x * 31.0 + t * 3.1) * Math.PI) * 0.12
            ).toFloat() * envelope * 0.55f
    }
    return out
}

private fun DrawScope.drawWaveBold(samples: FloatArray) {
    if (samples.isEmpty()) return
    val path = Path()
    val last = samples.lastIndex.coerceAtLeast(1)
    val midY = size.height / 2f
    val amp = size.height * 0.42f
    samples.forEachIndexed { i, sample ->
        val x = size.width * i / last
        val y = midY + sample * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, AeroRed.copy(alpha = 0.22f), style = Stroke(width = 22f, cap = StrokeCap.Round))
    drawPath(path, AeroRedGlow.copy(alpha = 0.55f), style = Stroke(width = 10f, cap = StrokeCap.Round))
    drawPath(path, Color.White.copy(alpha = 0.92f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
}

@Composable
private fun BalanceRow(
    pan: Float,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BalanceLabel(
            letter = "L",
            title = "Короб с обработкой",
            accent = "AEROCELL",
            modifier = Modifier.widthIn(min = 170.dp).weight(0.9f)
        )
        BalanceFader(
            pan = pan,
            onPan = onPan,
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
        )
        BalanceLabel(
            letter = "R",
            title = "Короб без обработки",
            accent = null,
            modifier = Modifier.widthIn(min = 170.dp).weight(0.9f)
        )
    }
}

@Composable
private fun BalanceLabel(
    letter: String,
    title: String,
    accent: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AeroPanelSoft)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = letter,
            color = AeroRedGlow,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            modifier = Modifier.shadow(8.dp, spotColor = AeroRed, ambientColor = AeroRed)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (accent != null) {
                Text(
                    text = accent,
                    color = AeroRedGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun BalanceFader(
    pan: Float,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = rememberUpdatedState(onPan)
    Box(
        modifier = modifier.pointerInput(Unit) {
            fun emit(x: Float) {
                val t = (x / size.width.toFloat()).coerceIn(0f, 1f)
                latest.value(-1f + t * 2f)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                emit(down.position.x)
                drag(down.id) { change ->
                    emit(change.position.x)
                    change.consume()
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = 4.dp.toPx()
            val y = size.height / 2f
            val mid = size.width / 2f
            val t = ((pan + 1f) / 2f).coerceIn(0f, 1f)
            val thumbX = t * size.width
            drawRoundRect(
                color = AeroTrackDim,
                topLeft = Offset(0f, y - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH, trackH)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(AeroRedDark, AeroRedGlow)),
                topLeft = Offset(0f, y - trackH / 2f),
                size = Size(thumbX.coerceAtLeast(trackH), trackH),
                cornerRadius = CornerRadius(trackH, trackH)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(mid, y - 14.dp.toPx()),
                end = Offset(mid, y + 14.dp.toPx()),
                strokeWidth = 1.5f
            )
            drawCircle(color = AeroRed.copy(alpha = 0.35f), radius = 18.dp.toPx(), center = Offset(thumbX, y))
            drawCircle(color = AeroSilver, radius = 11.dp.toPx(), center = Offset(thumbX, y))
            drawCircle(
                color = AeroRedGlow,
                radius = 11.dp.toPx(),
                center = Offset(thumbX, y),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun BottomBar(
    state: StandUiState,
    onSelectTrack: (StandTrack) -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onVolume: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AeroPanel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.15f)) {
            Text(
                text = "ВЫБЕРИТЕ ЖАНР ТРЕКА",
                color = AeroMuted,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StandTrack.entries.forEach { track ->
                    GenrePill(
                        label = track.label,
                        active = track == state.track,
                        onClick = { onSelectTrack(track) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleControl(
                size = 42.dp,
                filled = false,
                onClick = onPrevious
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Предыдущий",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            CircleControl(
                size = 64.dp,
                filled = true,
                onClick = { if (state.playing) onPause() else onPlay() }
            ) {
                Icon(
                    imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Пауза" else "Играть",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            CircleControl(
                size = 42.dp,
                filled = false,
                onClick = onStop
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Стоп",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            modifier = Modifier.weight(1.05f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            VolumeFader(
                volume = state.volume,
                onVolume = onVolume,
                modifier = Modifier
                    .width(150.dp)
                    .height(36.dp)
            )
        }
    }
}

@Composable
private fun GenrePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.Black else AeroPanelSoft)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) AeroRedGlow else Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CircleControl(
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = if (filled) 16.dp else 0.dp,
                shape = CircleShape,
                spotColor = AeroRed,
                ambientColor = AeroRed
            )
            .clip(CircleShape)
            .background(if (filled) Color.Black else Color.Transparent)
            .border(
                width = if (filled) 2.5.dp else 1.5.dp,
                color = if (filled) AeroRedGlow else Color.White.copy(alpha = 0.55f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun VolumeFader(
    volume: Float,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = rememberUpdatedState(onVolume)
    Box(
        modifier = modifier.pointerInput(Unit) {
            fun emit(x: Float) {
                val t = (x / size.width.toFloat()).coerceIn(0f, 1f)
                latest.value(t)
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
            val trackH = 4.dp.toPx()
            val y = size.height / 2f
            val t = volume.coerceIn(0f, 1f)
            val thumbX = t * size.width
            drawRoundRect(
                color = AeroTrackDim,
                topLeft = Offset(0f, y - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH, trackH)
            )
            drawRoundRect(
                color = AeroRed,
                topLeft = Offset(0f, y - trackH / 2f),
                size = Size(thumbX.coerceAtLeast(trackH), trackH),
                cornerRadius = CornerRadius(trackH, trackH)
            )
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(thumbX, y))
        }
    }
}
