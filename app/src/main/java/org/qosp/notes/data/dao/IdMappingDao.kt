package org.qosp.notes.data.dao

import androidx.room.Dao
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

    @Query("UPDATE cloud_ids SET extras = :extras WHERE localNoteId = :localId AND provider = :cloudService")
    suspend fun updateNoteExtras(localId: Long, cloudService: CloudService, extras: String?)

    @Query("DELETE FROM cloud_ids WHERE localNoteId IN (:ids)")
    suspend fun deleteByLocalId(vararg ids: Long)

    @Query("UPDATE cloud_ids SET isDeletedLocally = 1 WHERE localNoteId IN (:ids)")
    suspend fun setNotesToBeDeleted(vararg ids: Long)

    @Query("DELETE from cloud_ids WHERE provider = :cloudService")
    suspend fun deleteAllMappingsFor(cloudService: CloudService)

    @Query("SELECT * FROM cloud_ids WHERE localNoteId = :localId AND provider = :provider LIMIT 1")
    suspend fun getByLocalIdAndProvider(localId: Long, provider: CloudService): IdMapping?

    @Query("SELECT * FROM cloud_ids WHERE localNoteId = :localId AND provider IS NULL LIMIT 1")
    suspend fun getNonRemoteByLocalId(localId: Long): IdMapping?

    @Query("SELECT * FROM cloud_ids WHERE localNoteId = :localId AND provider IS NOT NULL AND remoteNoteId IS NOT NULL")
    suspend fun getAllByLocalId(localId: Long): List<IdMapping>

    @Query("SELECT * FROM cloud_ids WHERE provider = :provider")
    suspend fun getAllByCloudService(provider: CloudService): List<IdMapping>

    @Query("SELECT count(mappingId) FROM cloud_ids WHERE provider = :provider")
    suspend fun getCountByCloudService(provider: CloudService): Int
}
