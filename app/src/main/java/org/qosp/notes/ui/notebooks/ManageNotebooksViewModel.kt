package org.qosp.notes.ui.notebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository
import javax.inject.Inject

@HiltViewModel
class ManageNotebooksViewModel @Inject constructor(
    private val notebookRepository: NotebookRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    fun deleteNotebooks(vararg notebooks: Notebook) {
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.delete(*notebooks)
        }
    }

    fun getSortNavdrawerNotebooksMethod() : String {
        return runBlocking {
            return@runBlocking preferenceRepository
                .getAll()
                .map { it.sortNavdrawerNotebooksMethod }
                .first()
                .name
        }
    }
}
