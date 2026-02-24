package org.qosp.notes.ui.tasks

interface TaskRecyclerListener {
    fun onDrag(viewHolder: TaskViewHolder)
    fun onTaskStatusChanged(position: Int, isDone: Boolean)
    fun onTaskContentChanged(position: Int, content: String)
    fun onNext(position: Int)
    /**
     * Called when a task is requested to be deleted.
     * @param position The adapter position of the task to be deleted.
     */
    fun onTaskDelete(position: Int)
    /**
     * Called when the indentation level of a task changes.
     * @param position The position of the task in the list.
     * @param newIndentationLevel The new indentation level (0 to MAX_INDENTATION_LEVELS).
     */
    fun onTaskIndentationChanged(position: Int, newIndentationLevel: Int)
}
