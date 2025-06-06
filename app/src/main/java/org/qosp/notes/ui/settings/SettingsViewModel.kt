package org.qosp.notes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.msoul.datastore.EnumPreference
import org.koin.android.annotation.KoinViewModel
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SyncMode

@KoinViewModel
class SettingsViewModel(private val preferenceRepository: PreferenceRepository) : ViewModel() {

    val appPreferences = preferenceRepository.getAll()
    val loggedInUsername = NextcloudConfig.fromPreferences(preferenceRepository).map { it?.username }

    fun <T> setPreference(pref: T) where T : Enum<T>, T : EnumPreference {
        when (pref) {
            is CloudService -> {
                if (pref in listOf(CloudService.FILE_STORAGE, CloudService.DISABLED)) {
                    setPreference(SyncMode.ALWAYS)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.set(pref)
        }
    }

    suspend fun <T> setPreferenceSuspending(pref: T) where T : Enum<T>, T : EnumPreference {
        preferenceRepository.set(pref)
    }

    fun getEncryptedString(key: String): Flow<String> {
        return preferenceRepository.getEncryptedString(key)
    }

    fun setEncryptedString(key: String, value: String) =
        viewModelScope.launch { preferenceRepository.putEncryptedStrings(key to value) }

    fun clearNextcloudCredentials() = viewModelScope.launch {
        preferenceRepository.putEncryptedStrings(
            PreferenceRepository.NEXTCLOUD_INSTANCE_URL to "",
            PreferenceRepository.NEXTCLOUD_PASSWORD to "",
            PreferenceRepository.NEXTCLOUD_USERNAME to "",
        )
    }
}
