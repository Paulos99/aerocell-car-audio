package com.aerocell.stand

import android.app.Application
import com.aerocell.stand.update.ContentRepository
import com.aerocell.stand.update.StandAppHolder

class StandApplication : Application(), StandAppHolder {
    override val contentRepository: ContentRepository by lazy { ContentRepository(this) }
}
