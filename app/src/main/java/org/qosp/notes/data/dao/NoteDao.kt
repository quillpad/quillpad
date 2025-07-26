package org.qosp.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteEntity
import org.qosp.notes.data.model.NoteTagJoin
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.data.model.Tag
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.preferences.SortMethod.CREATION_ASC
import org.qosp.notes.preferences.SortMethod.CREATION_DESC
import org.qosp.notes.preferences.SortMethod.MODIFIED_ASC
import org.qosp.notes.preferences.SortMethod.MODIFIED_DESC
import org.qosp.notes.preferences.SortMethod.TITLE_ASC
import org.qosp.notes.preferences.SortMethod.TITLE_DESC
import java.time.Instant

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(vararg notes: NoteEntity)

    @Transaction
    @Query("UPDATE notes SET modifiedDate = :modified WHERE id = :id")
    suspend fun updateLastModified(id: Long, modified: Long = Instant.now().epochSecond)

    @Delete
    suspend fun delete(vararg notes: NoteEntity)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun permanentlyDeleteNotesInBin()

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun getById(noteId: Long): Flow<Note?>

    @Query(
        """
        UPDATE notes SET isDeleted = 1 WHERE id IN (
            SELECT localNoteId FROM cloud_ids
            WHERE remoteNoteId IS NOT NULL AND isDeletedLocally = 0 AND remoteNoteId NOT IN (:idsInUse)
            AND provider = :provider
        )"""
    )
    suspend fun moveRemotelyDeletedNotesToBin(idsInUse: List<Long>, provider: CloudService)

    @Transaction
    @RawQuery(
        observedEntities = [
            NoteEntity::class,
            Tag::class,
            Reminder::class,
            NoteTagJoin::class,
        ]
    )
    fun rawGetQuery(query: SimpleSQLiteQuery): Flow<List<Note>>

    fun getNonRemoteNotes(sortMethod: SortMethod, provider: CloudService): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes
                WHERE isDeleted = 0 AND isLocalOnly = 0
                AND id NOT IN (
                    SELECT localNoteId FROM cloud_ids
                    WHERE provider = '${provider.name}'
                )
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }

    fun getDeleted(sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE isDeleted = 1
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }

    fun getArchived(sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }

    fun getNonDeleted(sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE isDeleted = 0
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }

    fun getNonDeletedOrArchived(sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE isArchived = 0 AND isDeleted = 0
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }

    fun getAll(sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }
    fun getAllBlankTitleNotes(): Flow<List<Note>> {
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE title IS NULL OR trim(title) = ''
                """
            )
        )
    }

    fun getByNotebook(notebookId: Long, sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE isArchived = 0 AND isDeleted = 0 AND notebookId = $notebookId
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }

    private fun getOrderByMethod(sortMethod: SortMethod): Pair<String, String> {
        val column = when (sortMethod) {
            TITLE_ASC, TITLE_DESC -> "title"
            CREATION_ASC, CREATION_DESC -> "creationDate"
            MODIFIED_ASC, MODIFIED_DESC -> "modifiedDate"
        }
        val order = when (sortMethod) {
            TITLE_ASC, CREATION_ASC, MODIFIED_ASC -> "ASC"
            TITLE_DESC, CREATION_DESC, MODIFIED_DESC -> "DESC"
        }
        return Pair(column, order)
    }

    fun getNotesWithoutNotebook(sortMethod: SortMethod): Flow<List<Note>> {
        val (column, order) = getOrderByMethod(sortMethod)
        return rawGetQuery(
            SimpleSQLiteQuery(
                """
                SELECT * FROM notes WHERE isArchived = 0 AND isDeleted = 0 AND notebookId IS NULL
                ORDER BY isPinned DESC, $column $order
            """
            )
        )
    }
}
