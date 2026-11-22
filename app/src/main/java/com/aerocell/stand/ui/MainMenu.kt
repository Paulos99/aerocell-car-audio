package com.aerocell.stand.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerocell.stand.R
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroRedGlow
import com.aerocell.stand.web.LinksConfig
import com.aerocell.stand.web.WebTarget

/** Плитки главного меню. */
enum class MenuTile { COMPARISON, CALCULATOR, CATALOG }

internal val MenuTileCorner = 26.dp

/** Пружина переходов меню: почти без перелёта, ~350–400 мс, прерывается с сохранением скорости. */
internal fun <T> menuSpring(): SpringSpec<T> =
    spring(dampingRatio = MenuMotion.DAMPING, stiffness = MenuMotion.STIFFNESS)

/**
 * Координаты экрана сравнения без учёта анимации перехода.
 * Сцена, подсказка и шейдер считают позиции от него, поэтому масштаб перехода их не сбивает.
 */
class StageCoords {
    var coords: LayoutCoordinates? = null
}

internal val LocalStageCoords = staticCompositionLocalOf { StageCoords() }

internal fun LayoutCoordinates.boundsIn(stage: StageCoords): Rect {
    val container = stage.coords
    return if (container != null && container.isAttached && isAttached) {
        container.localBoundingBoxOf(this, clipBounds = false)
    } else {
        boundsInRoot()
    }
}

internal fun Rect.toMotion(): MotionRect = MotionRect(left, top, right, bottom)

/** Скруглённый прямоугольник внутри слоя: им обрезаем сравнение, пока оно растёт из плитки. */
internal class InsetRoundShape(private val r: MotionRect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r.left, r.top, r.right, r.bottom, CornerRadius(radius.coerceAtLeast(0f))))
}

/**
 * Главное меню: «Сравнение» на две трети ширины, справа «Калькулятор» и «Каталог».
 */
@Composable
fun MainMenu(
    links: LinksConfig,
    onOpenComparison: () -> Unit,
    onOpenWeb: (WebTarget) -> Unit,
    onLogoLongPress: () -> Unit,
    onTileBounds: (MenuTile, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFF2A0505),
                        0.45f to Color(0xFF0A0A0A),
                        1f to Color.Black,
                        center = Offset(size.width * 0.3f, size.height * 0.1f),
                        radius = size.width * 0.95f
                    )
                )
            }
    ) {
        Image(
            painter = painterResource(R.drawable.aerocell_qp_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 18.dp)
                .height(104.dp)
                .semantics { contentDescription = "aerocell QP" }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLogoLongPress() }) }
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 40.dp, end = 40.dp, top = 140.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PressableTile(
                onClick = onOpenComparison,
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .onGloballyPositioned { onTileBounds(MenuTile.COMPARISON, it.boundsInRoot()) },
                background = Brush.linearGradient(
                    listOf(Color(0xFF1C0C0C), Color(0xFF0D0D0D)),
                    start = Offset.Zero,
                    end = Offset(1400f, 1600f)
                )
            ) {
                ComparisonTileContent()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PressableTile(
                    onClick = { onOpenWeb(WebTarget.CALCULATOR) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { onTileBounds(MenuTile.CALCULATOR, it.boundsInRoot()) }
                ) {
                    SmallTileContent(
                        icon = Icons.Filled.Calculate,
                        title = links.calculator.label,
                        subtitle = "Сколько материала нужно на вашу машину"
                    )
                }
                PressableTile(
                    onClick = { onOpenWeb(WebTarget.CATALOG) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { onTileBounds(MenuTile.CATALOG, it.boundsInRoot()) }
                ) {
                    SmallTileContent(
                        icon = Icons.Filled.GridView,
                        title = links.catalog.label,
                        subtitle = "Все материалы STP для шумоизоляции"
                    )
                }
            }
        }
    }
}

/**
 * Плитка с пружинным нажатием: масштаб до 0,96, красная обводка и лёгкая подсветка.
 * Отпустили — пружина возвращает обратно с той же скоростью, без рывка.
 */
@Composable
private fun PressableTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Brush = Brush.linearGradient(listOf(AeroPanel, AeroPanel)),
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = menuSpring(),
        label = "tilePress"
    )
    val shape = RoundedCornerShape(MenuTileCorner)
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = MenuMotion.lerp(1f, MenuMotion.TILE_PRESSED_SCALE, press)
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .drawWithContent {
                drawContent()
                if (press > 0.001f) {
                    val p = press.coerceIn(0f, 1f)
                    val corner = CornerRadius(MenuTileCorner.toPx())
                    drawRoundRect(Color.White.copy(alpha = 0.07f * p), cornerRadius = corner)
                    drawRoundRect(
                        AeroRedGlow.copy(alpha = 0.9f * p),
                        cornerRadius = corner,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            },
        content = content
    )
}

@Composable
private fun BoxScope.ComparisonTileContent() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // Красное свечение за коробом AEROCELL.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to AeroRed.copy(alpha = 0.36f),
                            0.7f to Color.Transparent,
                            center = Offset(size.width * 0.32f, size.height * 0.58f),
                            radius = size.width * 0.66f
                        )
                    )
                }
        )
        Image(
            painter = painterResource(R.drawable.speaker_right),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = w * 0.534f, y = h * 0.435f)
                .size(w * 0.343f)
                .graphicsLayer { alpha = 0.55f }
        )
        Image(
            painter = painterResource(R.drawable.speaker_aerocell_genio),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = w * 0.025f, y = h * 0.31f)
                .size(w * 0.52f)
        )
        Column(Modifier.padding(start = 44.dp, top = 36.dp).widthIn(max = 440.dp)) {
            Text(
                text = "ПОСЛУШАЙТЕ РАЗНИЦУ",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                letterSpacing = 2.4.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Сравнение", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Один трек, два короба: с AEROCELL и без обработки",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 19.sp,
                lineHeight = 26.sp
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 34.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(50))
                .background(AeroRed)
                .padding(start = 34.dp, end = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("СЛУШАТЬ", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BoxScope.SmallTileContent(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 34.dp)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AeroRed.copy(alpha = 0.12f))
                .border(1.dp, AeroRedGlow.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AeroRedGlow, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text(text = title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(text = subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp, lineHeight = 23.sp)
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 28.dp, bottom = 28.dp)
            .size(52.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Круглая стрелка «назад» на сравнении: круг 56 dp, зона касания 72 dp, пружина до 0,9 и красная подсветка. */
@Composable
fun BackArrowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = menuSpring(),
        label = "backPress"
    )
    Box(
        modifier = modifier
            .size(72.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = "Назад в меню" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    val s = MenuMotion.lerp(1f, MenuMotion.ROUND_PRESSED_SCALE, press)
                    scaleX = s
                    scaleY = s
                }
                .background(
                    lerpColor(Color(0xE0141414), AeroRed.copy(alpha = 0.75f), press),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color =
    androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
