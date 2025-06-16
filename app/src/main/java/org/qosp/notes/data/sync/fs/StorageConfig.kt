package org.qosp.notes.data.sync.fs

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
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

fun Uri.toFriendlyString(context: Context): String {
    // Get the provider name (app name)
    val packageManager = context.packageManager
    val providerName = packageManager.getInstalledPackages(PackageManager.GET_PROVIDERS)
        ?.firstOrNull { it?.providers?.any { p -> p?.authority == this.authority } ?: false }
        ?.applicationInfo
        ?.let { packageManager.getApplicationLabel(it) }?.toString() ?: this.authority ?: "Unknown"

    // Try to get the directory name using DocumentFile
    val documentFile = DocumentFile.fromTreeUri(context, this)
    val directoryName = documentFile?.name ?: this.lastPathSegment?.substringAfterLast('/') ?: ""

    // Combine provider name and directory name
    return if (directoryName.isNotEmpty()) {
        "$providerName: $directoryName"
    } else {
        providerName
    }
}
