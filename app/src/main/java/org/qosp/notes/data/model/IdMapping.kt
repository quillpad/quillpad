package org.qosp.notes.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import org.qosp.notes.preferences.CloudService

@Serializable
@Entity(tableName = "cloud_ids")
data class IdMapping(
    @PrimaryKey(autoGenerate = true)
    val mappingId: Long = 0L,
    val localNoteId: Long,
    val remoteNoteId: Long?,
    val provider: CloudService?,
    val extras: String?,
    val isDeletedLocally: Boolean,
    val isBeingUpdated: Boolean = false,
)
