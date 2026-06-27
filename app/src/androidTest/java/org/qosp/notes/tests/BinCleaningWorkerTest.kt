package org.qosp.notes.tests

import android.content.Context
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.PreferenceRepository
import java.time.Instant
import kotlin.time.Duration.Companion.days

class BinCleaningWorkerTest : KoinComponent {
    private lateinit var worker: BinCleaningWorker

    val context: Context by inject()

    val preferenceRepository: PreferenceRepository by inject()

    val noteRepository: NoteRepository by inject()

    @Before
    fun setup() {
        worker = TestListenableWorkerBuilder<BinCleaningWorker>(context)
            .setWorkerFactory(KoinWorkerFactory())
            .build()
    }

    @Test
    @Throws(Exception::class)
    fun test1WeekDelete() = runBlocking {
        val pref = NoteDeletionTime.WEEK
        preferenceRepository.set(pref)
        setupNotes()
        val allNotes = noteRepository.getAll().first()
        worker.doWork()

        val actual = noteRepository.getAll().first()
        val expected = allNotes.filter { it.title.toInt() < 7 }
        assertTrue("Notes were not deleted properly", actual == expected)
    }

    @Test
    @Throws(Exception::class)
    fun test2WeeksDelete() = runBlocking {
        val pref = NoteDeletionTime.TWO_WEEKS
        preferenceRepository.set(pref)
        setupNotes()
        val allNotes = noteRepository.getAll().first()
        worker.doWork()

        val actual = noteRepository.getAll().first()
        val expected = allNotes.filter { it.title.toInt() < 14 }
        assertTrue("Notes were not deleted properly", actual == expected)
    }

    @Test
    @Throws(Exception::class)
    fun test1MonthDelete() = runBlocking {
        val pref = NoteDeletionTime.MONTH
        preferenceRepository.set(pref)
        setupNotes()
        val allNotes = noteRepository.getAll().first()
        worker.doWork()

        val actual = noteRepository.getAll().first()
        val expected = allNotes.filter { it.title.toInt() < 30 }
        assertTrue("Notes were not deleted properly", actual == expected)
    }

    @Test
    @Throws(Exception::class)
    fun testNeverDelete() = runBlocking {
        val pref = NoteDeletionTime.NEVER
        preferenceRepository.set(pref)
        setupNotes()
        val allNotes = noteRepository.getAll().first()
        worker.doWork()

        val actual = noteRepository.getAll().first()
        assertTrue("Notes were not deleted properly", actual == allNotes)
    }

    private suspend fun setupNotes(): List<Note> {
        // Create and persist the notes
        val now = Instant.now().epochSecond
        val notes = listOf(
            Note(isDeleted = true, title = "0", deletionDate = now),
            Note(isDeleted = true, title = "3", deletionDate = now - 3.days.inWholeSeconds),
            Note(isDeleted = true, title = "8", deletionDate = now - 8.days.inWholeSeconds),
            Note(isDeleted = true, title = "18", deletionDate = now - 18.days.inWholeSeconds),
            Note(isDeleted = true, title = "31", deletionDate = now - 31.days.inWholeSeconds),
            Note(isDeleted = true, title = "58", deletionDate = now - 58.days.inWholeSeconds),
        )
            .map { note ->
                val id = noteRepository.insertNote(note)
                note.copy(id = id)
            }
        return notes
    }
}
