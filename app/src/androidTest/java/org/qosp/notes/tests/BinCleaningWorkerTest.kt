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
import kotlin.time.Duration.Companion.days

@HiltAndroidTest
class BinCleaningWorkerTest {
    private lateinit var worker: BinCleaningWorker

    @Inject
    @ApplicationContext
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
