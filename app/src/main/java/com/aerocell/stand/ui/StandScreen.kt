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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.aerocell.stand.wifi.WifiNetwork
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aerocell.stand.viz.StageFx
import com.aerocell.stand.viz.rememberStageFx
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.BuildConfig
import com.aerocell.stand.StandUiState
import com.aerocell.stand.audio.AudioFeed
import com.aerocell.stand.audio.StandTrack
import com.aerocell.stand.upload.UploadUiState
import com.aerocell.stand.web.WebTarget
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
import kotlin.math.abs
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
    onVolume: (Float) -> Unit,
    uploadState: UploadUiState = UploadUiState(),
    onOpenUpload: () -> Unit = {},
    onCloseUpload: () -> Unit = {},
    onCancelUpload: () -> Unit = {},
    onSelectCustomTrack: () -> Unit = {},
    onLogoLongPress: () -> Unit = {},
    onDismissServiceMenu: () -> Unit = {},
    onShowIdle: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onStartUpdate: () -> Unit = {},
    onExitKiosk: () -> Unit = {},
    onResumeKiosk: () -> Unit = {},
    onClearKiosk: () -> Unit = {},
    onHintPan: (Float) -> Unit = {},
    onFaderBoundsInWindow: (Rect) -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onCloseHelp: () -> Unit = {},
    onOpenWeb: (WebTarget) -> Unit = {},
    onCloseWeb: () -> Unit = {},
    onOpenWifi: () -> Unit = {},
    onCloseWifi: () -> Unit = {},
    onRescanWifi: () -> Unit = {},
    onConnectWifi: (WifiNetwork, String) -> Unit = { _, _ -> },
    onOpenWifiSettings: () -> Unit = {},
    onVizIntensity: (Int) -> Unit = {},
    audioFeed: AudioFeed? = null
) {
    var rowBounds by remember { mutableStateOf<Rect?>(null) }
    var faderBounds by remember { mutableStateOf<Rect?>(null) }
    var stageBounds by remember { mutableStateOf<Rect?>(null) }
    val hintVisible = state.onboarding.hintVisible && !state.idleActive && state.web == null
    val fx = rememberStageFx(
        feed = audioFeed,
        playing = state.playing && !state.idleActive,
        pan = state.pan,
        intensity = state.vizIntensity / 100f
    )
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        // Фоновый слой: свечение, короба, волна и логотип. Только его искажают ударные волны,
        // кнопки, плашки и плеер лежат выше и не гнутся.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { renderEffect = fx.renderEffect() }
                .background(AeroBg)
        ) {
            stageBounds?.let { r ->
                ComparisonStage(
                    playing = state.playing,
                    waveform = state.waveform,
                    fx = fx,
                    modifier = Modifier
                        .offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                )
            }
            Image(
                painter = painterResource(R.drawable.aerocell_qp_logo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 4.dp)
                    .fillMaxWidth(0.92f)
                    .height(195.dp),
                contentScale = ContentScale.Fit
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Reserve only a slim top gap — logo is drawn in an overlay above everything
            Spacer(Modifier.height(28.dp))
            // Место под сцену; сама сцена рисуется в фоновом слое ровно здесь.
            Spacer(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { stageBounds = it.boundsInRoot() }
            )
            Spacer(Modifier.height(6.dp))
            BalanceRow(
                pan = state.pan,
                onPan = onPan,
                hintActive = hintVisible,
                fx = fx,
                onFaderPositioned = { inRoot, inWindow ->
                    faderBounds = inRoot
                    onFaderBoundsInWindow(inWindow)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rowBounds = it.boundsInRoot() }
            )
            Spacer(Modifier.height(8.dp))
            UpdateBar(
                state = state,
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
                onVolume = onVolume,
                uploadState = uploadState,
                onOpenUpload = onOpenUpload,
                onSelectCustomTrack = onSelectCustomTrack
            )
        }
        // Логотип нарисован в фоновом слое; здесь только невидимая зона длинного нажатия для служебного меню.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp)
                .fillMaxWidth(0.5f)
                .height(195.dp)
                .zIndex(10f)
                .semantics { contentDescription = "aerocell QP" }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLogoLongPress() })
                }
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = AeroMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 90.dp, end = 24.dp)
                .zIndex(11f)
        )
        // Сайт STP и калькулятор: две компактные кнопки в левом верхнем углу, над коробом AEROCELL.
        WebLinkButtons(
            links = state.links,
            onOpen = onOpenWeb,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 14.dp, start = 20.dp)
                .zIndex(12f)
        )
        // Плашка обновления у верхнего края, левее кнопки «?» (64 dp + отступы).
        UpdatePill(
            state = state,
            onClick = onStartUpdate,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 20.dp, end = 104.dp)
                .zIndex(12f)
        )
        OwnTrackModal(state = uploadState, onClose = onCloseUpload)
        UploadProgressOverlay(state = uploadState, onCancel = onCancelUpload, modifier = Modifier.zIndex(26f))
        if (state.serviceMenuVisible) {
            ServiceMenuDialog(
                state = state,
                onDismiss = onDismissServiceMenu,
                onShowIdle = onShowIdle,
                onCheckUpdates = onCheckUpdates,
                onStartUpdate = onStartUpdate,
                onOpenWifi = onOpenWifi,
                onExitKiosk = onExitKiosk,
                onResumeKiosk = onResumeKiosk,
                onClearKiosk = onClearKiosk,
                onVizIntensity = onVizIntensity
            )
        }
        if (state.wifi.open) {
            WifiDialog(
                wifi = state.wifi,
                onDismiss = onCloseWifi,
                onRescan = onRescanWifi,
                onConnect = onConnectWifi,
                onOpenSettings = onOpenWifiSettings
            )
        }
        HintOverlay(
            visible = hintVisible,
            balanceRowBounds = rowBounds,
            faderBounds = faderBounds,
            onPan = onHintPan,
            modifier = Modifier.zIndex(50f)
        )
        HelpButton(
            onClick = onOpenHelp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 0.dp, end = 0.dp)
                .zIndex(55f)
        )
        HelpPanel(
            visible = state.onboarding.helpOpen && !state.idleActive,
            onClose = onCloseHelp,
            modifier = Modifier.zIndex(60f)
        )
        // Окно с сайтом поверх всего, кроме экрана простоя и обновления.
        // Это слой в том же окне, а не Dialog: касания идут через MainActivity и продлевают таймер простоя.
        state.web?.let { panel ->
            if (!state.idleActive) {
                WebPanelOverlay(
                    panel = panel,
                    onClose = onCloseWeb,
                    modifier = Modifier.zIndex(70f)
                )
            }
        }
        IdleOverlay(
            active = state.idleActive,
            videos = state.idleVideos,
            videoVolume = state.idleVideoVolume,
            modifier = Modifier.zIndex(100f)
        )
        UpdateScreenOverlay(
            screen = state.updateScreen,
            modifier = Modifier.zIndex(200f)
        )
    }
}

@Composable
private fun ComparisonStage(
    playing: Boolean,
    waveform: ByteArray,
    fx: StageFx,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PulsingSpeaker(
            resId = R.drawable.speaker_aerocell_genio,
            contentDescription = "Короб AEROCELL",
            scale = { fx.scaleL },
            glow = { fx.glowL },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 4.dp)
                .onGloballyPositioned { c ->
                    // Картинка 1024×1024 вписана по центру: центр динамика (656, 505), радиус короба 0.46 стороны.
                    val b = c.boundsInRoot()
                    val side = minOf(b.width, b.height)
                    val x0 = b.left + (b.width - side) / 2f
                    val y0 = b.top + (b.height - side) / 2f
                    val cx = x0 + side * 656f / 1024f
                    val cy = y0 + side * 505f / 1024f
                    val r = side * 0.46f
                    fx.cone = Rect(cx - r, cy - r, cx + r, cy + r)
                }
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
                energy = { fx.env },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
        }
        PulsingSpeaker(
            resId = R.drawable.speaker_right,
            contentDescription = "Короб без обработки",
            scale = { fx.scaleR },
            glow = { fx.glowR },
            silver = true,
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
    scale: () -> Float,
    glow: () -> Float,
    modifier: Modifier = Modifier,
    silver: Boolean = false
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Мягкая подсветка за коробом: у AEROCELL красная и ярче на ударах низов, у короба без обработки тусклая серая.
        // Радиальный градиент без резких краёв; рисуется и за пределами ячейки, слой сцены это позволяет.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        ) {
            val g = glow().coerceIn(0f, 1.2f)
            if (g <= 0.01f) return@Canvas
            val c = Offset(size.width / 2f, size.height / 2f)
            if (silver) {
                val r = size.minDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color(0xFFC8C8CD).copy(alpha = (0.16f * g).coerceIn(0f, 0.22f)),
                        0.55f to Color(0xFF96969B).copy(alpha = (0.07f * g).coerceIn(0f, 0.1f)),
                        1f to Color.Transparent,
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            } else {
                val r = size.minDimension * (0.70f + 0.10f * g)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color(0xFFFF2A2A).copy(alpha = (0.62f * g).coerceIn(0f, 0.7f)),
                        0.32f to Color(0xFFE81212).copy(alpha = (0.40f * g).coerceIn(0f, 0.46f)),
                        0.62f to Color(0xFFB00000).copy(alpha = (0.16f * g).coerceIn(0f, 0.2f)),
                        1f to Color.Transparent,
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            }
        }
        Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer {
                    val sc = scale()
                    scaleX = sc
                    scaleY = sc
                },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SingleWave(
    playing: Boolean,
    waveform: ByteArray,
    energy: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    val playingNow = rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) {
                    val dt = ((now - last).coerceAtMost(40)) / 1000f
                    phase += dt * if (playingNow.value) 2.4f else 1.15f
                }
                last = now
            }
        }
    }
    // Буферы создаём один раз: в кадре ничего не выделяем.
    val samples = remember { FloatArray(WAVE_N) }
    val path = remember { Path() }
    val paints = remember { WavePaints() }
    Canvas(modifier = modifier) {
        waveSamples(waveform, playing, phase, samples)
        drawWaveBold(samples, playing, phase, path, energy().coerceIn(0f, 1f), paints)
    }
}

private const val WAVE_N = 140

/** Волна как в 1.5.0: 140 точек из waveform плеера, в паузе — дыхание. */
private fun waveSamples(waveform: ByteArray, playing: Boolean, phase: Float, out: FloatArray) {
    val n = out.size
    if (playing && waveform.size >= n) {
        val step = waveform.size / n.toFloat()
        for (i in 0 until n) {
            val idx = (i * step).toInt() % waveform.size
            val raw = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
            val x = i / (n - 1f)
            val shimmer = sin((x * 10f + phase * 2.2f) * Math.PI).toFloat() * 0.08f
            out[i] = (raw * 0.95f + shimmer).coerceIn(-1f, 1f)
        }
        return
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
}

/** Кисти неоновой волны. Создаются один раз; размытие — настоящий bloom через BlurMaskFilter. */
private class WavePaints {
    private fun stroke(color: Int, blur: Float) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = color
        if (blur > 0f) maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val bloomWide = stroke(0xFFFF1A1A.toInt(), 26f)
    val bloomMid = stroke(0xFFFF3B3B.toInt(), 9f)
    val coreRed = stroke(0xFFFF4A4A.toInt(), 0f)
    val heat = stroke(0xFFFFE6E0.toInt(), 3f)
    val coreWhite = stroke(0xFFFFFFFF.toInt(), 0f)
    val peakGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        maskFilter = android.graphics.BlurMaskFilter(9f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val peakDot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
}

private fun alpha255(a: Float): Int = (a.coerceIn(0f, 1f) * 255f).toInt()

/**
 * Форма и движение волны как в 1.5.0; поверх — неон: широкий красный bloom, красная сердцевина,
 * горячая белая жила и белые блики на пиках. energy — огибающая низов, слегка подкачивает свечение.
 */
private fun DrawScope.drawWaveBold(
    samples: FloatArray,
    playing: Boolean,
    phase: Float,
    path: Path,
    energy: Float,
    p: WavePaints
) {
    if (samples.isEmpty()) return
    path.reset()
    val last = samples.lastIndex.coerceAtLeast(1)
    val midY = size.height / 2f
    val amp = size.height * if (playing) 0.46f else 0.38f
    for (i in samples.indices) {
        val x = size.width * i / last
        val y = midY + samples[i] * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val pulse = (0.8f + 0.2f * sin(phase * 2.0).toFloat())
    val boost = if (playing) 0.75f + 0.45f * energy else 0.6f
    val ap = path.asAndroidPath()
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        p.bloomWide.strokeWidth = if (playing) 30f else 22f
        p.bloomWide.alpha = alpha255(0.55f * pulse * boost)
        nc.drawPath(ap, p.bloomWide)
        p.bloomMid.strokeWidth = if (playing) 12f else 9f
        p.bloomMid.alpha = alpha255(0.85f * pulse * boost)
        nc.drawPath(ap, p.bloomMid)
        p.coreRed.strokeWidth = if (playing) 6f else 4.5f
        p.coreRed.alpha = alpha255(0.95f)
        nc.drawPath(ap, p.coreRed)
        p.heat.strokeWidth = if (playing) 4.5f else 3.5f
        p.heat.alpha = alpha255(0.55f * boost)
        nc.drawPath(ap, p.heat)
        p.coreWhite.strokeWidth = if (playing) 2.4f else 2f
        p.coreWhite.alpha = 255
        nc.drawPath(ap, p.coreWhite)
        // Белые блики на пиках волны.
        for (i in 2 until samples.size - 2) {
            val a = abs(samples[i])
            if (a > 0.40f && a >= abs(samples[i - 1]) && a >= abs(samples[i + 1]) && a > abs(samples[i - 2])) {
                val x = size.width * i / last
                val y = midY + samples[i] * amp
                val k = (a * pulse * boost).coerceIn(0f, 1f)
                p.peakGlow.alpha = alpha255(0.75f * k)
                nc.drawCircle(x, y, 9f + 6f * k, p.peakGlow)
                p.peakDot.alpha = alpha255(0.95f * k)
                nc.drawCircle(x, y, 2.6f + 1.4f * k, p.peakDot)
            }
        }
    }
}

@Composable
private fun BalanceRow(
    pan: Float,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier,
    hintActive: Boolean = false,
    fx: StageFx? = null,
    onFaderPositioned: (Rect, Rect) -> Unit = { _, _ -> }
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
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
                .height(FaderTouchHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Плашки по ширине подписи, каждая у своего края; всё остальное место отдаём ползунку.
            BalanceLabel(
                letter = "L",
                title = "Короб с обработкой",
                accent = "AEROCELL",
                alignEnd = false,
                highlighted = hintActive && pan < -0.3f,
                meter = fx?.let { f -> { f.meterL } }
            )
            BalanceFader(
                pan = pan,
                onPan = onPan,
                hintActive = hintActive,
                halo = fx?.let { f -> { f.halo } },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned {
                        // Подсказке нужна видимая полоса, а касаниям — вся зона с запасом.
                        val pad = FaderTouchSidePad.value * density
                        val root = it.boundsInRoot()
                        val track = Rect(root.left + pad, root.top, root.right - pad, root.bottom)
                        onFaderPositioned(track, it.boundsInWindow())
                    }
            )
            BalanceLabel(
                letter = "R",
                title = "Короб без обработки",
                accent = null,
                alignEnd = true,
                highlighted = hintActive && pan > 0.3f
            )
        }
    }
}

/** Невидимая зона касания ползунка: по высоте и с запасом по краям полосы. */
private val FaderTouchHeight = 72.dp
private val FaderTouchSidePad = 20.dp

@Composable
private fun BalanceLabel(
    letter: String,
    title: String,
    accent: String?,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    meter: (() -> Float)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AeroPanelSoft)
            .drawBehind {
                // Индикатор уровня: плашка заполняется слева, как в прототипе.
                val m = meter?.invoke()?.coerceIn(0f, 1f) ?: 0f
                if (m > 0.002f) {
                    val w = size.width * m
                    drawRect(AeroRed.copy(alpha = 0.72f), size = Size(w * 0.55f, size.height))
                    drawRect(Color(0xFFFF2828).copy(alpha = 0.40f), topLeft = Offset(w * 0.55f, 0f), size = Size(w * 0.30f, size.height))
                    drawRect(Color(0xFFB00000).copy(alpha = 0.18f), topLeft = Offset(w * 0.85f, 0f), size = Size(w * 0.15f, size.height))
                }
            }
            .border(
                if (highlighted) 1.5.dp else 1.dp,
                if (highlighted) AeroRed else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp)
            )
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
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
    modifier: Modifier = Modifier,
    hintActive: Boolean = false,
    halo: (() -> Float)? = null
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
    // Во время подсказки ползунок ведёт палец, своё «приглашение» не рисуем.
    val inviting = autoInvite && !dragging && !hintActive
    val visualPan = if (inviting) invitePan else pan
    val latest = rememberUpdatedState(onPan)
    Box(
        modifier = modifier.pointerInput(Unit) {
            fun emit(x: Float) {
                // Зона касания шире полосы на FaderTouchSidePad с каждой стороны.
                val pad = FaderTouchSidePad.toPx()
                val track = (size.width - 2 * pad).coerceAtLeast(1f)
                val t = ((x - pad) / track).coerceIn(0f, 1f)
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
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = FaderTouchSidePad)) {
            val trackH = 4.dp.toPx()
            val y = size.height / 2f
            val mid = size.width / 2f
            val t = ((visualPan + 1f) / 2f).coerceIn(0f, 1f)
            val thumbX = t * size.width
            val pulse = if (inviting) invitePulse else 1f
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
            val h = halo?.invoke() ?: 0f
            if (h > 0f) {
                // Ореол бегунка по уровню музыки.
                drawCircle(
                    color = AeroRedGlow.copy(alpha = (0.22f * h).coerceIn(0f, 0.4f)),
                    radius = (18.dp.toPx() + 14.dp.toPx() * h),
                    center = Offset(thumbX, y)
                )
            }
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
    onVolume: (Float) -> Unit,
    uploadState: UploadUiState = UploadUiState(),
    onOpenUpload: () -> Unit = {},
    onSelectCustomTrack: () -> Unit = {}
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
                        active = track == state.track && !uploadState.customSelected,
                        onClick = { onSelectTrack(track) }
                    )
                }
                // Одна кнопка «СВОЙ ТРЕК» того же размера, что жанры: без трека открывает QR,
                // с треком включает его. Загрузить другой — маленький «+» рядом.
                // Раньше здесь было две кнопки, и пятая не влезала: Row сжимал её почти до нуля,
                // текст переносился по букве в строку, и вырастала вся нижняя панель.
                val hasCustom = uploadState.customTrack != null
                GenrePill(
                    label = "СВОЙ ТРЕК",
                    active = hasCustom && uploadState.customSelected,
                    onClick = if (hasCustom) onSelectCustomTrack else onOpenUpload
                )
                if (hasCustom) {
                    AddTrackPill(onClick = onOpenUpload)
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
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Компактная круглая кнопка «загрузить другой трек», высотой как жанры. */
@Composable
private fun AddTrackPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(33.dp)
            .clip(CircleShape)
            .background(AeroPanelSoft)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Загрузить другой трек",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
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
