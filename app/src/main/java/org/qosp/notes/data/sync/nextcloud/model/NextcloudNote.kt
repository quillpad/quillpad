package org.qosp.notes.data.sync.nextcloud.model

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudNote(
    val id: Long,
    val etag: String? = null,
    val content: String,
    val title: String,
    val category: String,
    val favorite: Boolean,
    val modified: Long,
    val readOnly: Boolean? = null,
)
