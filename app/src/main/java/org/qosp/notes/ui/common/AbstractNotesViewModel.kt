package org.qosp.notes.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.msoul.datastore.defaultOf
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod

abstract class AbstractNotesViewModel(
    protected val preferenceRepository: PreferenceRepository,
    protected val backendProvider: BackendProvider,
) : ViewModel() {

    protected abstract val provideNotes: (SortMethod) -> Flow<List<Note>>

    val data = preferenceRepository.getAll()
        .flatMapLatest { prefs ->
            provideNotes(prefs.sortMethod).map { notes ->
                Data(notes, prefs.sortMethod, prefs.layoutMode, prefs.noteDeletionTime.toDays())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Data())

    fun isSyncingEnabled(): Boolean = backendProvider.isSyncing

    data class Data(
        val notes: List<Note> = emptyList(),
        val sortMethod: SortMethod = defaultOf(),
        val layoutMode: LayoutMode = defaultOf(),
        val noteDeletionTimeInDays: Long = defaultOf<NoteDeletionTime>().toDays(),
    )
}
