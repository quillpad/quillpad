package org.qosp.notes.data.sync.fs

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.qosp.notes.preferences.PreferenceRepository

data class StorageConfig(val location: Uri) {

    companion object {
        fun storageLocation(prefRepo: PreferenceRepository): Flow<StorageConfig?> =
            prefRepo.getEncryptedString(PreferenceRepository.STORAGE_LOCATION).map {
                runCatching { it.toUri() }.getOrNull()?.let { l -> StorageConfig(l) }
            }
    }
}
