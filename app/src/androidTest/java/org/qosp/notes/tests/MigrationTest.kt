package org.qosp.notes.tests

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.data.AppDatabase
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MigrationTest : KoinComponent {
    private val testDb = "migration-test"

    // Array of all migrations.
    private val allMigrations = arrayOf(
        AppDatabase.Companion.MIGRATION_1_2,
        AppDatabase.Companion.MIGRATION_2_3,
        AppDatabase.Companion.Migration_3_4,
    )

    // Inject MigrationTestHelper from Koin
    private val helper: MigrationTestHelper by inject()

    @get:Rule
    val helperRule: MigrationTestHelper
        get() = helper

    // Helper methods for inserting test data
    private fun insertNotebook(db: SupportSQLiteDatabase, name: String): Long {
        val statement = db.compileStatement(
            "INSERT INTO notebooks (notebookName) VALUES (?)"
        )
        statement.bindString(1, name)
        return statement.executeInsert()
    }

    private fun insertNote(
        db: SupportSQLiteDatabase,
        title: String,
        content: String,
        notebookId: Long? = null,
        isList: Boolean = false,
        isArchived: Boolean = false,
        isDeleted: Boolean = false,
        isPinned: Boolean = false,
        isHidden: Boolean = false,
        isMarkdownEnabled: Boolean = true,
        isLocalOnly: Boolean = false,
        creationDate: Long = Instant.now().epochSecond,
        modifiedDate: Long = Instant.now().epochSecond,
        deletionDate: Long? = null,
        color: Long = 0L // Default color
    ): Long {
        // In version 1, isCompactPreview and screenAlwaysOn columns don't exist
        val sql = if (db.version == 1) {
            """
            INSERT INTO notes (
                title, content, isList, taskList, isArchived, isDeleted, isPinned, isHidden,
                isMarkdownEnabled, isLocalOnly, creationDate, modifiedDate, deletionDate,
                attachments, color, notebookId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        } else if (db.version == 2) {
            // In version 2, isCompactPreview exists but screenAlwaysOn doesn't
            """
            INSERT INTO notes (
                title, content, isList, taskList, isArchived, isDeleted, isPinned, isHidden,
                isMarkdownEnabled, isLocalOnly, isCompactPreview, creationDate, modifiedDate,
                deletionDate, attachments, color, notebookId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        } else {
            // Version 3 has all columns
            """
            INSERT INTO notes (
                title, content, isList, taskList, isArchived, isDeleted, isPinned, isHidden,
                isMarkdownEnabled, isLocalOnly, isCompactPreview, screenAlwaysOn, creationDate,
                modifiedDate, deletionDate, attachments, color, notebookId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        }

        val statement = db.compileStatement(sql)
        var index = 1
        statement.bindString(index++, title)
        statement.bindString(index++, content)
        statement.bindLong(index++, if (isList) 1 else 0)
        statement.bindString(index++, "[]") // Empty taskList as JSON
        statement.bindLong(index++, if (isArchived) 1 else 0)
        statement.bindLong(index++, if (isDeleted) 1 else 0)
        statement.bindLong(index++, if (isPinned) 1 else 0)
        statement.bindLong(index++, if (isHidden) 1 else 0)
        statement.bindLong(index++, if (isMarkdownEnabled) 1 else 0)
        statement.bindLong(index++, if (isLocalOnly) 1 else 0)

        if (db.version >= 2) {
            statement.bindLong(index++, 0) // isCompactPreview default value
        }

        if (db.version >= 3) {
            statement.bindLong(index++, 0) // screenAlwaysOn default value
        }

        statement.bindLong(index++, creationDate)
        statement.bindLong(index++, modifiedDate)
        if (deletionDate != null) {
            statement.bindLong(index++, deletionDate)
        } else {
            statement.bindNull(index++)
        }
        statement.bindString(index++, "[]") // Empty attachments as JSON
        statement.bindLong(index++, color)
        if (notebookId != null) {
            statement.bindLong(index, notebookId)
        } else {
            statement.bindNull(index)
        }

        return statement.executeInsert()
    }

    // Helper methods for validating data
    private fun validateNoteExists(db: SupportSQLiteDatabase, noteId: Long, expectedTitle: String) {
        val cursor = db.query("SELECT title FROM notes WHERE id = ?", arrayOf(noteId.toString()))
        Assert.assertTrue("Note with ID $noteId should exist", cursor.moveToFirst())
        Assert.assertEquals("Note title should match", expectedTitle, cursor.getString(0))
        cursor.close()
    }

    private fun validateNotebookExists(db: SupportSQLiteDatabase, notebookId: Long, expectedName: String) {
        val cursor = db.query("SELECT notebookName FROM notebooks WHERE id = ?", arrayOf(notebookId.toString()))
        Assert.assertTrue("Notebook with ID $notebookId should exist", cursor.moveToFirst())
        Assert.assertEquals("Notebook name should match", expectedName, cursor.getString(0))
        cursor.close()
    }

    private fun validateNoteHasColumn(db: SupportSQLiteDatabase, noteId: Long, columnName: String) {
        val cursor = db.query("SELECT $columnName FROM notes WHERE id = ?", arrayOf(noteId.toString()))
        Assert.assertTrue("Note with ID $noteId should have column $columnName", cursor.moveToFirst())
        cursor.close()
    }

    private fun insertIdMapping(
        db: SupportSQLiteDatabase,
        localNoteId: Long,
        remoteNoteId: Long? = null,
        provider: String? = null,
        extras: String? = null,
        isDeletedLocally: Boolean = false,
        isBeingUpdated: Boolean = false
    ): Long {
        val sql = """
            INSERT INTO cloud_ids (
                localNoteId, remoteNoteId, provider, extras, isDeletedLocally, isBeingUpdated
            ) VALUES (?, ?, ?, ?, ?, ?)
        """

        val statement = db.compileStatement(sql)
        var index = 1
        statement.bindLong(index++, localNoteId)

        if (remoteNoteId != null) {
            statement.bindLong(index++, remoteNoteId)
        } else {
            statement.bindNull(index++)
        }

        if (provider != null) {
            statement.bindString(index++, provider)
        } else {
            statement.bindNull(index++)
        }

        if (extras != null) {
            statement.bindString(index++, extras)
        } else {
            statement.bindNull(index++)
        }

        statement.bindLong(index++, if (isDeletedLocally) 1 else 0)
        statement.bindLong(index++, if (isBeingUpdated) 1 else 0)

        return statement.executeInsert()
    }

    private fun validateIdMappingExists(db: SupportSQLiteDatabase, mappingId: Long, localNoteId: Long) {
        val cursor = db.query("SELECT localNoteId FROM cloud_ids WHERE mappingId = ?", arrayOf(mappingId.toString()))
        Assert.assertTrue("IdMapping with ID $mappingId should exist", cursor.moveToFirst())
        Assert.assertEquals("IdMapping localNoteId should match", localNoteId, cursor.getLong(0))
        cursor.close()
    }

    private fun validateIdMappingHasColumn(db: SupportSQLiteDatabase, mappingId: Long, columnName: String) {
        val cursor = db.query("SELECT $columnName FROM cloud_ids WHERE mappingId = ?", arrayOf(mappingId.toString()))
        Assert.assertTrue("IdMapping with ID $mappingId should have column $columnName", cursor.moveToFirst())
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateAlreadyIn3() {
        // Create the database at version 3
        helper.createDatabase(testDb, 3).apply {
            // Insert test data
            val notebookId = insertNotebook(this, "Test Notebook")
            val noteId = insertNote(this, "Test Note", "This is a test note", notebookId)

            // Validate data was inserted correctly
            validateNoteExists(this, noteId, "Test Note")
            validateNotebookExists(this, notebookId, "Test Notebook")
            validateNoteHasColumn(this, noteId, "screenAlwaysOn")

            close()
        }

        // Open latest version of the database. Room validates the schema
        // once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            testDb
        ).addMigrations(*allMigrations).build().apply {
            // Validate data is still correct after opening with Room
            openHelper.writableDatabase.use { db ->
                validateNoteExists(db, 1, "Test Note")
                validateNotebookExists(db, 1, "Test Notebook")
                validateNoteHasColumn(db, 1, "screenAlwaysOn")
            }
            close()
        }
    }


    @Test
    @Throws(IOException::class)
    fun migrate2to3() {
        // Create the database at version 2
        helper.createDatabase(testDb, 2).apply {
            // Insert test data
            val notebookId = insertNotebook(this, "Notebook v2")
            val noteId = insertNote(this, "Note v2", "Content created in v2", notebookId)

            // Validate data was inserted correctly
            validateNoteExists(this, noteId, "Note v2")
            validateNotebookExists(this, notebookId, "Notebook v2")
            validateNoteHasColumn(this, noteId, "isCompactPreview")
            close()
        }

        // Open with Room to validate schema
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            testDb
        ).addMigrations(*allMigrations).build().apply {
            // Validate data is still correct after opening with Room
            openHelper.writableDatabase.use { db ->
                validateNoteExists(db, 1, "Note v2")
                validateNotebookExists(db, 1, "Notebook v2")
                validateNoteHasColumn(db, 1, "screenAlwaysOn")
            }
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate3to4() {
        // Create the database at version 3
        helper.createDatabase(testDb, 3).apply {
            // Insert test data
            val notebookId = insertNotebook(this, "Notebook v3")
            val noteId = insertNote(this, "Note v3", "Content created in v3", notebookId)
            val mappingId = insertIdMapping(this, noteId, 12345L, "NEXTCLOUD", "{\"some\":\"data\"}", false, false)

            // Validate data was inserted correctly
            validateNoteExists(this, noteId, "Note v3")
            validateNotebookExists(this, notebookId, "Notebook v3")
            validateIdMappingExists(this, mappingId, noteId)
            close()
        }

        // Open with Room to validate schema
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            testDb
        ).addMigrations(*allMigrations).build().apply {
            // Validate data is still correct after opening with Room
            openHelper.writableDatabase.use { db ->
                validateNoteExists(db, 1, "Note v3")
                validateNotebookExists(db, 1, "Notebook v3")
                validateIdMappingExists(db, 1, 1)
                validateIdMappingHasColumn(db, 1, "storageUri")
            }
            close()
        }
    }
}
