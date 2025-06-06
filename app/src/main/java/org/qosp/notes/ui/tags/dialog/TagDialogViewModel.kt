package org.qosp.notes.ui.tags.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.qosp.notes.data.model.Tag
import org.qosp.notes.data.repo.TagRepository

class TagDialogViewModel(private val tagRepository: TagRepository) : ViewModel() {

    fun insertTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.insert(tag)
        }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.update(tag)
        }
    }

    suspend fun tagExistsByName(name: String, ignoreId: Long? = null): Boolean {
        val tag = tagRepository.getByName(name).first()
        return tag != null && (if (ignoreId != null) tag.id != ignoreId else true)
    }
}
