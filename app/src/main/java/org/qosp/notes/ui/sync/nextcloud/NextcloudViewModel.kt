package org.qosp.notes.ui.sync.nextcloud

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qosp.notes.data.sync.neu.BackendValidationResult
import org.qosp.notes.data.sync.neu.ValidateNextcloud
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.preferences.PreferenceRepository

class NextcloudViewModel(
    private val preferenceRepository: PreferenceRepository,
    private val validateNextcloud: ValidateNextcloud,
) : ViewModel() {

    val username = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_USERNAME)
    val password = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_PASSWORD)

    fun setURL(url: String) = viewModelScope.launch {
        if (!URLUtil.isHttpsUrl(url)) return@launch

        val url = if (url.endsWith("/")) url else "$url/"
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

        val response: BackendValidationResult = withContext(Dispatchers.IO) {
            val loginResult = validateNextcloud(config)
            loginResult
        }

        return response.also {
            if (it == BackendValidationResult.Success) {
                preferenceRepository.putEncryptedStrings(
                    PreferenceRepository.NEXTCLOUD_USERNAME to username,
                    PreferenceRepository.NEXTCLOUD_PASSWORD to password,
                )
            }
        }
    }
}
