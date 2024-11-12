package org.qosp.notes.ui.tags

import android.util.Log
import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.msoul.datastore.defaultOf
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Tag
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortTagsMethod
import javax.inject.Inject

data class TagData(val tag: Tag, val inNote: Boolean)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    fun getData(noteId: Long? = null): Flow<List<TagData>> {
        return when (noteId) {
            null -> tagRepository.getAll().map { tags ->
                tags.map { TagData(it, false) }
            }
            else -> tagRepository.getByNoteId(noteId).flatMapLatest { noteTags ->
                tagRepository.getAll().map { tags ->
                    tags.map { TagData(it, it in noteTags) }
                }
            }
        }
    }

    fun getSortTagsMethod() : String {
        return runBlocking {
            return@runBlocking preferenceRepository
                .getAll()
                .map { it.sortTagsMethod }
                .first()
                .name
        }
    }

    suspend fun insert(tag: Tag): Long {
        return withContext(Dispatchers.IO) {
            tagRepository.insert(tag)
        }
    }

    fun delete(vararg tags: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.delete(*tags)
        }
    }

    fun addTagToNote(tagId: Long, noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.addTagToNote(tagId, noteId)
        }
    }

    fun deleteTagFromNote(tagId: Long, noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.deleteTagFromNote(tagId, noteId)
        }
    }

    data class TagDataList(
        val tagsList: MutableList<TagData> = mutableListOf(),
        val sortTagsMethod: SortTagsMethod = defaultOf()
    )

}
