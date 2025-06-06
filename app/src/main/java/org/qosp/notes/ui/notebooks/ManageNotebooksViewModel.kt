package org.qosp.notes.ui.notebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository

class ManageNotebooksViewModel(
    private val notebookRepository: NotebookRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    fun deleteNotebooks(vararg notebooks: Notebook) {
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.delete(*notebooks)
        }
    }

    fun getSortNavdrawerNotebooksMethod(): String {
        return runBlocking {
            preferenceRepository.getAll().first().sortNavdrawerNotebooksMethod.name
        }
    }
}
