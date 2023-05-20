package org.qosp.notes.ui.common.recycler

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.commonmark.node.Code
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.data.model.Tag
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.ui.attachments.recycler.AttachmentViewHolder
import org.qosp.notes.ui.attachments.recycler.AttachmentsAdapter
import org.qosp.notes.ui.attachments.recycler.AttachmentsPreviewGridManager
import org.qosp.notes.ui.editor.markdown.applyTo
import org.qosp.notes.ui.tasks.TasksAdapter
import org.qosp.notes.ui.utils.dp
import org.qosp.notes.ui.utils.ellipsize
import org.qosp.notes.ui.utils.resId

class NoteViewHolder(
    private val binding: LayoutNoteBinding,
    listener: NoteRecyclerListener?,
    private val context: Context,
    private val searchMode: Boolean,
    private val markwon: Markwon,
) : RecyclerView.ViewHolder(binding.root), SelectableViewHolder {

    private val tasksAdapter = TasksAdapter(true, null, markwon)
    private val attachmentsAdapter = AttachmentsAdapter(null, true)

    private val defaultStrokeWidth = 1.dp(context)
    private val selectedStrokeWidth = 2.dp(context)

    init {
        binding.recyclerAttachments.apply {
            layoutManager = AttachmentsPreviewGridManager(context, 2)
            adapter = attachmentsAdapter
        }

        binding.recyclerTasks.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = tasksAdapter
        }

        if (listener != null) {
            itemView.setOnClickListener { listener.onItemClick(bindingAdapterPosition, binding) }
            itemView.setOnLongClickListener { listener.onLongClick(bindingAdapterPosition, binding) }
        }
    }

    private fun updateBackgroundColor(color: NoteColor) {
        color.resId(context)?.let { resId ->
            binding.root.setCardBackgroundColor(resId)
            binding.linearLayout.setBackgroundColor(resId)
        }
    }

    private fun updateTags(tags: List<Tag>) {
        binding.containerTags.removeAllViews()
        binding.containerTags.isVisible = tags.isNotEmpty()

        if (tags.isEmpty()) return

        for (tag in tags) {
            val tagView = TextView(ContextThemeWrapper(context, R.style.TagChip))
            if (binding.containerTags.childCount > 0) {
                tagView.text = "+${tags.size - binding.containerTags.childCount}"
                binding.containerTags.addView(tagView)
                break
            }
            tagView.text = "# ${tag.name}"
            binding.containerTags.addView(tagView)
        }
    }

    private fun updateIndicatorIcons(note: Note, hasReminders: Boolean) = with(binding) {
        indicatorNoteHidden.isVisible = note.isHidden && !searchMode
        indicatorPinned.isVisible = note.isPinned && !searchMode
        indicatorHasReminder.isVisible = hasReminders
        indicatorDeleted.isVisible = note.isDeleted && searchMode
        indicatorArchived.isVisible = note.isArchived && searchMode
    }

    private fun setTitle(note: Note) {
        binding.textViewTitle.text = note.title.ifEmpty { context.getString(R.string.indicator_untitled) }
    }

    private fun setContent(note: Note) = with(binding) {
        recyclerTasks.isVisible = note.isList && note.taskList.isNotEmpty() && !note.isCompactPreview
        indicatorMoreTasks.isVisible = false
        textViewContent.isVisible = !note.isList && note.content.isNotEmpty() && !note.isCompactPreview

        val taskList = note.taskList.takeIf { it.size <= 8 } ?: note.taskList.subList(0, 8).also {
            val moreItems = note.taskList.size - 8

            indicatorMoreTasks.isVisible = true
            indicatorMoreTasks.text = context.resources.getQuantityString(R.plurals.more_items, moreItems, moreItems)
        }

        tasksAdapter.submitList(taskList)
        textViewContent.ellipsize()

        if (note.isMarkdownEnabled && note.content.isNotBlank()) {
            try {
                markwon.applyTo(textViewContent, note.content) {
                    maximumTableColumns = 4
                    tableReplacement = { Code(context.getString(R.string.message_cannot_preview_table)) }
                }
            } catch(e: Throwable) {
                textViewContent.text = ""
            }
        } else {
            textViewContent.text = note.content
        }
    }

    private fun setupAttachments(attachments: List<Attachment>) {
        binding.recyclerAttachments.isVisible = attachments.isNotEmpty()
        if (attachments.isEmpty()) return

        val layoutManager = binding.recyclerAttachments.layoutManager as AttachmentsPreviewGridManager

        val list = attachments.take(attachments.size.coerceAtMost(4))
        val remaining = attachments.size - list.size
        layoutManager.allocateSpans(list.size)
        attachmentsAdapter.submitList(list)

        if (remaining > 0) {
            binding.recyclerAttachments.doOnPreDraw {
                (binding.recyclerAttachments.findViewHolderForAdapterPosition(3) as? AttachmentViewHolder)
                    ?.showMoreAttachmentsIndicator(remaining)
            }
        }
    }

    fun runPayloads(note: Note, payloads: List<NoteRecyclerAdapter.Payload>) {
        payloads.forEach {
            when (it) {
                NoteRecyclerAdapter.Payload.TitleChanged -> setTitle(note)
                NoteRecyclerAdapter.Payload.ContentChanged -> setContent(note)
                NoteRecyclerAdapter.Payload.PinChanged -> updateIndicatorIcons(
                    note,
                    note.reminders.isNotEmpty()
                )
                NoteRecyclerAdapter.Payload.MarkdownChanged -> setContent(note)
                NoteRecyclerAdapter.Payload.HiddenChanged -> updateIndicatorIcons(
                    note,
                    note.reminders.isNotEmpty()
                )
                NoteRecyclerAdapter.Payload.ColorChanged -> updateBackgroundColor(note.color)
                NoteRecyclerAdapter.Payload.ArchivedChanged -> updateIndicatorIcons(
                    note,
                    note.reminders.isNotEmpty()
                )
                NoteRecyclerAdapter.Payload.DeletedChanged -> updateIndicatorIcons(
                    note,
                    note.reminders.isNotEmpty()
                )
                NoteRecyclerAdapter.Payload.AttachmentsChanged -> setupAttachments(note.attachments)
                NoteRecyclerAdapter.Payload.TagsChanged -> updateTags(note.tags)
                NoteRecyclerAdapter.Payload.RemindersChanged -> updateIndicatorIcons(
                    note,
                    note.reminders.isNotEmpty()
                )
                NoteRecyclerAdapter.Payload.TasksChanged -> setContent(note)
            }
        }
    }

    fun bind(note: Note) {
        setContent(note)
        setTitle(note)
        updateBackgroundColor(note.color)
        updateIndicatorIcons(note, note.reminders.isNotEmpty())
        updateTags(note.tags)
        setupAttachments(note.attachments)

        ViewCompat.setTransitionName(binding.root, "editor_${note.id}")
    }

    override fun onSelectedStatusChanged(isSelected: Boolean) {
        binding.root.isChecked = isSelected
        binding.root.strokeWidth = if (isSelected) selectedStrokeWidth else defaultStrokeWidth
    }
}
