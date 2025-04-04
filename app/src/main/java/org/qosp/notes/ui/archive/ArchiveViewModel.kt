package org.qosp.notes.ui.archive

import org.koin.android.annotation.KoinViewModel
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.common.AbstractNotesViewModel

@KoinViewModel
class ArchiveViewModel(
    noteRepository: NoteRepository,
    preferenceRepository: PreferenceRepository,
    syncManager: SyncManager
) : AbstractNotesViewModel(preferenceRepository, syncManager) {
    override val provideNotes = noteRepository::getArchived
}
