package org.qosp.notes.data.sync.googledrive

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.qosp.notes.preferences.PreferenceRepository

data class GoogleDriveConfig(
    val accessToken: String,
    val refreshToken: String,
    val userEmail: String,
) {
    val authenticationHeaders: Map<String, String>
        get() = mapOf("Authorization" to "Bearer $accessToken")

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun fromPreferences(preferenceRepository: PreferenceRepository): Flow<GoogleDriveConfig?> {
            val accessToken = preferenceRepository.getEncryptedString(PreferenceRepository.GOOGLE_DRIVE_ACCESS_TOKEN)
            val refreshToken = preferenceRepository.getEncryptedString(PreferenceRepository.GOOGLE_DRIVE_REFRESH_TOKEN)
            val userEmail = preferenceRepository.getEncryptedString(PreferenceRepository.GOOGLE_DRIVE_USER_EMAIL)

            return accessToken.flatMapLatest { token ->
                refreshToken.flatMapLatest { refresh ->
                    userEmail.map { email ->
                        GoogleDriveConfig(token, refresh, email)
                            .takeUnless { token.isBlank() or refresh.isBlank() or email.isBlank() }
                    }
                }
            }
        }
    }
}
