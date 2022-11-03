package org.qosp.notes.data.sync.nextcloud

import android.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.qosp.notes.data.sync.core.ProviderConfig
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository

data class NextcloudConfig(
    override val remoteAddress: String,
    override val username: String,
    private val password: String,
) : ProviderConfig {

    val credentials = ("Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)).trim()

    override val provider: CloudService = CloudService.NEXTCLOUD
    override val authenticationHeaders: Map<String, String>
        get() = mapOf("Authorization" to credentials)

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun fromPreferences(preferenceRepository: PreferenceRepository): Flow<NextcloudConfig?> {
            val url = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_INSTANCE_URL)
            val username = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_USERNAME)
            val password = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_PASSWORD)

            return url.flatMapLatest { url ->
                username.flatMapLatest { username ->
                    password.map { password ->
                        NextcloudConfig(url, username, password)
                            .takeUnless { url.isBlank() or username.isBlank() or password.isBlank() }
                    }
                }
            }
        }
    }
}
