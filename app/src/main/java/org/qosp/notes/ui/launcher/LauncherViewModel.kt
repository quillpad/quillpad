package org.qosp.notes.ui.launcher

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.qosp.notes.BuildConfig
import org.qosp.notes.preferences.PreferenceRepository

class LauncherViewModel(private val preferenceRepository: PreferenceRepository) : ViewModel() {

    val isCurrentVersionInstalled: Flow<Boolean> = preferenceRepository
        .getEncryptedString(LAST_INSTALLED_VERSION)
        .map { lastInstalledVersion ->
            BuildConfig.VERSION_CODE.toString() == lastInstalledVersion
        }

    suspend fun updateLastInstalledVersion() {
        val thisVersion = BuildConfig.VERSION_CODE.toString()
        Log.d(TAG, "updateLastInstalledVersion: $thisVersion")
        preferenceRepository.putEncryptedStrings(LAST_INSTALLED_VERSION to thisVersion)
    }

    companion object {
        private const val TAG = "LauncherViewModel"
        private const val LAST_INSTALLED_VERSION = "LAST_INSTALLED_VERSION"
    }
}
