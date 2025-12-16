package org.qosp.notes.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.databinding.LayoutTaskSuggestionBinding

class TaskSuggestionAdapter(
    private val onSuggestionClicked: (NoteTask) -> Unit
) : ListAdapter<NoteTask, TaskSuggestionViewHolder>(TaskSuggestionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskSuggestionViewHolder {
        val binding = LayoutTaskSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskSuggestionViewHolder(binding, onSuggestionClicked)
    }

    override fun onBindViewHolder(holder: TaskSuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class TaskSuggestionViewHolder(
    private val binding: LayoutTaskSuggestionBinding,
    private val onSuggestionClicked: (NoteTask) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(task: NoteTask) {
        binding.textSuggestion.text = task.content
        binding.root.setOnClickListener {
            onSuggestionClicked(task)
        }
    }
}

class TaskSuggestionDiffCallback : DiffUtil.ItemCallback<NoteTask>() {
    override fun areItemsTheSame(oldItem: NoteTask, newItem: NoteTask): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: NoteTask, newItem: NoteTask): Boolean {
        return oldItem == newItem
    }
}
