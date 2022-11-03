package org.qosp.notes.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Attachment(
    val type: Type = Type.IMAGE,
    val path: String = "",
    val description: String = "",
    val fileName: String = "",
) : Parcelable {
    enum class Type { AUDIO, IMAGE, VIDEO, GENERIC }

    fun isEmpty() = path.isEmpty() && description.isEmpty() && fileName.isEmpty()
}
