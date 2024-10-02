package org.qosp.notes.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tfcporciuncula.flow.FlowSharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import me.msoul.datastore.EnumPreference
import me.msoul.datastore.getEnum
import me.msoul.datastore.setEnum
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceRepository(
    val dataStore: DataStore<Preferences>,
    private val sharedPreferences: FlowSharedPreferences,
) {
    fun getEncryptedString(key: String): Flow<String> {
        return sharedPreferences.getString(key, "").asFlow()
    }

    fun getAll(): Flow<AppPreferences> {
        return dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { prefs ->
                AppPreferences(
                    layoutMode = prefs.getEnum(),
                    themeMode = prefs.getEnum(),
                    darkThemeMode = prefs.getEnum(),
                    colorScheme = prefs.getEnum(),
                    sortMethod = prefs.getEnum(),
                    sortTagsMethod = prefs.getEnum(),
                    sortNavdrawerNotebooksMethod = prefs.getEnum(),
                    backupStrategy = prefs.getEnum(),
                    noteDeletionTime = prefs.getEnum(),
                    dateFormat = prefs.getEnum(),
                    timeFormat = prefs.getEnum(),
                    openMediaIn = prefs.getEnum(),
                    showDate = prefs.getEnum(),
                    editorFontSize = prefs.getEnum(),
                    showFabChangeMode = prefs.getEnum(),
                    groupNotesWithoutNotebook = prefs.getEnum(),
                    moveCheckedItems = prefs.getEnum(),
                    cloudService = prefs.getEnum(),
                    syncMode = prefs.getEnum(),
                    backgroundSync = prefs.getEnum(),
                    newNotesSyncable = prefs.getEnum(),
                )
            }
    }

    inline fun <reified T> get(): Flow<T> where T : Enum<T>, T : EnumPreference {
        return dataStore.getEnum()
    }

    suspend fun putEncryptedStrings(vararg pairs: Pair<String, String>) {
        pairs.forEach { (key, value) ->
            sharedPreferences.getString(key).setAndCommit(value)
        }
    }

    suspend fun <T> set(preference: T) where T : Enum<T>, T : EnumPreference {
        dataStore.setEnum(preference)
    }

    companion object {
        const val NEXTCLOUD_INSTANCE_URL = "NEXTCLOUD_INSTANCE_URL"
        const val NEXTCLOUD_USERNAME = "NEXTCLOUD_USERNAME"
        const val NEXTCLOUD_PASSWORD = "NEXTCLOUD_PASSWORD"
    }
}
