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
        val payloads = (payloads as MutableList<List<Payload>>).flatten()

        if (payloads.isEmpty()) {
            return super.onBindViewHolder(holder, position, payloads)
        }

        holder.runPayloads(getItem(position), payloads.map { it })
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

        override fun getChangePayload(oldItem: Note, newItem: Note): Any {
            return sequenceOf(
                Payload.TitleChanged to (oldItem.title != newItem.title),
                Payload.ContentChanged to (oldItem.content != newItem.content),
                Payload.PinChanged to (oldItem.isPinned != newItem.isPinned),
                Payload.MarkdownChanged to (oldItem.isMarkdownEnabled != newItem.isMarkdownEnabled),
                Payload.HiddenChanged to (oldItem.isHidden != newItem.isHidden),
                Payload.ColorChanged to (oldItem.color != newItem.color),
                Payload.ArchivedChanged to (oldItem.isArchived != newItem.isArchived),
                Payload.DeletedChanged to (oldItem.isDeleted != newItem.isDeleted),
                Payload.RemindersChanged to (oldItem.reminders != newItem.reminders),
                Payload.TagsChanged to (oldItem.tags != newItem.tags),
                Payload.AttachmentsChanged to (oldItem.attachments != newItem.attachments),
                Payload.TasksChanged to (oldItem.taskList != newItem.taskList),
            )
                .filter { (_, condition) -> condition }
                .map { (payload, _) -> payload }
                .toList()
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
