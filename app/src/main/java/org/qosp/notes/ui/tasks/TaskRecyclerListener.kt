package org.qosp.notes.ui.tasks

interface TaskRecyclerListener {
    fun onDrag(viewHolder: TaskViewHolder)
    fun onTaskStatusChanged(position: Int, isDone: Boolean)
    fun onTaskContentChanged(position: Int, content: String)
    fun onNext(position: Int)
}
