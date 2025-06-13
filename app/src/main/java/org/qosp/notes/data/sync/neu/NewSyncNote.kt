package org.qosp.notes.data.sync.neu

data class NewSyncNote(
    val id: Long,
    val idStr: String,
    val content: String?,
    val title: String,
    val lastModified: Long,
    val extra: String? = null,
    val category: String = "",
    val favorite: Boolean = false,
    val readOnly: Boolean = false,
)
