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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.sqrt

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
                    colors = listOf(Color(0x28FF1A1A), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.3f),
                    radius = size.minDimension * 0.7f
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
            Spacer(Modifier.height(4.dp))
            ComparisonStage(
                playing = state.playing,
                waveform = state.waveform,
                midEnergy = state.midEnergy,
                pan = state.pan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
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
        }
    }
}

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(195.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.aerocell_qp_logo),
            contentDescription = "aerocell QP",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ComparisonStage(
    playing: Boolean,
    waveform: ByteArray,
    midEnergy: Float,
    pan: Float,
    modifier: Modifier = Modifier
) {
    val bassPulse = rememberBassPulse(playing, waveform)
    val midPulse = rememberMidPulse(playing, midEnergy)
    val leftLevel = if (pan <= 0f) 1f else 1f - pan
    val rightLevel = if (pan >= 0f) 1f else 1f + pan
    // Left = untreated (2x weaker), Right = AEROCELL
    val leftBass = bassPulse * leftLevel * 0.5f
    val rightBass = bassPulse * rightLevel
    val leftGlow = midPulse * leftLevel * 0.5f
    val rightGlow = midPulse * rightLevel
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PulsingSpeaker(
            resId = R.drawable.speaker_untreated,
            contentDescription = "Короб без обработки",
            bassPulse = leftBass,
            glowPulse = leftGlow,
            glowStrength = 0.75f,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 4.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
        }
        PulsingSpeaker(
            resId = R.drawable.speaker_aerocell,
            contentDescription = "Короб AEROCELL",
            bassPulse = rightBass,
            glowPulse = rightGlow,
            glowStrength = 1.15f,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 4.dp)
        )
    }
}

@Composable
private fun PulsingSpeaker(
    resId: Int,
    contentDescription: String,
    bassPulse: Float,
    glowPulse: Float,
    glowStrength: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow sits behind the enclosure and blooms with mid energy
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.98f)
                .graphicsLayer { alpha = 1f }
        ) {
            val intensity = (0.35f + glowPulse * 0.85f) * glowStrength
            val radius = size.minDimension * (0.48f + glowPulse * 0.12f)
            // offset slightly back/down so light reads as behind the box
            val center = Offset(size.width * 0.5f, size.height * 0.52f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF3B3B).copy(alpha = (intensity * 0.95f).coerceAtMost(1f)),
                        AeroRed.copy(alpha = (intensity * 0.55f).coerceAtMost(1f)),
                        AeroRedDark.copy(alpha = (intensity * 0.22f).coerceAtMost(1f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + bassPulse * 0.06f
                    scaleX = s
                    scaleY = s
                },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun rememberBassPulse(playing: Boolean, waveform: ByteArray): Float {
    var smoothed by remember { mutableFloatStateOf(0f) }
    val latestWave = rememberUpdatedState(waveform)
    val latestPlaying = rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                if (!latestPlaying.value) {
                    smoothed += (0f - smoothed) * 0.2f
                } else {
                    val wave = latestWave.value
                    if (wave.isEmpty()) {
                        smoothed += (0f - smoothed) * 0.2f
                    } else {
                        var sum = 0.0
                        val step = (wave.size / 64).coerceAtLeast(1)
                        var count = 0
                        var i = 0
                        while (i < wave.size) {
                            val v = ((wave[i].toInt() and 0xFF) - 128) / 128.0
                            sum += v * v
                            count++
                            i += step
                        }
                        val rms = if (count == 0) 0f else sqrt(sum / count).toFloat()
                        val target = (rms * 2.4f).coerceIn(0f, 1f)
                        smoothed += (target - smoothed) * 0.28f
                    }
                }
            }
        }
    }
    return smoothed
}

@Composable
private fun rememberMidPulse(playing: Boolean, midEnergy: Float): Float {
    var smoothed by remember { mutableFloatStateOf(0f) }
    val latestMid = rememberUpdatedState(midEnergy)
    val latestPlaying = rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                val target = if (latestPlaying.value) latestMid.value.coerceIn(0f, 1f) else 0f
                smoothed += (target - smoothed) * 0.32f
            }
        }
    }
    return smoothed
}

@Composable
private fun SingleWave(
    playing: Boolean,
    waveform: ByteArray,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) {
                    val dt = ((now - last).coerceAtMost(40)) / 1000f
                    phase += dt * if (playing) 2.4f else 1.15f
                }
                last = now
            }
        }
    }
    Canvas(modifier = modifier) {
        val samples = waveSamples(waveform, playing, phase)
        drawWaveBold(samples, playing, phase)
    }
}

private fun waveSamples(waveform: ByteArray, playing: Boolean, phase: Float): FloatArray {
    val n = 140
    val out = FloatArray(n)
    if (playing && waveform.size >= n) {
        val step = waveform.size / n.toFloat()
        for (i in 0 until n) {
            val idx = (i * step).toInt() % waveform.size
            val raw = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
            val x = i / (n - 1f)
            val shimmer = sin((x * 10f + phase * 2.2f) * Math.PI).toFloat() * 0.08f
            out[i] = (raw * 0.95f + shimmer).coerceIn(-1f, 1f)
        }
        return out
    }
    val breath = 0.55f + 0.45f * sin(phase * 0.9).toFloat()
    for (i in 0 until n) {
        val x = i / (n - 1f)
        val envelope = (0.25f + 0.75f * sin(x * Math.PI).toFloat()) * breath
        out[i] = (
            sin((x * 7.2 + phase * 1.6) * Math.PI) * 0.5 +
                sin((x * 14.5 + phase * 2.1) * Math.PI) * 0.28 +
                sin((x * 28.0 + phase * 2.8) * Math.PI) * 0.14 +
                sin((x * 42.0 + phase * 3.4) * Math.PI) * 0.07
            ).toFloat() * envelope * 0.62f
    }
    return out
}

private fun DrawScope.drawWaveBold(samples: FloatArray, playing: Boolean, phase: Float) {
    if (samples.isEmpty()) return
    val path = Path()
    val last = samples.lastIndex.coerceAtLeast(1)
    val midY = size.height / 2f
    val amp = size.height * if (playing) 0.46f else 0.38f
    samples.forEachIndexed { i, sample ->
        val x = size.width * i / last
        val y = midY + sample * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val pulse = (0.75f + 0.25f * sin(phase * 2.0).toFloat())
    drawPath(
        path,
        AeroRed.copy(alpha = 0.18f * pulse),
        style = Stroke(width = if (playing) 28f else 20f, cap = StrokeCap.Round)
    )
    drawPath(
        path,
        AeroRedGlow.copy(alpha = 0.5f * pulse),
        style = Stroke(width = if (playing) 12f else 9f, cap = StrokeCap.Round)
    )
    drawPath(
        path,
        Color.White.copy(alpha = 0.95f),
        style = Stroke(width = if (playing) 2.8f else 2.2f, cap = StrokeCap.Round)
    )
}

@Composable
private fun BalanceRow(
    pan: Float,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "БАЛАНС",
            color = AeroMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BalanceLabel(
                letter = "L",
                title = "Короб без обработки",
                accent = null,
                alignEnd = false,
                modifier = Modifier
                    .widthIn(min = 170.dp)
                    .weight(0.9f)
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
                title = "Короб с обработкой",
                accent = "AEROCELL",
                alignEnd = true,
                modifier = Modifier
                    .widthIn(min = 170.dp)
                    .weight(0.9f)
            )
        }
    }
}

@Composable
private fun BalanceLabel(
    letter: String,
    title: String,
    accent: String?,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AeroPanelSoft)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        if (!alignEnd) {
            LetterBadge(letter)
            Spacer(Modifier.width(10.dp))
            LabelTexts(title, accent, TextAlign.Start)
        } else {
            LabelTexts(title, accent, TextAlign.End)
            Spacer(Modifier.width(10.dp))
            LetterBadge(letter)
        }
    }
}

@Composable
private fun LetterBadge(letter: String) {
    Text(
        text = letter,
        color = AeroRedGlow,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        modifier = Modifier.shadow(8.dp, spotColor = AeroRed, ambientColor = AeroRed)
    )
}

@Composable
private fun LabelTexts(title: String, accent: String?, align: TextAlign) {
    Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align
        )
        if (accent != null) {
            Text(
                text = accent,
                color = AeroRedGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                textAlign = align
            )
        }
    }
}

@Composable
private fun BalanceFader(
    pan: Float,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var autoInvite by remember { mutableStateOf(true) }
    var manualEpoch by remember { mutableLongStateOf(0L) }
    val infinite = rememberInfiniteTransition(label = "balanceInvite")
    val invitePan by infinite.animateFloat(
        initialValue = -0.38f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "invitePan"
    )
    val invitePulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "invitePulse"
    )
    LaunchedEffect(autoInvite, manualEpoch) {
        if (!autoInvite) {
            delay(5 * 60 * 1000L)
            autoInvite = true
        }
    }
    val visualPan = if (autoInvite && !dragging) invitePan else pan
    val latest = rememberUpdatedState(onPan)
    Box(
        modifier = modifier.pointerInput(Unit) {
            fun emit(x: Float) {
                val t = (x / size.width.toFloat()).coerceIn(0f, 1f)
                latest.value(-1f + t * 2f)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                autoInvite = false
                manualEpoch = System.currentTimeMillis()
                dragging = true
                emit(down.position.x)
                drag(down.id) { change ->
                    emit(change.position.x)
                    change.consume()
                }
                dragging = false
                manualEpoch = System.currentTimeMillis()
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = 4.dp.toPx()
            val y = size.height / 2f
            val mid = size.width / 2f
            val t = ((visualPan + 1f) / 2f).coerceIn(0f, 1f)
            val thumbX = t * size.width
            val pulse = if (autoInvite && !dragging) invitePulse else 1f
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
            drawCircle(
                color = AeroRed.copy(alpha = 0.28f * pulse),
                radius = 18.dp.toPx() * pulse,
                center = Offset(thumbX, y)
            )
            drawCircle(
                color = AeroSilver,
                radius = 11.dp.toPx() * (0.92f + 0.08f * pulse),
                center = Offset(thumbX, y)
            )
            drawCircle(
                color = AeroRedGlow,
                radius = 11.dp.toPx() * (0.92f + 0.08f * pulse),
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
            CircleControl(size = 42.dp, filled = false, onClick = onPrevious) {
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
            CircleControl(size = 42.dp, filled = false, onClick = onStop) {
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
