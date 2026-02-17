package org.qosp.notes.data.sync.onedrive

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.qosp.notes.preferences.PreferenceRepository

data class OneDriveConfig(
    val accessToken: String,
    val refreshToken: String,
    val userEmail: String,
) {
    val authenticationHeaders: Map<String, String>
        get() = mapOf("Authorization" to "Bearer $accessToken")

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun fromPreferences(preferenceRepository: PreferenceRepository): Flow<OneDriveConfig?> {
            val accessToken = preferenceRepository.getEncryptedString(PreferenceRepository.ONEDRIVE_ACCESS_TOKEN)
            val refreshToken = preferenceRepository.getEncryptedString(PreferenceRepository.ONEDRIVE_REFRESH_TOKEN)
            val userEmail = preferenceRepository.getEncryptedString(PreferenceRepository.ONEDRIVE_USER_EMAIL)

            return accessToken.flatMapLatest { token ->
                refreshToken.flatMapLatest { refresh ->
                    userEmail.map { email ->
                        OneDriveConfig(token, refresh, email)
                            .takeUnless { token.isBlank() || refresh.isBlank() || email.isBlank() }
                    }
                }
            }
        }
    }
}
