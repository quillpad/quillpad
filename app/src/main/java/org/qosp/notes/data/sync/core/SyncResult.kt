package org.qosp.notes.data.sync.core

sealed class SyncResult<T> {
    data class Success<T>(val data: T) : SyncResult<T>()
    data class Error<T>(val error: Throwable) : SyncResult<T>()
}
