package org.qosp.notes.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class NoteTask(val id: Long, val content: String, var isDone: Boolean,
                    var indentationLevel: Int = 0,
                    var parentId: Long? = null // null means it's a top-level task
 ) : Parcelable
