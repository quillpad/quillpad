package org.qosp.notes.ui.sync.nextcloud

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.sync.nextcloud.BackendValidationResult
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.data.sync.nextcloud.ValidateNextcloud
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository

class NextcloudViewModel(
    private val preferenceRepository: PreferenceRepository,
    private val validateNextcloud: ValidateNextcloud,
    private val noteRepository: NoteRepository
) : ViewModel() {

    val username = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_USERNAME)
    val password = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_PASSWORD)

    fun setURL(url: String) = viewModelScope.launch {
        if (!URLUtil.isHttpsUrl(url)) return@launch

        val url = if (url.endsWith("/")) url else "$url/"
        val previousUrl = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_INSTANCE_URL).first()

        if (url != previousUrl) {
            noteRepository.deleteIdMappingsForCloudService(CloudService.NEXTCLOUD)
        }

        preferenceRepository.putEncryptedStrings(
            PreferenceRepository.NEXTCLOUD_INSTANCE_URL to url,
        )
    }

    suspend fun authenticate(username: String, password: String): BackendValidationResult {
        val config = NextcloudConfig(
            username = username,
            password = password,
            remoteAddress = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_INSTANCE_URL).first()
        )

        val response = withContext(Dispatchers.IO) { validateNextcloud(config) }
        if (response == BackendValidationResult.Success) {
            val previousUsername =
                preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_USERNAME).first()
            if (username != previousUsername) {
                noteRepository.deleteIdMappingsForCloudService(CloudService.NEXTCLOUD)
            }
            preferenceRepository.putEncryptedStrings(
                PreferenceRepository.NEXTCLOUD_USERNAME to username,
                PreferenceRepository.NEXTCLOUD_PASSWORD to password,
            )
        }
        return response
    }
}
