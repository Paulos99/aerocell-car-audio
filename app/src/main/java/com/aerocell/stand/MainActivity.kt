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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aerocell.stand.ui.StandScreen
import com.aerocell.stand.ui.theme.AerocellTheme
import com.aerocell.stand.ui.theme.AeroBg

class MainActivity : ComponentActivity() {
    private val viewModel: StandViewModel by viewModels()

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            AerocellTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AeroBg) {
                    val state by viewModel.state.collectAsState()
                    StandScreen(
                        state = state,
                        onSelectTrack = viewModel::selectTrack,
                        onPrevious = viewModel::previousTrack,
                        onPlay = viewModel::play,
                        onPause = viewModel::pause,
                        onStop = viewModel::stop,
                        onPan = viewModel::setPan,
                        onVolume = viewModel::setVolume,
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
                        }
                    )
                }
            }
        }
    }

    /** true, пока «съедаем» жест, который разбудил стенд. */
    private var swallowWakeGesture = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN && viewModel.state.value.idleActive) {
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
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
