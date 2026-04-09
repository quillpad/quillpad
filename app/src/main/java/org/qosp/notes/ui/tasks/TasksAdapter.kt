package org.qosp.notes.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.databinding.LayoutTaskBinding
import java.lang.Float.min
import java.util.Collections

class TasksAdapter(
    private val inPreview: Boolean,
    var listener: TaskRecyclerListener?,
    private val markwon: Markwon,
) : RecyclerView.Adapter<TaskViewHolder>() {

    private var fontSize: Float = -1.0f
    private var hiddenTasks: MutableList<NoteTask> = mutableListOf()

    var tasks: MutableList<NoteTask> = mutableListOf()

    override fun getItemCount(): Int = tasks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding: LayoutTaskBinding =
            LayoutTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        // apply font size preference
        binding.editText.textSize = fontSize
        // checkboxes are only downscaled, because upscaled looks blurry
        val checkBoxScaleRatio: Float = if (fontSize > 0) min(fontSize / 16, 1.0F) else 1.0F // 16 because by default edit_text uses MaterialComponents.Body1 = 16sp
        binding.checkBox.scaleX = checkBoxScaleRatio
        binding.checkBox.scaleY = checkBoxScaleRatio

        return TaskViewHolder(parent.context, binding, listener, inPreview, markwon)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task: NoteTask = tasks[position]
        holder.bind(task)
    }

    fun moveItem(fromPos: Int, toPos: Int) {
        Collections.swap(tasks, fromPos, toPos)
        notifyItemMoved(fromPos, toPos)
    }

    fun submitList(list: List<NoteTask>?, useDiff: Boolean = true) {
        if (list != null) {
            if (useDiff) {
                DiffUtil.calculateDiff(DiffCallback(tasks, list), true).let { result ->
                    tasks = list.toMutableList()
                    result.dispatchUpdatesTo(this)
                }
            } else {
                tasks = list.toMutableList()
                notifyDataSetChanged()
            }
        }
    }

    fun setFontSize(fs: Float) {
        fontSize = fs
    }

    fun hide(absoluteAdapterPosition: Int) {
        val draggingTask = tasks[absoluteAdapterPosition]
        val workingTasks: MutableList<NoteTask> = tasks.toMutableList()
        val hideFrom= absoluteAdapterPosition + 1
        // Only move items below if they are actual children of the dragging task
        if (hideFrom < itemCount  &&
            tasks[hideFrom].parentId == draggingTask.id) {
            // Find the contiguous block of children
            for (x in hideFrom until itemCount) {
                // Stop if we hit a task that isn't a child of our draggingTask
                if (tasks[x].parentId != draggingTask.id) {
                    break
                } else {
                    hiddenTasks.add(tasks[x])
                    workingTasks.remove(tasks[x])
                }
            }
            tasks = workingTasks
            notifyItemRangeRemoved(hideFrom, hiddenTasks.size)
        }
    }

    fun finaliseMove(absoluteAdapterPosition: Int) {
        if (hiddenTasks.isNotEmpty()) { // moving a group of items
            // Re-insert children that were hidden during the drag
            // first, determine if we moved into an indented section -> attempt to find new parent
            val movedParent=tasks[absoluteAdapterPosition]
            val newParentId =findParentForPosition(absoluteAdapterPosition)
            if (newParentId!=null) {
                // adjust parent id of moved parent and all moved children
                movedParent.parentId = newParentId
                movedParent.indentationLevel++
                for (task in hiddenTasks) {
                    task.parentId = newParentId
                }
                tasks[absoluteAdapterPosition] = movedParent
                notifyItemChanged(absoluteAdapterPosition)
            }
            // re-insert moved children
            tasks.addAll(absoluteAdapterPosition+1, hiddenTasks)
            notifyItemRangeInserted(absoluteAdapterPosition+1, hiddenTasks.size)
            hiddenTasks= mutableListOf()
        }
        else { // a single item was moved:
            val movedTask = tasks[absoluteAdapterPosition]

            // 1. Force top item to be root
            if (absoluteAdapterPosition == 0) {
                movedTask.indentationLevel = 0
                movedTask.parentId = null
            }
            // 2. update parentId & indentation level based on the new position
            else {
                val newParentId = findParentForPosition(absoluteAdapterPosition)
                when {
                    newParentId != null && movedTask.indentationLevel == 0 -> {
                        // has a new parent (and had no parent before)
                        // increase indentation
                        movedTask.indentationLevel++
                    }
                    movedTask.parentId != newParentId && movedTask.indentationLevel > 0 -> {
                        // was indented, but got new parent
                        // do nothing, keep indendation level
                    }
                    (newParentId == null) && (movedTask.indentationLevel > 0) -> {
                        //had a parent, but not anymore
                        // decrease indentation
                        movedTask.indentationLevel--
                    }
                }
                movedTask.parentId = newParentId
            }
            tasks[absoluteAdapterPosition]=movedTask

            notifyItemChanged(absoluteAdapterPosition)
        }
    }

    private class DiffCallback(val oldList: List<NoteTask>, val newList: List<NoteTask>) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    /**
     * Helper to find the nearest item above the current position
     * that could be viewed as a parent.
     */
    fun findParentForPosition(position: Int): Long? {
        // 1. Initial Guards
        if (tasks.size <= 1) return null
        if (position <= 0 || position >= tasks.size) return null

        val currentLevel = tasks[position].indentationLevel

        // 2. last item
        if (position == tasks.size - 1) {
            val prev = tasks[position - 1]
            return when {
                prev.indentationLevel > 0 && currentLevel > 0 -> prev.parentId //already indented, use same item as parent
                prev.indentationLevel < currentLevel -> prev.id // not indented yet, use previous item as parent
                else -> null
            }
        }

        // 3. checking the next item
        val nextLevel = tasks[position + 1].indentationLevel
        if (nextLevel > currentLevel || (nextLevel == currentLevel && currentLevel > 0)) { //next item is indented
            return tasks[position + 1].parentId
        }

        // 4. all other cases: look upwards for task with lower indentation -> use as parent
        for (i in position - 1 downTo 0) {
            if (tasks[i].indentationLevel < currentLevel) {
                return tasks[i].id
            }
        }

        return null
    }

    /**
     * get all children belonging to the item that has the id "parentId"
     * side effect: change "done" status of all items (parent and children)
     * based on previous "done" status of parent
     */
    fun getChangedSection(parentId: Long?): MutableList<NoteTask> {
        val section = mutableListOf<NoteTask>()
        val position = tasks.indexOfFirst { it.id == parentId }
        if (position == -1) return section

        val parentTask= tasks[position]
        val parentIsDone = !parentTask.isDone // change status
        val newParentTask = parentTask.copy(isDone = parentIsDone)
        section.add(newParentTask) // include parent

        // identify direct children
        // -> look for contiguous items that have parentTask as parent
        var nextIdx = position + 1

        while (nextIdx < tasks.size && tasks[nextIdx].parentId == parentId) {
            section.add(tasks[nextIdx].copy(isDone = parentIsDone))
            // while adding, change isDone to reflect parent status
            nextIdx++
        }
        return section
    }

    /**
     * Finds the index of the last undone child or the first completed child.
     * Returns -1 if the parentId is not found.
     */
    fun findTargetDoneChildPosition(parentId: Long): Int {
        // Find the parent's current position in the list
        val parentPos = tasks.indexOfFirst { it.id == parentId }
        if (parentPos == -1) return -1

        val targetPos= tasks.indexOfLast { it.parentId == parentId }
        return targetPos

    }

    /**
     * Finds the index of the first done child
     * If no done child exists, return the index of the first child.
     * Returns -1 if the parentId is not found.
     */
    fun findTargetUnDoneChildPosition(parentId: Long): Int {
        val parentPos = tasks.indexOfFirst { it.id == parentId }
        if (parentPos == -1) return -1

        // find the first done child
        var targetPos=tasks.indexOfFirst { it.parentId == parentId && it.isDone}
        if (targetPos == -1)
            // if no done child exists, use the first child position
            targetPos = tasks.indexOfFirst { it.parentId == parentId }
        return targetPos
    }


}
