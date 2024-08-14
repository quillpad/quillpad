package org.qosp.notes.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            onDelete = CASCADE,
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"]
        ),
        ForeignKey(
            onDelete = CASCADE,
            onUpdate = CASCADE,
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"]
        )
    ],
)
@Parcelize
@Serializable
data class NoteTagJoin(
    @ColumnInfo(index = true)
    val tagId: Long = 0L,
    @ColumnInfo(index = true)
    val noteId: Long = 0L
) : Parcelable
