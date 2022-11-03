package org.qosp.notes.preferences

import me.msoul.datastore.defaultOf

data class AppPreferences(
    val layoutMode: LayoutMode = defaultOf(),
    val themeMode: ThemeMode = defaultOf(),
    val darkThemeMode: DarkThemeMode = defaultOf(),
    val colorScheme: ColorScheme = defaultOf(),
    val sortMethod: SortMethod = defaultOf(),
    val backupStrategy: BackupStrategy = defaultOf(),
    val noteDeletionTime: NoteDeletionTime = defaultOf(),
    val dateFormat: DateFormat = defaultOf(),
    val timeFormat: TimeFormat = defaultOf(),
    val openMediaIn: OpenMediaIn = defaultOf(),
    val showDate: ShowDate = defaultOf(),
    val groupNotesWithoutNotebook: GroupNotesWithoutNotebook = defaultOf(),
    val cloudService: CloudService = defaultOf(),
    val syncMode: SyncMode = defaultOf(),
    val backgroundSync: BackgroundSync = defaultOf(),
    val newNotesSyncable: NewNotesSyncable = defaultOf(),
)
