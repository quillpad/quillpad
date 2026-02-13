package org.qosp.notes.data.sync.nextcloud

import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.qosp.notes.preferences.PreferenceRepository

data class NextcloudConfig(
    val remoteAddress: String,
    val username: String,
    private val password: String,
) {

    val credentials = ("Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)).trim()

    val authenticationHeaders: Map<String, String>
        get() = mapOf("Authorization" to credentials)

    companion object {
        fun fromPreferences(preferenceRepository: PreferenceRepository): Flow<NextcloudConfig?> {
            val url = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_INSTANCE_URL)
            val username = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_USERNAME)
            val password = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_PASSWORD)

            return url.flatMapLatest { u ->
                username.flatMapLatest { username ->
                    password.map { password ->
                        NextcloudConfig(u, username, password)
                            .takeUnless { u.isBlank() or username.isBlank() or password.isBlank() }
                    }
                }
            }
        }
    }
}
