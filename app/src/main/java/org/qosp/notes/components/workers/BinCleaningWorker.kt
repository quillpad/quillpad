package org.qosp.notes.components.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.PreferenceRepository
import java.time.Instant

@HiltWorker
class BinCleaningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferenceRepository: PreferenceRepository,
    private val noteRepository: NoteRepository,
    private val mediaStorageManager: MediaStorageManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deletionTime = preferenceRepository.get<NoteDeletionTime>().first().interval
        val now = Instant.now()
        val toBeDeleted = noteRepository.getDeleted().first()
            .filter { note ->
                val deletionDate = note.deletionDate?.let { Instant.ofEpochSecond(it) } ?: return@filter false
                now.isAfter(deletionDate.plusSeconds(deletionTime))
            }
            .toTypedArray()

        noteRepository.deleteNotes(*toBeDeleted)
        mediaStorageManager.cleanUpStorage()

        Result.success()
    }
}
