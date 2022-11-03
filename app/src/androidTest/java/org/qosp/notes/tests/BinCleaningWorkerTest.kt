package org.qosp.notes.tests

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.PreferenceRepository
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class BinCleaningWorkerTest {
    private lateinit var worker: BinCleaningWorker
    @Inject @ApplicationContext
    lateinit var context: Context
    @Inject
    lateinit var preferenceRepository: PreferenceRepository
    @Inject
    lateinit var noteRepository: NoteRepository
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun prepare() {
        hiltRule.inject()
        worker = TestListenableWorkerBuilder<BinCleaningWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
    }

    @Test
    @Throws(Exception::class)
    fun workerShouldDeleteNotesAfterCertainInterval() = runBlocking {
        val pref = NoteDeletionTime.WEEK
        val interval = pref.interval

        preferenceRepository.set(pref)

        // Create and persist the notes
        val notes = listOf(
            Note(isDeleted = true, deletionDate = Instant.now().epochSecond),
            Note(isDeleted = true, deletionDate = Instant.now().epochSecond - interval / 2),
            Note(isDeleted = true, deletionDate = Instant.now().epochSecond - (interval + 1)), // Should get deleted permanently
        )
            .map { note ->
                val id = noteRepository.insertNote(note)
                note.copy(id = id)
            }

        val allNotes = noteRepository.getAll().first()

        worker.doWork()

        val actual = noteRepository.getAll().first()
        val expected = allNotes.filterNot { it.id == notes[2].id }

        assertTrue("Notes were not deleted properly", actual == expected)
    }
}
