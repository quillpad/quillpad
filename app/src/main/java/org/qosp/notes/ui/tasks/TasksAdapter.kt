package org.qosp.notes.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.databinding.LayoutTaskBinding
import java.util.*

class TasksAdapter(
    private val inPreview: Boolean,
    var listener: TaskRecyclerListener?,
    private val markwon: Markwon,
) : RecyclerView.Adapter<TaskViewHolder>() {

    var tasks: MutableList<NoteTask> = mutableListOf()

    override fun getItemCount(): Int = tasks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding: LayoutTaskBinding =
            LayoutTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
