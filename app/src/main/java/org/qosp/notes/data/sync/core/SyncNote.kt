package org.qosp.notes.data.sync.core

data class SyncNote(
    val id: Long,
    val idStr: String,
    val content: String?,
    val title: String,
    val lastModified: Long, // Epoch seconds
    val extra: String? = null,
    val category: String = "",
    val favorite: Boolean = false,
    val readOnly: Boolean = false,
)
