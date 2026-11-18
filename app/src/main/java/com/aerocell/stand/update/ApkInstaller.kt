package com.aerocell.stand.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import java.io.File

class ApkInstaller(private val context: Context) {
    fun canInstall(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun permissionSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun install(file: File, silent: Boolean = false) {
        if (!file.isFile) throw IllegalArgumentException("APK не найден")
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("package", 0, file.length()).use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = "${context.packageName}.INSTALL_RESULT"
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            Log.i(TAG, "committing install session=$sessionId silent=$silent size=${file.length()}")
            session.commit(pending.intentSender)
        }
    }

    companion object {
        private const val TAG = "AerocellKiosk"
    }
}
