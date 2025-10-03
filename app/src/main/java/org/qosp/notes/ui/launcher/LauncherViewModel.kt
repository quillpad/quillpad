package org.qosp.notes.ui.launcher

import android.app.Application
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.mamoe.yamlkt.Yaml
import org.qosp.notes.data.WhatsNew
import org.qosp.notes.data.WhatsNewItem
import org.qosp.notes.preferences.PreferenceRepository

class LauncherViewModel(
    private val application: Application,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val whatsNew: WhatsNew by lazy {
        val yamlText = application.assets.open("whatsnew.yaml").bufferedReader().use { it.readText() }
        Yaml.decodeFromString(WhatsNew.serializer(), yamlText)
    }

    fun whatsNewToShow(): Flow<WhatsNewItem?> = preferenceRepository.getEncryptedString(LAST_SHOWN_WHATS_NEW_ID)
        .map { it.toIntOrNull() ?: 1 }
        .map { lastShownId ->
            val latest = whatsNew.updates.maxByOrNull { it.id }
            latest?.takeIf { latest.id > lastShownId }
        }

    suspend fun setLatestWhatsNewId() {
        val latestId = whatsNew.updates.maxOfOrNull { it.id } ?: 0
        preferenceRepository.putEncryptedStrings(LAST_SHOWN_WHATS_NEW_ID to latestId.toString())
    }

    companion object {
        private const val LAST_SHOWN_WHATS_NEW_ID = "LAST_SHOWN_WHATS_NEW_ID"
    }
}
