package org.qosp.notes.data.sync.nextcloud

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudNote(
    val id: Long,
    val etag: String? = null,
    val content: String?,
    val title: String,
    val category: String,
    val favorite: Boolean,
    val modified: Long, // seconds
    val readOnly: Boolean? = null,
    val remoteId: String = id.toString(),
)
