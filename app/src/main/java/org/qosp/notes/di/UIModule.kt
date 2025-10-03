package org.qosp.notes.di

import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.qosp.notes.ui.ActivityViewModel
import org.qosp.notes.ui.archive.ArchiveViewModel
import org.qosp.notes.ui.attachments.dialog.AttachmentDialogViewModel
import org.qosp.notes.ui.deleted.DeletedViewModel
import org.qosp.notes.ui.editor.EditorViewModel
import org.qosp.notes.ui.launcher.LauncherViewModel
import org.qosp.notes.ui.main.MainViewModel
import org.qosp.notes.ui.notebooks.ManageNotebooksViewModel
import org.qosp.notes.ui.notebooks.dialog.NotebookDialogViewModel
import org.qosp.notes.ui.reminders.EditReminderViewModel
import org.qosp.notes.ui.search.SearchViewModel
import org.qosp.notes.ui.settings.SettingsViewModel
import org.qosp.notes.ui.sync.nextcloud.NextcloudViewModel
import org.qosp.notes.ui.tags.TagsViewModel
import org.qosp.notes.ui.tags.dialog.TagDialogViewModel

object UIModule {
    val uiModule = module {
        viewModelOf(::EditorViewModel)
        viewModelOf(::ActivityViewModel)
        viewModelOf(::ArchiveViewModel)
        viewModelOf(::TagsViewModel)
        viewModelOf(::TagDialogViewModel)
        viewModelOf(::NextcloudViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::EditReminderViewModel)
        viewModelOf(::ManageNotebooksViewModel)
        viewModelOf(::NotebookDialogViewModel)
        viewModelOf(::MainViewModel)
        viewModel { LauncherViewModel(androidApplication(), get()) }
        viewModelOf(::DeletedViewModel)
        viewModelOf(::AttachmentDialogViewModel)
    }
}
