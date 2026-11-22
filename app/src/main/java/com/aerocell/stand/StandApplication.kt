package com.aerocell.stand

import android.app.Application
import com.aerocell.stand.update.ContentRepository
import com.aerocell.stand.update.StandAppHolder
import com.aerocell.stand.update.UpdateLog

class StandApplication : Application(), StandAppHolder {
    override val contentRepository: ContentRepository by lazy { ContentRepository(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateLog.init(this)
        UpdateLog.add("запуск ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
