package com.aerocell.stand.ui

import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.aerocell.stand.R
import com.aerocell.stand.ui.theme.AeroRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.sin

private val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val EaseInOut = CubicBezierEasing(0.33f, 0f, 0.15f, 1f)

/** Длительность затемнения через чёрный между роликами. */
private const val GAP_FADE_MS = 420
/** Вход в режим простоя и выход из него. */
private const val ENTER_MS = 600
private const val EXIT_MS = 250
/** Отступ оверлеев от края экрана. */
private val EDGE = 48.dp

/**
 * Экран простоя поверх экрана сравнения. Касания сюда не доходят:
 * первое касание перехватывает MainActivity.dispatchTouchEvent и будит стенд.
 */
@Composable
fun IdleOverlay(
    active: Boolean,
    videos: List<File>,
    videoVolume: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = active,
        modifier = modifier,
        enter = fadeIn(tween(ENTER_MS, easing = EaseOut)),
        exit = fadeOut(tween(EXIT_MS, easing = EaseInOut))
    ) {
        val exiting = transition.targetState == EnterExitState.PostExit
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (videos.isNotEmpty()) {
                IdleVideoPlaylist(videos = videos, volume = videoVolume, exiting = exiting)
            } else {
                IdleBrandFallback()
            }
            IdleChrome()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun IdleVideoPlaylist(videos: List<File>, volume: Float, exiting: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(videos) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(videos.map { MediaItem.fromUri(Uri.fromFile(it)) })
            repeatMode = Player.REPEAT_MODE_ALL
            this.volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    // Чёрная шторка: 1 = экран чёрный. Стартуем из чёрного.
    val blackout = remember { Animatable(1f) }
    var itemIndex by remember { mutableIntStateOf(0) }
    var firstFrame by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                itemIndex++
            }

            override fun onRenderedFirstFrame() {
                firstFrame++
            }
        }
        player.addListener(listener)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }

    // Новый ролик (или первый кадр): выходим из чёрного.
    LaunchedEffect(itemIndex, firstFrame) {
        if (firstFrame > 0) blackout.animateTo(0f, tween(GAP_FADE_MS, easing = EaseOut))
    }
    // Ближе к концу ролика уходим в чёрный. При одном ролике в плейлисте — тоже.
    LaunchedEffect(player) {
        while (isActive) {
            val duration = player.duration
            val position = player.currentPosition
            if (duration > 0 && duration - position in 1..GAP_FADE_MS.toLong() && blackout.targetValue < 1f) {
                blackout.animateTo(1f, tween((duration - position).toInt().coerceAtLeast(120), easing = EaseInOut))
            }
            delay(50)
        }
    }

    // Громкость: плавное нарастание на входе, провал в паузах между роликами, затухание при касании.
    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(player) { fadeIn.animateTo(1f, tween(1_200, easing = EaseOut)) }
    val exitGain by animateFloatAsState(
        targetValue = if (exiting) 0f else 1f,
        animationSpec = tween(EXIT_MS, easing = EaseInOut),
        label = "idle-exit-gain"
    )
    val targetVolume by rememberUpdatedState(volume.coerceIn(0f, 1f))
    val latestExit by rememberUpdatedState(exitGain)
    LaunchedEffect(player) {
        while (isActive) {
            withFrameMillis {
                player.volume = targetVolume * fadeIn.value * (1f - blackout.value) * latestExit
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Заполняем экран целиком с обрезкой краёв (16:9 ролик на 16:10 планшете).
        val boxAspect = maxWidth / maxHeight
        val (w, h) = if (boxAspect > videoAspect) {
            maxWidth to maxWidth / videoAspect
        } else {
            maxHeight * videoAspect to maxHeight
        }
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            modifier = Modifier.requiredSize(w, h)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blackout.value }
                .background(Color.Black)
        )
    }
}

/** Запасной экран, пока роликов нет в кэше: логотип, красный луч, свечение. */
@Composable
private fun IdleBrandFallback() {
    val transition = rememberInfiniteTransition(label = "idle-brand")
    val sweep by transition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = EaseInOut), RepeatMode.Restart),
        label = "beam"
    )
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) phase += (now - last).coerceAtMost(40) / 1000f
                last = now
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val breath = 0.75f + 0.25f * sin(phase * 1.1f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33FF1A1A).copy(alpha = 0.2f * breath), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.minDimension * 0.85f
                )
            )
            val y = size.height * 0.62f
            // Тонкая линия-«луч» и яркий бегущий сегмент.
            drawLine(
                color = AeroRed.copy(alpha = 0.28f),
                start = Offset(size.width * 0.08f, y),
                end = Offset(size.width * 0.92f, y),
                strokeWidth = 2.dp.toPx()
            )
            val segW = size.width * 0.16f
            val x = size.width * sweep
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, AeroRed, Color(0xFFFF6A5A), AeroRed, Color.Transparent),
                    startX = x - segW / 2f,
                    endX = x + segW / 2f
                ),
                start = Offset(x - segW / 2f, y),
                end = Offset(x + segW / 2f, y),
                strokeWidth = 5.dp.toPx()
            )
        }
        Image(
            painter = painterResource(R.drawable.aerocell_qp_logo),
            contentDescription = "aerocell QP",
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp)
                .fillMaxWidth(0.62f)
                .height(260.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** Логотипы в углах и призыв внизу. Раз в несколько минут сдвигаются на пару пикселей. */
@Composable
private fun IdleChrome() {
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3 * 60_000L)
            shift = (shift + 1) % BURN_IN_STEPS.size
        }
    }
    val (dxTarget, dyTarget) = BURN_IN_STEPS[shift]
    val dx by animateFloatAsState(dxTarget, tween(2_000, easing = EaseInOut), label = "burn-dx")
    val dy by animateFloatAsState(dyTarget, tween(2_000, easing = EaseInOut), label = "burn-dy")

    val pulse = rememberInfiniteTransition(label = "cta-pulse")
    val ctaAlpha by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = EaseInOut), RepeatMode.Reverse),
        label = "cta-alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(dx.dp, dy.dp)
            .padding(EDGE)
    ) {
        Image(
            painter = painterResource(R.drawable.aerocell_qp_logo),
            contentDescription = "aerocell QP",
            modifier = Modifier
                .align(Alignment.TopStart)
                .height(64.dp)
                .width(220.dp)
                .graphicsLayer { alpha = 0.75f },
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart
        )
        Image(
            painter = painterResource(R.drawable.stp_logo),
            contentDescription = "StP",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(56.dp)
                .width(140.dp)
                .graphicsLayer { alpha = 0.75f },
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterEnd
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = ctaAlpha }
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                tint = AeroRed,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Коснитесь экрана, чтобы услышать разницу",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Небольшие сдвиги оверлеев против выгорания, в dp. */
private val BURN_IN_STEPS = listOf(
    0f to 0f, 3f to -2f, -3f to 2f, 2f to 3f, -2f to -3f
)
