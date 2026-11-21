package com.aerocell.stand.wifi

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.aerocell.stand.kiosk.StandAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class WifiNetwork(
    val ssid: String,
    val secured: Boolean,
    /** 0..4 */
    val level: Int
)

/**
 * Подключение к Wi-Fi прямо из служебного меню, без выхода из киоска.
 * Владелец устройства может сам выдать себе доступ к геолокации (нужен для списка сетей)
 * и добавлять сети через WifiManager.addNetwork.
 */
class WifiController(private val context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, StandAdminReceiver::class.java)

    private fun isOwner() = dpm.isDeviceOwnerApp(context.packageName)

    /** Готовит права и геолокацию, без них Android не отдаёт список сетей. */
    fun prepare() {
        if (!isOwner()) return
        try {
            dpm.setPermissionGrantState(
                admin,
                context.packageName,
                Manifest.permission.ACCESS_FINE_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        } catch (t: Throwable) {
            Log.w(TAG, "grant location failed", t)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                dpm.setLocationEnabled(admin, true)
            } catch (t: Throwable) {
                Log.w(TAG, "enable location failed", t)
            }
        }
        if (!wifi.isWifiEnabled) {
            @Suppress("DEPRECATION")
            try {
                wifi.isWifiEnabled = true
            } catch (t: Throwable) {
                Log.w(TAG, "enable wifi failed", t)
            }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun currentSsid(): String? {
        return try {
            val raw = wifi.connectionInfo?.ssid ?: return null
            raw.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        } catch (_: Throwable) {
            null
        }
    }

    /** Сканирует эфир и возвращает сети по убыванию сигнала, без дублей и скрытых. */
    @Suppress("DEPRECATION")
    suspend fun scan(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        prepare()
        if (!hasLocationPermission()) throw IllegalStateException("нет доступа к геолокации, список сетей недоступен")
        try {
            wifi.startScan()
        } catch (t: Throwable) {
            Log.w(TAG, "startScan failed", t)
        }
        delay(SCAN_WAIT_MS)
        val results = try {
            wifi.scanResults
        } catch (t: SecurityException) {
            throw IllegalStateException("нет доступа к списку сетей")
        }
        results
            .filter { !it.SSID.isNullOrBlank() }
            .groupBy { it.SSID }
            .map { (ssid, list) ->
                val best = list.maxBy { it.level }
                val caps = best.capabilities.orEmpty()
                WifiNetwork(
                    ssid = ssid,
                    secured = caps.contains("WPA") || caps.contains("WEP") || caps.contains("SAE") || caps.contains("PSK"),
                    level = WifiManager.calculateSignalLevel(best.level, 5)
                )
            }
            .sortedByDescending { it.level }
    }

    /**
     * Добавляет сеть и подключается. Возвращает null при успехе или текст ошибки.
     * Ждёт подключения до [CONNECT_WAIT_MS].
     */
    @Suppress("DEPRECATION")
    suspend fun connect(network: WifiNetwork, password: String): String? = withContext(Dispatchers.IO) {
        prepare()
        if (network.secured && password.length < 8) return@withContext "Пароль Wi-Fi не короче 8 символов"
        val config = WifiConfiguration().apply {
            SSID = "\"${network.ssid}\""
            if (network.secured) {
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            } else {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }
        val id = try {
            wifi.addNetwork(config)
        } catch (t: Throwable) {
            Log.e(TAG, "addNetwork failed", t)
            -1
        }
        if (id == -1) return@withContext "Система не дала добавить сеть. Откройте настройки Wi-Fi"
        try {
            wifi.disconnect()
            wifi.enableNetwork(id, true)
            wifi.reconnect()
        } catch (t: Throwable) {
            Log.e(TAG, "enableNetwork failed", t)
            return@withContext "Не удалось подключиться: ${t.message}"
        }
        val deadline = System.currentTimeMillis() + CONNECT_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(1_000)
            if (currentSsid() == network.ssid) return@withContext null
        }
        "Не подключилось за ${CONNECT_WAIT_MS / 1000} с. Проверьте пароль"
    }

    /** Запасной путь: системная панель Wi-Fi (в киоске её покажут только после выхода из блокировки). */
    fun settingsIntent(): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val TAG = "AerocellWifi"
        private const val SCAN_WAIT_MS = 3_000L
        private const val CONNECT_WAIT_MS = 20_000L
    }
}
