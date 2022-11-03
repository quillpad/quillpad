package org.qosp.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.preferences.CloudService

@Dao
interface IdMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg mappings: IdMapping)

    @Update
    suspend fun update(vararg mappings: IdMapping)

    @Delete
    suspend fun delete(vararg mappings: IdMapping)

    @Query("DELETE FROM cloud_ids WHERE localNoteId IN (:ids)")
    suspend fun deleteByLocalId(vararg ids: Long)

    @Query("UPDATE cloud_ids SET isDeletedLocally = 1 WHERE localNoteId IN (:ids)")
    suspend fun setNotesToBeDeleted(vararg ids: Long)

    @Query("SELECT * FROM cloud_ids WHERE remoteNoteId = :remoteId AND provider = :provider LIMIT 1")
    suspend fun getByRemoteId(remoteId: Long, provider: CloudService): IdMapping?

    @Query("SELECT * FROM cloud_ids WHERE localNoteId = :localId AND provider = :provider LIMIT 1")
    suspend fun getByLocalIdAndProvider(localId: Long, provider: CloudService): IdMapping?

    @Query("SELECT * FROM cloud_ids WHERE localNoteId = :localId AND provider IS NULL LIMIT 1")
    suspend fun getNonRemoteByLocalId(localId: Long): IdMapping?

    @Query("UPDATE cloud_ids SET remoteNoteId = NULL, provider = NULL WHERE isDeletedLocally = 0 AND remoteNoteId NOT IN (:idsInUse) AND provider = :provider")
    suspend fun unassignProviderFromRemotelyDeletedNotes(idsInUse: List<Long>, provider: CloudService)

    @Query("DELETE FROM cloud_ids WHERE remoteNoteId IN (:remoteIds) AND provider = :provider")
    suspend fun deleteByRemoteId(provider: CloudService, vararg remoteIds: Long)

    @Query("UPDATE cloud_ids SET provider = :provider WHERE localNoteId = :localId AND provider IS NULL")
    suspend fun assignProviderToNote(localId: Long, provider: CloudService)

    @Query("UPDATE cloud_ids SET provider = NULL, remoteNoteId = NULL WHERE localNoteId = :localId AND provider = :provider")
    suspend fun unassignProviderFromNote(localId: Long, provider: CloudService)

    @Query("DELETE FROM cloud_ids WHERE localNoteId NOT IN (:ids)")
    suspend fun deleteIfLocalIdNotIn(ids: List<Long>)

    @Query("SELECT * FROM cloud_ids WHERE localNoteId = :localId AND provider IS NOT NULL AND remoteNoteId IS NOT NULL")
    suspend fun getAllByLocalId(localId: Long): List<IdMapping>

    @Query("UPDATE cloud_ids SET isBeingUpdated = :isBeingUpdated WHERE localNoteId = :id")
    suspend fun setNoteIsBeingUpdated(id: Long, isBeingUpdated: Boolean)
}
