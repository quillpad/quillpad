package org.qosp.notes.components.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.preferences.BackgroundSync
import org.qosp.notes.preferences.PreferenceRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val preferenceRepository: PreferenceRepository,
    private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        if (preferenceRepository.get<BackgroundSync>().first() == BackgroundSync.DISABLED)
            return@withContext Result.failure()

        when (syncManager.sync()) {
            Success -> Result.success()
            else -> Result.failure()
        }
    }
}
