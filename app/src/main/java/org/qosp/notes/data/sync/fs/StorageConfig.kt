package org.qosp.notes.data.sync.fs

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.qosp.notes.data.sync.core.ProviderConfig
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository

data class StorageConfig(
    val location: Uri,
    val context: Context,
    override val provider: CloudService = CloudService.FILE_STORAGE
) : ProviderConfig {

    companion object {
        fun storageLocation(prefRepo: PreferenceRepository, context: Context): Flow<StorageConfig?> {
            return prefRepo.getEncryptedString(PreferenceRepository.STORAGE_LOCATION).map {
                val location = try {
                    Uri.parse(it)
                } catch (e: Exception) {
                    null
                }
                location?.let { l -> StorageConfig(l, context) }
            }
        }
    }
}
