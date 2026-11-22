package com.aerocell.stand

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.aerocell.stand.kiosk.KioskController
import com.aerocell.stand.ui.StandScreen
import com.aerocell.stand.ui.theme.AerocellTheme
import com.aerocell.stand.ui.theme.AeroBg
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: StandViewModel by viewModels()
    private val kiosk by lazy { KioskController(this) }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        if (kiosk.isDeviceOwner()) {
            kiosk.applyOwnerPolicies()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        lifecycleScope.launch {
            viewModel.state
                .map { Triple(it.kioskEnabled, it.kioskLockTaskDesired, it.installLockPaused) }
                .distinctUntilChanged()
                .collect { (enabled, desired, pausedForInstall) ->
                    if (enabled && desired && pausedForInstall) {
                        // Тихая установка: HyperOS отменяет её, пока экран заперт в киоске.
                        kiosk.pauseLockTaskForInstall(this@MainActivity)
                    } else if (enabled && desired) {
                        kiosk.startLockTask(this@MainActivity)
                    } else if (enabled) {
                        kiosk.stopLockTask(this@MainActivity)
                    }
                }
        }

        setContent {
            AerocellTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AeroBg) {
                    val state by viewModel.state.collectAsState()
                    val upload by viewModel.upload.collectAsState()
                    StandScreen(
                        state = state,
                        onSelectTrack = viewModel::selectTrack,
                        onPrevious = viewModel::previousTrack,
                        onPlay = viewModel::play,
                        onPause = viewModel::pause,
                        onStop = viewModel::stop,
                        onPan = viewModel::setPan,
                        onVolume = viewModel::setVolume,
                        onVizIntensity = viewModel::setVizIntensity,
                        audioFeed = viewModel.audioFeed,
                        uploadState = upload,
                        onOpenUpload = viewModel::openUpload,
                        onCloseUpload = viewModel::closeUpload,
                        onCancelUpload = viewModel::cancelUpload,
                        onSelectCustomTrack = viewModel::selectCustomTrack,
                        onLogoLongPress = viewModel::onLogoLongPress,
                        onDismissServiceMenu = viewModel::dismissServiceMenu,
                        onShowIdle = viewModel::showIdleNow,
                        onCheckUpdates = viewModel::checkUpdates,
                        onStartUpdate = {
                            val permissionIntent = viewModel.installPermissionIntent()
                            if (permissionIntent != null) {
                                startActivity(permissionIntent)
                            }
                            viewModel.startApkUpdate()
                        },
                        onExitKiosk = { viewModel.exitKioskTemporarily() },
                        onResumeKiosk = viewModel::resumeKiosk,
                        onClearKiosk = { viewModel.clearKiosk() },
                        onHintPan = viewModel::setPanFromHint,
                        onFaderBoundsInWindow = { faderBounds = it },
                        onOpenWeb = viewModel::openWeb,
                        onOpenComparison = viewModel::openComparison,
                        onBackToMenu = viewModel::backToMenu,
                        onCloseWeb = viewModel::closeWeb,
                        onOpenHelp = viewModel::openHelp,
                        onCloseHelp = viewModel::closeHelp,
                        onOpenWifi = viewModel::openWifi,
                        onCloseWifi = viewModel::closeWifi,
                        onRescanWifi = viewModel::scanWifi,
                        onConnectWifi = viewModel::connectWifi,
                        onOpenWifiSettings = {
                            val intent = viewModel.wifiSettingsIntent()
                            try {
                                startActivity(intent)
                            } catch (_: Throwable) {
                            }
                        }
                    )
                }
            }
        }
    }

    /** true, пока «съедаем» жест, который разбудил стенд. */
    private var swallowWakeGesture = false

    /** Границы ползунка баланса в координатах окна: касание по нему во время подсказки не съедаем. */
    private var faderBounds: Rect? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        val s = viewModel.state.value
        if (s.updateScreen != null) {
            // Пока идёт обновление, экран касания не принимает.
            return true
        }
        if (action == MotionEvent.ACTION_DOWN && !s.idleActive && s.onboarding.hintVisible && s.web == null &&
            s.screen == com.aerocell.stand.nav.AppScreen.COMPARISON
        ) {
            viewModel.onUserInteraction()
            viewModel.dismissHint()
            val onFader = faderBounds?.let { b ->
                // Чуть шире самого ползунка, чтобы палец посетителя попадал наверняка.
                ev.x in (b.left - 24f)..(b.right + 24f) && ev.y in (b.top - 24f)..(b.bottom + 24f)
            } ?: false
            if (!onFader) {
                swallowWakeGesture = true
                return true
            }
            return super.dispatchTouchEvent(ev)
        }
        if (action == MotionEvent.ACTION_DOWN && s.idleActive) {
            // Первое касание только будит стенд и не доходит до кнопок под экраном простоя.
            swallowWakeGesture = true
            viewModel.wakeFromIdle()
            return true
        }
        if (swallowWakeGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                swallowWakeGesture = false
            }
            return true
        }
        viewModel.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        viewModel.onResume()
        val s = viewModel.state.value
        if (s.kioskEnabled && s.kioskLockTaskDesired && !s.installLockPaused) {
            kiosk.startLockTask(this)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
