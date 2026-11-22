package com.aerocell.stand.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aerocell.stand.WebPanelState
import com.aerocell.stand.ui.theme.AeroMuted
import com.aerocell.stand.ui.theme.AeroPanel
import com.aerocell.stand.ui.theme.AeroPanelSoft
import com.aerocell.stand.ui.theme.AeroRed
import com.aerocell.stand.ui.theme.AeroRedGlow
import com.aerocell.stand.web.DomainAllowlist
import com.aerocell.stand.web.WebTarget
import kotlinx.coroutines.delay

/**
 * Держит окно с сайтом, пока оно анимируется: вырастает из плитки меню и уходит в неё обратно.
 * WebView живёт, пока окно на экране; после закрытия он уничтожается, куки и кэш стираются.
 */
@Composable
fun WebPanelHost(
    panel: WebPanelState?,
    idle: Boolean,
    originFor: (WebTarget) -> Rect?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf<WebPanelState?>(null) }
    val growAnim = remember { Animatable(0f) }
    LaunchedEffect(panel) {
        if (panel != null) {
            shown = panel
            growAnim.animateTo(1f, menuSpring())
        } else if (shown != null) {
            if (idle) growAnim.snapTo(0f) else growAnim.animateTo(0f, menuSpring())
            shown = null
        }
    }
    val current = shown ?: return
    WebPanelOverlay(
        panel = current,
        grow = { growAnim.value },
        origin = originFor(current.target),
        onClose = onClose,
        modifier = modifier
    )
}

/**
 * Окно с сайтом: ~90 % экрана, скруглённые углы, затемнение под ним.
 * Ходить можно только по доменам из белого списка; звонки, почта, чужие приложения,
 * скачивания и выбор файлов заблокированы. При закрытии чистим куки, кэш и хранилище.
 */
@Composable
fun WebPanelOverlay(
    panel: WebPanelState,
    grow: () -> Float,
    origin: Rect?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var progress by remember(panel.openId) { mutableIntStateOf(0) }
    var loading by remember(panel.openId) { mutableStateOf(true) }
    var canGoBack by remember(panel.openId) { mutableStateOf(false) }
    var failed by remember(panel.openId) { mutableStateOf(false) }
    var failedUrl by remember(panel.openId) { mutableStateOf<String?>(null) }
    var blockedTick by remember(panel.openId) { mutableIntStateOf(0) }

    val webView = remember(panel.openId) {
        createKioskWebView(
            context = context,
            allowed = panel.allowedDomains,
            onProgress = { progress = it },
            onLoading = { loading = it },
            onHistory = { canGoBack = it },
            onMainFrameError = { url ->
                failed = true
                failedUrl = url
                loading = false
            },
            onBlocked = { blockedTick += 1 }
        ).also { it.loadUrl(panel.url) }
    }

    DisposableEffect(webView) {
        onDispose { destroyAndWipe(webView) }
    }

    // «←» в шапке и аппаратная «Назад» делают одно и то же: шаг назад по сайту,
    // а с первой страницы закрывают окно в плитку меню. Историю спрашиваем у WebView
    // в момент нажатия, а не по сохранённому флагу: SPA и #якоря меняют её без загрузки.
    val goBack: () -> Unit = {
        if (failed || !stepBack(webView)) onClose()
    }
    BackHandler(enabled = true) { goBack() }

    var modalBounds by remember { mutableStateOf<Rect?>(null) }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Затемнение проявляется вместе с ростом окна.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = MenuMotion.SCRIM_ALPHA * grow().coerceIn(0f, 1f) }
                .background(Color.Black)
                // Касания мимо окна не проходят к меню, но и окно не закрывают: только крестик.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        // Внешний бокс без трансформаций: по нему знаем итоговое место окна. Окно на весь экран.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { modalBounds = it.boundsInRoot() }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val g = grow()
                    val end = modalBounds?.toMotion()
                    val start = origin?.toMotion()
                    if (end != null && start != null && end.width > 0f && end.height > 0f) {
                        val r = MenuMotion.lerp(start, end, g)
                        scaleX = (r.width / end.width).coerceAtLeast(0.01f)
                        scaleY = (r.height / end.height).coerceAtLeast(0.01f)
                        translationX = r.centerX - end.centerX
                        translationY = r.centerY - end.centerY
                    } else {
                        val s = MenuMotion.lerp(0.9f, 1f, g)
                        scaleX = s
                        scaleY = s
                    }
                    alpha = MenuMotion.contentAlpha(g, speed = 2.5f)
                    // Пока растёт из плитки, углы скруглены как у плитки; на весь экран — прямые.
                    // Масштаб по осям разный, поэтому радиус делим на него, чтобы угол выглядел ровно.
                    val radius = MenuMotion.lerp(MenuTileCorner.toPx(), 0f, g.coerceIn(0f, 1f))
                    shape = RoundedCornerShape(radius / maxOf(minOf(scaleX, scaleY), 0.05f))
                    clip = true
                }
                .background(AeroPanel)
        ) {
            WebHeader(
                title = panel.title,
                canGoBack = canGoBack && !failed,
                onBack = goBack,
                onClose = onClose
            )
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (loading && !failed) {
                    LinearProgressIndicator(
                        progress = { (progress.coerceIn(5, 100)) / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = AeroRed,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (failed) {
                    OfflineScreen(
                        onRetry = {
                            failed = false
                            loading = true
                            progress = 0
                            webView.loadUrl(failedUrl ?: panel.url)
                        }
                    )
                }
                BlockedToast(tick = blockedTick, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        }
    }
}

@Composable
private fun WebHeader(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Окно на весь экран: шапка не заходит под вырез и системные зоны.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(80.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Назад по страницам сайта. Зона касания 72 dp, как у крестика.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                // Всегда активна: по истории сайта или закрывает окно.
                .clickable(onClick = onBack)
                .semantics { contentDescription = if (canGoBack) "Назад" else "Назад в меню" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Большой крестик: зона касания 76 dp, видимый круг 56 dp.
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Закрыть" },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(AeroRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun OfflineScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AeroPanel)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = AeroRedGlow,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Нет интернета. Проверьте Wi-Fi",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Страница не открылась. Попробуйте ещё раз через пару секунд.",
            color = AeroMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(AeroRed)
                .clickable(onClick = onRetry)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Попробовать снова", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Короткая плашка, если посетитель нажал ссылку, которая ведёт за пределы сайта STP. */
@Composable
private fun BlockedToast(tick: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        visible = true
        delay(2_600)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text("Эта ссылка на стенде не открывается", color = Color.White, fontSize = 15.sp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createKioskWebView(
    context: Context,
    allowed: Set<String>,
    onProgress: (Int) -> Unit,
    onLoading: (Boolean) -> Unit,
    onHistory: (Boolean) -> Unit,
    onMainFrameError: (String?) -> Unit,
    onBlocked: () -> Unit
): WebView {
    val webView = WebView(context)
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        setGeolocationEnabled(false)
        // target=_blank и window.open открываются в этом же окне и проходят ту же проверку доменов.
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        // Ролики на сайте не включаются сами и не перебивают музыку стенда.
        mediaPlaybackRequiresUserGesture = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = false
        displayZoomControls = false
        setSupportZoom(false)
        @Suppress("DEPRECATION")
        saveFormData = false
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    // Без долгого нажатия: иначе выделение текста предлагает «Поделиться» и «Поиск в интернете».
    webView.isLongClickable = false
    webView.isHapticFeedbackEnabled = false
    webView.setOnLongClickListener { true }
    // Скачивания запрещены.
    webView.setDownloadListener { _, _, _, _, _ -> onBlocked() }

    webView.webViewClient = object : WebViewClient() {
        private var firstPageDone = false

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url?.toString()
            val scheme = request.url?.scheme?.lowercase()
            if (!request.isForMainFrame) {
                // Встроенные фреймы (карты, капча) можно, но только по http(s).
                return scheme != "http" && scheme != "https"
            }
            if (DomainAllowlist.isAllowed(url, allowed)) return false
            onBlocked()
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            onLoading(true)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onLoading(false)
            // Первая загрузка: JS-переадресации стартовой ссылки не должны быть шагом «назад»,
            // иначе стрелка вернёт на адрес, который тут же снова уйдёт вперёд.
            if (!firstPageDone && url != null && url != "about:blank") {
                firstPageDone = true
                view.clearHistory()
            }
            onHistory(view.canGoBack())
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            onHistory(view.canGoBack())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onMainFrameError(request.url?.toString())
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            // Экран ошибки только если не открылась сама страница, а не картинка на ней.
            if (view.url.isNullOrEmpty() || view.url == "about:blank" || error.url == view.url) {
                onMainFrameError(error.url)
            }
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onProgress(newProgress)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            // Выбор файлов на стенде не нужен.
            filePathCallback.onReceiveValue(null)
            onBlocked()
            return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }
    }
    return webView
}

/**
 * Шаг назад по истории WebView. Пропускает записи с тем же адресом, что и текущая страница
 * (повторная загрузка, переадресация на себя), и about:blank. false — назад идти некуда.
 */
private fun stepBack(webView: WebView): Boolean {
    val list = webView.copyBackForwardList()
    val current = list.currentIndex
    if (current <= 0) return false
    val currentUrl = list.currentItem?.url
    var target = current - 1
    while (target >= 0) {
        val url = list.getItemAtIndex(target)?.url
        if (url != null && url != "about:blank" && url != currentUrl) break
        target--
    }
    if (target < 0) return false
    webView.goBackOrForward(target - current)
    return true
}

/** Закрыли окно: останавливаем страницу и стираем всё, что сайт успел сохранить. */
private fun destroyAndWipe(webView: WebView) {
    try {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    } catch (_: Throwable) {
    }
    try {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    } catch (_: Throwable) {
    }
}
