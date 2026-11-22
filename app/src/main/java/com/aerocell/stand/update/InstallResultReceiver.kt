package com.aerocell.stand.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        UpdateLog.add("ответ установщика: ${statusName(status)}${message?.let { " — $it" } ?: ""} (сессия $session)")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i(TAG, "install needs user confirmation")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                        UpdateLog.add("система просит подтвердить установку: открыл окно ${confirm.component?.packageName ?: confirm.`package` ?: "?"}")
                    } catch (t: Throwable) {
                        UpdateLog.error("окно подтверждения не открылось", t)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "install succeeded")
            }
            else -> {
                Log.e(TAG, "install failed status=$status message=$message")
                val text = message ?: "Не удалось установить обновление"
                val app = context.applicationContext as? StandAppHolder
                app?.contentRepository?.reportInstallFailed(text)
            }
        }
    }

    companion object {
        private const val TAG = "AerocellKiosk"

        fun statusName(status: Int): String = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "успех"
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "нужно подтверждение"
            PackageInstaller.STATUS_FAILURE -> "ошибка"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "отменено"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "заблокировано"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "конфликт"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "несовместимо"
            PackageInstaller.STATUS_FAILURE_INVALID -> "битый APK"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "нет места"
            else -> "код $status"
        }
    }
}
