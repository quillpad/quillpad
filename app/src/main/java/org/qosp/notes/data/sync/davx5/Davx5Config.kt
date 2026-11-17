package org.qosp.notes.data.sync.davx5

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.qosp.notes.preferences.PreferenceRepository

data class Davx5Config(
    val accountName: String = "",
    val collectionId: Long = 0L
) {
    companion object {
        fun fromPreferences(preferenceRepository: PreferenceRepository): Flow<Davx5Config> {
            return combine(
                preferenceRepository.getEncryptedString(PreferenceRepository.DAVX5_ACCOUNT_NAME),
                preferenceRepository.getEncryptedString(PreferenceRepository.DAVX5_COLLECTION_ID)
            ) { account, collectionIdStr ->
                Davx5Config(
                    accountName = account,
                    collectionId = collectionIdStr.toLongOrNull() ?: 0L
                )
            }
        }
    }
}
