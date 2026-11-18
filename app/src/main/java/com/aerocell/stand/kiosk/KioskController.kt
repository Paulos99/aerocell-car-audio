package com.aerocell.stand.kiosk

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log
import com.aerocell.stand.MainActivity

class KioskController(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent: ComponentName = ComponentName(context, StandAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun applyOwnerPolicies() {
        if (!isDeviceOwner()) return
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
        } catch (t: Throwable) {
            Log.w(TAG, "setLockTaskPackages failed", t)
        }
        try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                adminComponent,
                filter,
                ComponentName(context, MainActivity::class.java)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "addPersistentPreferredActivity failed", t)
        }
        try {
            dpm.setStatusBarDisabled(adminComponent, true)
        } catch (t: Throwable) {
            Log.w(TAG, "setStatusBarDisabled failed", t)
        }
        try {
            dpm.setKeyguardDisabled(adminComponent, true)
        } catch (t: Throwable) {
            Log.w(TAG, "setKeyguardDisabled failed", t)
        }
        try {
            val plugged = BatteryManager.BATTERY_PLUGGED_AC or
                BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            dpm.setGlobalSetting(
                adminComponent,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                plugged.toString()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "setGlobalSetting STAY_ON_WHILE_PLUGGED_IN failed", t)
        }
        try {
            dpm.setPermissionGrantState(
                adminComponent,
                context.packageName,
                Manifest.permission.RECORD_AUDIO,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        } catch (t: Throwable) {
            Log.w(TAG, "setPermissionGrantState RECORD_AUDIO failed", t)
        }
    }

    fun startLockTask(activity: Activity) {
        if (!isDeviceOwner()) return
        applyOwnerPolicies()
        try {
            activity.startLockTask()
        } catch (t: Throwable) {
            Log.w(TAG, "startLockTask failed", t)
        }
    }

    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (t: Throwable) {
            Log.w(TAG, "stopLockTask failed", t)
        }
        if (!isDeviceOwner()) return
        try {
            dpm.setStatusBarDisabled(adminComponent, false)
        } catch (t: Throwable) {
            Log.w(TAG, "re-enable status bar failed", t)
        }
        try {
            dpm.setKeyguardDisabled(adminComponent, false)
        } catch (t: Throwable) {
            Log.w(TAG, "re-enable keyguard failed", t)
        }
    }

    fun clearDeviceOwner() {
        if (!isDeviceOwner()) return
        try {
            dpm.clearPackagePersistentPreferredActivities(adminComponent, context.packageName)
        } catch (t: Throwable) {
            Log.w(TAG, "clearPackagePersistentPreferredActivities failed", t)
        }
        try {
            dpm.setLockTaskPackages(adminComponent, emptyArray())
        } catch (t: Throwable) {
            Log.w(TAG, "clear lock task packages failed", t)
        }
        try {
            dpm.setStatusBarDisabled(adminComponent, false)
        } catch (t: Throwable) {
            Log.w(TAG, "enable status bar before clear failed", t)
        }
        try {
            dpm.setKeyguardDisabled(adminComponent, false)
        } catch (t: Throwable) {
            Log.w(TAG, "enable keyguard before clear failed", t)
        }
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(context.packageName)
        Log.i(TAG, "device owner cleared")
    }

    companion object {
        private const val TAG = "AerocellKiosk"
    }
}
