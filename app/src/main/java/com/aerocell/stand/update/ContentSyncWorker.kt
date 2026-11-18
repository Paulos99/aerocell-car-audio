package com.aerocell.stand.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ContentSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? StandAppHolder ?: return Result.retry()
        val repository = app.contentRepository
        val manifest = repository.loadCachedManifest() ?: return Result.success()
        return try {
            repository.syncMedia(manifest)
            Result.success()
        } catch (t: Throwable) {
            repository.reportError(t.message ?: "Ошибка загрузки контента")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

interface StandAppHolder {
    val contentRepository: ContentRepository
}
