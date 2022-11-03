package org.qosp.notes.ui.notebooks.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NotebookRepository
import javax.inject.Inject

@HiltViewModel
class NotebookDialogViewModel @Inject constructor(private val notebookRepository: NotebookRepository) : ViewModel() {
    fun insertNotebook(notebook: Notebook) {
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.insert(notebook)
        }
    }

    fun updateNotebook(notebook: Notebook) {
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.update(notebook)
        }
    }

    suspend fun notebookExistsByName(name: String, ignoreId: Long? = null): Boolean {
        val notebook = notebookRepository.getByName(name).first()
        return notebook != null && (if (ignoreId != null) notebook.id != ignoreId else true)
    }
}
