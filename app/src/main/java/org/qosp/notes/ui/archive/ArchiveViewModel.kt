package org.qosp.notes.ui.archive

import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.common.AbstractNotesViewModel

class ArchiveViewModel(
    noteRepository: NoteRepository,
    preferenceRepository: PreferenceRepository,
    backendProvider: BackendProvider
) : AbstractNotesViewModel(preferenceRepository, backendProvider) {
    override val provideNotes = noteRepository::getArchived
}
