package org.qosp.notes.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class NoteTask(val id: Long, val content: String, val isDone: Boolean) : Parcelable
