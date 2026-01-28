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

    fun submitList(list: List<NoteTask>?) {
        if (list != null) {
            DiffUtil.calculateDiff(DiffCallback(tasks, list), true).let { result ->
                tasks = list.toMutableList()
                result.dispatchUpdatesTo(this)
            }
        }
    }

    fun setFontSize(fs: Float) {
        fontSize = fs
    }

    fun hide(absoluteAdapterPosition: Int) {
        val workingTasks: MutableList<NoteTask> = tasks.toMutableList()
        val hideFrom= absoluteAdapterPosition + 1
        //hide  if we have indented items directly below current position
        if (hideFrom < itemCount  &&
            tasks[hideFrom].indentationLevel > tasks[absoluteAdapterPosition].indentationLevel ) {
            // find end position
            for (x in hideFrom until itemCount) {
                if (tasks[x].indentationLevel == 0) {
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
        if (!hiddenTasks.isEmpty()) { // move hidden tasks as well
            tasks.addAll(absoluteAdapterPosition+1, hiddenTasks)
            notifyItemRangeInserted(absoluteAdapterPosition+1, hiddenTasks.size)
            hiddenTasks= mutableListOf()
        }
        else // add indentation (if necessary) (only applies when moving a single item into a nested list)
        if (absoluteAdapterPosition+1< itemCount && tasks[absoluteAdapterPosition+1].indentationLevel > 0 ) {
            tasks[absoluteAdapterPosition].indentationLevel = tasks[absoluteAdapterPosition+1].indentationLevel
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
}
