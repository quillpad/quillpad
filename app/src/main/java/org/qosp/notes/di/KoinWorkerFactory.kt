package org.qosp.notes.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.PreferenceRepository

class KoinWorkerFactory : WorkerFactory(), KoinComponent {
    private val preferenceRepository: PreferenceRepository by inject()
    private val noteRepository: NoteRepository by inject()
    private val mediaStorageManager: MediaStorageManager by inject()

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            BinCleaningWorker::class.java.name -> BinCleaningWorker(
                appContext,
                workerParameters,
                preferenceRepository,
                noteRepository,
                mediaStorageManager
            )

            SyncWorker::class.java.name -> SyncWorker(
                appContext,
                workerParameters,
                preferenceRepository,
                noteRepository,
            )

            else -> null
        }
    }
}
