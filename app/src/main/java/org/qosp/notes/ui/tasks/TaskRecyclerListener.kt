package org.qosp.notes.ui.tasks

import org.qosp.notes.data.model.NoteTask

interface TaskRecyclerListener {
    fun onDrag(viewHolder: TaskViewHolder)
    fun onTaskStatusChanged(position: Int, isDone: Boolean)
    fun onTaskContentChanged(position: Int, content: String)
    fun onNext(position: Int)
    fun onRequestSuggestions(position: Int, query: String): List<NoteTask>
    fun onSuggestionSelected(position: Int, suggestedTask: NoteTask)
}
