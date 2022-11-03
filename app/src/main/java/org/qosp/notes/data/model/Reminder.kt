package org.qosp.notes.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.time.Instant

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            onDelete = ForeignKey.CASCADE,
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"]
        ),
    ]
)
@Serializable
@Parcelize
data class Reminder(
    val name: String,
    @ColumnInfo(index = true)
    val noteId: Long,
    val date: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
) : Parcelable {

    fun hasExpired(): Boolean {
        val dateInstant = Instant.ofEpochSecond(date)
        return dateInstant.isBefore(Instant.now())
    }
}
