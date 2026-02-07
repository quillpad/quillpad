package org.qosp.notes.ui.common.recycler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.qosp.notes.data.model.Note
import org.qosp.notes.databinding.LayoutNoteBinding

class NoteRecyclerAdapter(
    var listener: NoteRecyclerListener?,
    private val markwon: Markwon,
) : ExtendedListAdapter<Note, NoteViewHolder>(DiffCallback()) {

    private var allItems = listOf<Note>()
    private var visibleItems = listOf<Note>()
    var searchMode: Boolean = false

    private val tasksViewPool = RecyclerView.RecycledViewPool()
    private val attachmentsViewPool = RecyclerView.RecycledViewPool()

    var showHiddenNotes: Boolean = false
        set(value) {
            field = value
            if (field) {
                super.submitList(allItems)
            } else {
                super.submitList(visibleItems)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = LayoutNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(
            binding = binding,
            listener = listener,
            context = parent.context,
            searchMode = searchMode,
            markwon = markwon,
            tasksViewPool = tasksViewPool,
            attachmentsViewPool = attachmentsViewPool,
        )
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            return super.onBindViewHolder(holder, position, payloads)
        }

        val note = getItem(position)
        val combinedPayloads = mutableSetOf<Payload>()

        for (payload in payloads) {
            if (payload is List<*>) {
                for (item in payload) {
                    if (item is Payload) {
                        combinedPayloads.add(item)
                    }
                }
            }
        }

        if (combinedPayloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.runPayloads(note, combinedPayloads)
        }
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val note: Note = getItem(position)
        holder.bind(note)
    }

    override fun getItemId(position: Int) = getItem(position).id

    override fun submitList(list: List<Note>?) {
        if (list != null) {
            allItems = list
            visibleItems = list.filterNot { it.isHidden }

            if (showHiddenNotes) {
                super.submitList(allItems)
            } else {
                super.submitList(visibleItems)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: Note, newItem: Note): Any? {
            val payloads = mutableListOf<Payload>()
            if (oldItem.title != newItem.title) payloads.add(Payload.TitleChanged)
            if (oldItem.content != newItem.content) payloads.add(Payload.ContentChanged)
            if (oldItem.isPinned != newItem.isPinned) payloads.add(Payload.PinChanged)
            if (oldItem.isMarkdownEnabled != newItem.isMarkdownEnabled) payloads.add(Payload.MarkdownChanged)
            if (oldItem.isHidden != newItem.isHidden) payloads.add(Payload.HiddenChanged)
            if (oldItem.color != newItem.color) payloads.add(Payload.ColorChanged)
            if (oldItem.isArchived != newItem.isArchived) payloads.add(Payload.ArchivedChanged)
            if (oldItem.isDeleted != newItem.isDeleted) payloads.add(Payload.DeletedChanged)
            if (oldItem.reminders != newItem.reminders) payloads.add(Payload.RemindersChanged)
            if (oldItem.tags != newItem.tags) payloads.add(Payload.TagsChanged)
            if (oldItem.attachments != newItem.attachments) payloads.add(Payload.AttachmentsChanged)
            if (oldItem.taskList != newItem.taskList) payloads.add(Payload.TasksChanged)

            return payloads.takeIf { it.isNotEmpty() }
        }
    }

    enum class Payload {
        TitleChanged,
        ArchivedChanged,
        DeletedChanged,
        ContentChanged,
        PinChanged,
        MarkdownChanged,
        HiddenChanged,
        ColorChanged,
        TagsChanged,
        RemindersChanged,
        AttachmentsChanged,
        TasksChanged,
    }
}
