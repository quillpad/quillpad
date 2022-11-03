package org.qosp.notes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.msoul.datastore.EnumPreference
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.preferences.PreferenceRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    syncManager: SyncManager,
) : ViewModel() {

    val appPreferences = preferenceRepository.getAll()
    val loggedInUsername = syncManager.config.map { it?.username }

    fun <T> setPreference(pref: T) where T : Enum<T>, T : EnumPreference {
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

    fun clearNextcloudCredentials() = viewModelScope.launch {
        preferenceRepository.putEncryptedStrings(
            PreferenceRepository.NEXTCLOUD_INSTANCE_URL to "",
            PreferenceRepository.NEXTCLOUD_PASSWORD to "",
            PreferenceRepository.NEXTCLOUD_USERNAME to "",
        )
    }
}
