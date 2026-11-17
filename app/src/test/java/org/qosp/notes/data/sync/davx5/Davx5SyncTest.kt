package org.qosp.notes.data.sync.davx5

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Davx5SyncTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Robolectric.setupContentProvider(Davx5ContentProvider::class.java, Davx5Contract.AUTHORITY)
    }

    @Test
    fun `database helper initializes default collection with VJOURNAL support`() {
        val cursor = context.contentResolver.query(
            Davx5Contract.CONTENT_URI_COLLECTION,
            null,
            "${Davx5Contract.Collection.SUPPORTS_VJOURNAL} = 1",
            null,
            null
        )
        assertNotNull(cursor)
        cursor?.use {
            assertTrue(it.moveToFirst())
            val nameCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.DISPLAY_NAME)
            val supportsVJournalCol = it.getColumnIndexOrThrow(Davx5Contract.Collection.SUPPORTS_VJOURNAL)
            assertEquals("Local", it.getString(nameCol))
            assertEquals(1, it.getInt(supportsVJournalCol))
        }
    }

    @Test
    fun `content provider supports insert, query, update, delete for icalobject`() {
        // Insert
        val values = ContentValues().apply {
            put(Davx5Contract.ICalObject.COLLECTION_ID, 0L)
            put(Davx5Contract.ICalObject.UID, "test-uid-123")
            put(Davx5Contract.ICalObject.SUMMARY, "My Test Journal")
            put(Davx5Contract.ICalObject.DESCRIPTION, "Journal note content")
            put(Davx5Contract.ICalObject.CREATED, 1000L)
            put(Davx5Contract.ICalObject.LAST_MODIFIED, 1000L)
            put(Davx5Contract.ICalObject.DIRTY, 1)
            put(Davx5Contract.ICalObject.DELETED, 0)
            put(Davx5Contract.ICalObject.COMPONENT, "VJOURNAL")
        }

        val uri = context.contentResolver.insert(Davx5Contract.CONTENT_URI_ICALOBJECT, values)
        assertNotNull(uri)

        // Query
        val queryCursor = context.contentResolver.query(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            null,
            "${Davx5Contract.ICalObject.UID} = ?",
            arrayOf("test-uid-123"),
            null
        )
        assertNotNull(queryCursor)
        queryCursor?.use {
            assertTrue(it.moveToFirst())
            val summary = it.getString(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.SUMMARY))
            val desc = it.getString(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.DESCRIPTION))
            val dirty = it.getInt(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.DIRTY))
            assertEquals("My Test Journal", summary)
            assertEquals("Journal note content", desc)
            assertEquals(1, dirty)
        }

        // Update
        val updateValues = ContentValues().apply {
            put(Davx5Contract.ICalObject.SUMMARY, "Updated Journal Title")
            put(Davx5Contract.ICalObject.DIRTY, 0)
        }
        val updatedRows = context.contentResolver.update(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            updateValues,
            "${Davx5Contract.ICalObject.UID} = ?",
            arrayOf("test-uid-123")
        )
        assertEquals(1, updatedRows)

        // Verify update
        val updatedCursor = context.contentResolver.query(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            null,
            "${Davx5Contract.ICalObject.UID} = ?",
            arrayOf("test-uid-123"),
            null
        )
        updatedCursor?.use {
            assertTrue(it.moveToFirst())
            val summary = it.getString(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.SUMMARY))
            val dirty = it.getInt(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.DIRTY))
            assertEquals("Updated Journal Title", summary)
            assertEquals(0, dirty)
        }

        // Delete
        val deletedRows = context.contentResolver.delete(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            "${Davx5Contract.ICalObject.UID} = ?",
            arrayOf("test-uid-123")
        )
        assertEquals(1, deletedRows)
    }

    @Test
    fun `davx5 backend CRUD operations work seamlessly with ISyncBackend interface`() = runTest {
        val config = Davx5Config(accountName = "test@example.com", collectionId = 0L)
        val backend = Davx5Backend(context, config)

        assertEquals(CloudService.DAVX5, backend.type)

        // Create Note
        val localNote = Note(
            id = 10L,
            title = "Backend Journal Note",
            content = "Backend Markdown Content",
            creationDate = 500L,
            modifiedDate = 600L
        )
        val syncNote = backend.createNote(localNote)
        assertEquals("Backend Journal Note", syncNote.title)
        assertEquals("Backend Markdown Content", syncNote.content)
        assertTrue(syncNote.idStr.isNotEmpty())

        val mapping = IdMapping(
            localNoteId = 10L,
            remoteNoteId = syncNote.id,
            provider = CloudService.DAVX5,
            extras = null,
            isDeletedLocally = false,
            storageUri = syncNote.idStr
        )

        // Get Note
        val fetched = backend.getNote(mapping)
        assertNotNull(fetched)
        assertEquals("Backend Journal Note", fetched?.title)
        assertEquals("Backend Markdown Content", fetched?.content)

        // Get All
        val allNotes = backend.getAll()
        assertNotNull(allNotes)
        assertTrue(allNotes!!.any { it.idStr == syncNote.idStr })

        // Update Note
        val updatedLocalNote = localNote.copy(
            title = "Updated Backend Title",
            content = "Updated Content",
            modifiedDate = 700L
        )
        backend.updateNote(updatedLocalNote, mapping)

        val updatedFetched = backend.getNote(mapping)
        assertNotNull(updatedFetched)
        assertEquals("Updated Backend Title", updatedFetched?.title)
        assertEquals("Updated Content", updatedFetched?.content)

        // Delete Note
        val deleted = backend.deleteNote(mapping)
        assertTrue(deleted)

        // Once marked deleted, getNote should return null
        val afterDelete = backend.getNote(mapping)
        assertEquals(null, afterDelete)
    }

    @Test
    fun `davx5 helper returns package name and handles missing accounts safely`() {
        val pkg = Davx5Helper.getDavx5Package(context)
        assertEquals(Davx5Contract.DAVX5_PACKAGE, pkg)

        val accounts = Davx5Helper.getDavx5Accounts(context)
        assertTrue(accounts.isEmpty())
    }

    @Test
    fun `davx5 helper detects accounts added to AccountManager`() {
        val am = android.accounts.AccountManager.get(context)
        val testAccount = android.accounts.Account("user@nextcloud.example.com", Davx5Contract.DAVX5_ACCOUNT_TYPE)
        am.addAccountExplicitly(testAccount, "password", null)

        val accounts = Davx5Helper.getDavx5Accounts(context)
        assertEquals(1, accounts.size)
        assertEquals("user@nextcloud.example.com", accounts[0].name)
        assertEquals(Davx5Contract.DAVX5_ACCOUNT_TYPE, accounts[0].type)
    }

    @Test
    fun `davx5 helper creates choose account intent with proper allowable account types`() {
        val intent = Davx5Helper.createChooseAccountIntent("test@example.com")
        assertNotNull(intent)
        val types = intent.getStringArrayExtra("allowableAccountTypes")
        assertNotNull(types)
        assertTrue(types!!.contains(Davx5Contract.DAVX5_ACCOUNT_TYPE))
        assertTrue(types.contains(Davx5Contract.DAVX5_ACCOUNT_TYPE_ALT))
    }

    @Test
    fun `davx5 helper getDavx5Calendars returns collections`() {
        val calendars = Davx5Helper.getDavx5Calendars(context)
        assertNotNull(calendars)
        assertTrue(calendars.isNotEmpty())
        assertEquals("Local", calendars[0].displayName)
    }

    @Test
    fun `davx5 helper discovers VJOURNAL collections for account without calendar permissions`() {
        val values = ContentValues().apply {
            put(Davx5Contract.Collection._ID, 42L)
            put(Davx5Contract.Collection.DISPLAY_NAME, "Work Notes")
            put(Davx5Contract.Collection.ACCOUNT_NAME, "notes@example.com")
            put(Davx5Contract.Collection.ACCOUNT_TYPE, Davx5Contract.DAVX5_ACCOUNT_TYPE)
            put(Davx5Contract.Collection.SUPPORTS_VJOURNAL, 1)
        }
        context.contentResolver.insert(Davx5Contract.CONTENT_URI_COLLECTION, values)

        val accountCollections = Davx5Helper.getDavx5Calendars(context, "notes@example.com")
        assertEquals(1, accountCollections.size)
        assertEquals(42L, accountCollections[0].id)
        assertEquals("Work Notes", accountCollections[0].displayName)

        val discoveredAccounts = Davx5Helper.getDavx5Accounts(context)
        assertTrue(discoveredAccounts.any { it.name == "notes@example.com" })
    }

    @Test
    fun `content provider rejects negative or non-numeric item IDs safely`() {
        val invalidUri = Uri.withAppendedPath(Davx5Contract.CONTENT_URI_ICALOBJECT, "-1")
        val cursor = context.contentResolver.query(invalidUri, null, null, null, null)
        assertNull(cursor)

        val updated = context.contentResolver.update(invalidUri, ContentValues(), null, null)
        assertEquals(0, updated)

        val deleted = context.contentResolver.delete(invalidUri, null, null)
        assertEquals(0, deleted)
    }

    @Test
    fun `davx5 backend scopes getAll to active collectionId and local fallback`() = runTest {
        val col1Values = ContentValues().apply {
            put(Davx5Contract.ICalObject.COLLECTION_ID, 10L)
            put(Davx5Contract.ICalObject.UID, "uid-col-10")
            put(Davx5Contract.ICalObject.SUMMARY, "Collection 10 Note")
            put(Davx5Contract.ICalObject.DESCRIPTION, "Body 10")
            put(Davx5Contract.ICalObject.CREATED, 1000L)
            put(Davx5Contract.ICalObject.LAST_MODIFIED, 1000L)
            put(Davx5Contract.ICalObject.DELETED, 0)
        }
        val col2Values = ContentValues().apply {
            put(Davx5Contract.ICalObject.COLLECTION_ID, 20L)
            put(Davx5Contract.ICalObject.UID, "uid-col-20")
            put(Davx5Contract.ICalObject.SUMMARY, "Collection 20 Note")
            put(Davx5Contract.ICalObject.DESCRIPTION, "Body 20")
            put(Davx5Contract.ICalObject.CREATED, 1000L)
            put(Davx5Contract.ICalObject.LAST_MODIFIED, 1000L)
            put(Davx5Contract.ICalObject.DELETED, 0)
        }
        context.contentResolver.insert(Davx5Contract.CONTENT_URI_ICALOBJECT, col1Values)
        context.contentResolver.insert(Davx5Contract.CONTENT_URI_ICALOBJECT, col2Values)

        val backendCol10 = Davx5Backend(context, Davx5Config(accountName = "test@example.com", collectionId = 10L))
        val notesCol10 = backendCol10.getAll()
        assertNotNull(notesCol10)
        assertEquals(1, notesCol10!!.size)
        assertEquals("uid-col-10", notesCol10[0].idStr)
    }
}
