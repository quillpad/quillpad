package org.qosp.notes.ui.editor

import android.app.AlarmManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.clearFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.commonmark.node.Code
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.databinding.FragmentEditorBinding
import org.qosp.notes.databinding.LayoutAttachmentBinding
import org.qosp.notes.ui.attachments.dialog.EditAttachmentDialog
import org.qosp.notes.ui.attachments.fromUri
import org.qosp.notes.ui.attachments.recycler.AttachmentRecyclerListener
import org.qosp.notes.ui.attachments.recycler.AttachmentsAdapter
import org.qosp.notes.ui.attachments.recycler.AttachmentsGridManager
import org.qosp.notes.ui.attachments.uri
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.common.showMoveToNotebookDialog
import org.qosp.notes.ui.editor.dialog.InsertHyperlinkDialog
import org.qosp.notes.ui.editor.dialog.InsertImageDialog
import org.qosp.notes.ui.editor.dialog.InsertTableDialog
import org.qosp.notes.ui.editor.markdown.*
import org.qosp.notes.ui.media.MediaActivity
import org.qosp.notes.ui.recorder.RECORDED_ATTACHMENT
import org.qosp.notes.ui.recorder.RECORD_CODE
import org.qosp.notes.ui.recorder.RecordAudioDialog
import org.qosp.notes.ui.reminders.EditReminderDialog
import org.qosp.notes.ui.tasks.TaskRecyclerListener
import org.qosp.notes.ui.tasks.TaskViewHolder
import org.qosp.notes.ui.tasks.TasksAdapter
import org.qosp.notes.ui.utils.*
import org.qosp.notes.ui.utils.views.BottomSheet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.inject.Inject

private typealias Data = EditorViewModel.Data

@AndroidEntryPoint
class EditorFragment : BaseFragment(R.layout.fragment_editor) {
    private val binding by viewBinding(FragmentEditorBinding::bind)
    private val model: EditorViewModel by viewModels()

    private val args: EditorFragmentArgs by navArgs()
    private var snackbar: Snackbar? = null
    private var mainMenu: Menu? = null
    private var contentHasFocus: Boolean = false
    private var isNoteDeleted: Boolean = false
    private var markwonTextWatcher: TextWatcher? = null
    private var onBackPressHandled: Boolean = false

    @ColorInt
    private var backgroundColor: Int = Color.TRANSPARENT
    private var data = Data()

    private var nextTaskId: Long = 0L
    private var isList: Boolean = false
    private var isFirstLoad: Boolean = true
    private var formatter: DateTimeFormatter? = null

    private lateinit var attachmentsAdapter: AttachmentsAdapter
    private lateinit var tasksAdapter: TasksAdapter

    @Inject
    lateinit var markwon: Markwon

    @Inject
    lateinit var markwonEditor: MarkwonEditor

    override val hasDefaultAnimation = false
    override val toolbar: Toolbar
        get() = binding.toolbar

    private val requestMediaLauncher = registerForActivityResult(ChooseFilesContract) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult

        val attachments = uris.map {
            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Attachment.fromUri(requireContext(), it)
        }

        model.insertAttachments(*attachments.toTypedArray())
    }

    private val takePhotoLauncher = registerForActivityResult(TakePictureContract) { saved ->
        if (!saved) return@registerForActivityResult
        val uri = activityModel.tempPhotoUri ?: return@registerForActivityResult

        model.insertAttachments(Attachment.fromUri(requireContext(), uri))
        activityModel.tempPhotoUri = null
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(UP or DOWN, LEFT or RIGHT) {
        override fun isLongPressDragEnabled() = false

        override fun isItemViewSwipeEnabled() = model.inEditMode

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.5F

        override fun getSwipeEscapeVelocity(defaultValue: Float) = 3 * defaultValue

        override fun getSwipeVelocityThreshold(defaultValue: Float) = defaultValue / 3

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            tasksAdapter.tasks.removeAt(viewHolder.bindingAdapterPosition)
            model.updateTaskList(tasksAdapter.tasks)
            tasksAdapter.notifyItemRemoved(viewHolder.bindingAdapterPosition)
            tasksAdapter.notifyItemRangeChanged(viewHolder.bindingAdapterPosition, tasksAdapter.tasks.size - 1)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            tasksAdapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            when (actionState) {
                ACTION_STATE_DRAG -> {
                    val top = viewHolder.itemView.top + dY
                    val bottom = top + viewHolder.itemView.height
                    if (top > 0 && bottom < recyclerView.height) {
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }
                }

                ACTION_STATE_SWIPE -> {
                    val newDx = dX / 3
                    val p = Paint().apply { color = context?.resolveAttribute(R.attr.colorTaskSwipe) ?: Color.RED }
                    val itemView = viewHolder.itemView
                    val icon = context?.getDrawableCompat(R.drawable.ic_indicator_delete_task)?.toBitmap()
                    val height = itemView.bottom - itemView.top
                    val size = (24).dp(requireContext())

                    if (dX < 0) {
                        val background = RectF(
                            itemView.right.toFloat() + newDx,
                            itemView.top.toFloat(),
                            itemView.right.toFloat(),
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, p)

                        val iconRect = RectF(
                            background.right - size - 16.dp(requireContext()),
                            background.top + (height - size) / 2,
                            background.right - 16.dp(requireContext()),
                            background.bottom - (height - size) / 2,
                        )
                        if (icon != null) c.drawBitmap(icon, null, iconRect, p)
                    } else if (dX > 0) {
                        val background = RectF(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            newDx,
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, p)
                        val iconRect = RectF(
                            background.left + 16.dp(requireContext()),
                            background.top + (height - size) / 2,
                            background.left + size + 16.dp(requireContext()),
                            background.bottom - (height - size) / 2,
                        )
                        if (icon != null) c.drawBitmap(icon, null, iconRect, p)
                    }
                    return super.onChildDraw(c, recyclerView, viewHolder, newDx, dY, actionState, isCurrentlyActive)
                }
            }
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)

            (viewHolder as TaskViewHolder?)?.let { vh ->
                vh.taskBackgroundColor = backgroundColor
                vh.isBeingMoved = true
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            (viewHolder as TaskViewHolder?)?.let {
                if (it.isBeingMoved) it.isBeingMoved = false
            }
            model.updateTaskList(tasksAdapter.tasks)
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment
            duration = 300L
            scrimColor = Color.TRANSPARENT

            requireContext().resolveAttribute(R.attr.colorBackground)?.let { setAllContainerColors(it) }
        }
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply { duration = 300L }

        postponeEnterTransition()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        data = Data()
        isFirstLoad = true

        if (model.isNotInitialized) {
            model.initialize(
                noteId = args.noteId,
                newNoteTitle = args.newNoteTitle,
                newNoteContent = args.newNoteContent,
                newNoteAttachments = args.newNoteAttachments?.toList() ?: emptyList(),
                newNoteIsList = args.newNoteIsList,
                newNoteNotebookId = args.newNoteNotebookId.takeIf { it > 0L }
            )
        }

        setupAttachmentsRecycler()
        setupTasksRecycler()
        observeData()
        setupEditTexts()
        setupMarkdown()
        setupListeners()

        toolbar.setTitleTextColor(Color.TRANSPARENT)
        ViewCompat.setTransitionName(binding.root, args.transitionName)
        binding.scrollView.liftAppBarOnScroll(
            binding.layoutAppBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )

        setFragmentResultListener(RECORD_CODE) { s, bundle ->
            val attachment = bundle.getParcelable<Attachment>(RECORDED_ATTACHMENT) ?: return@setFragmentResultListener
            model.insertAttachments(attachment)
        }

        setFragmentResultListener(MARKDOWN_DIALOG_RESULT) { s, bundle ->
            val markdown = bundle.getString(MARKDOWN_DIALOG_RESULT) ?: return@setFragmentResultListener
            binding.editTextContent.apply {
                if (selectedText?.isNotEmpty() == true) {
                    text?.replace(selectionStart, selectionEnd, "")
                }
                text?.insert(selectionStart, markdown)
            }
        }

        binding.fabChangeMode.setOnClickListener {
            updateEditMode(!model.inEditMode)
            if (model.inEditMode) requestFocusForFields(true) else view.hideKeyboard()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_top, menu)
        this.mainMenu = menu

        lifecycleScope.launch {
            model.data.first().note?.let { setupMenuItems(it, it.reminders.isNotEmpty()) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        data.note?.let { note ->
            when (item.itemId) {
                R.id.action_convert_note -> {
                    if (note.isList) model.toTextNote() else model.toList()
                }

                R.id.action_archive_note -> {
                    if (note.isArchived) activityModel.unarchiveNotes(note) else activityModel.archiveNotes(note)
                    sendMessage(getString(R.string.indicator_archive_note))
                    activity?.onBackPressed()
                }

                R.id.action_delete_note -> {
                    activityModel.deleteNotes(note)
                    sendMessage(getString(R.string.indicator_moved_note_to_bin))
                    activity?.onBackPressed()
                }

                R.id.action_restore_note -> {
                    activityModel.restoreNotes(note)
                    activity?.onBackPressed()
                }

                R.id.action_delete_permanently_note -> {
                    activityModel.deleteNotesPermanently(note)
                    sendMessage(getString(R.string.indicator_deleted_note_permanently))
                    activity?.onBackPressed()
                }

                R.id.action_view_tags -> {
                    findNavController().navigateSafely(
                        EditorFragmentDirections.actionEditorToTags().setNoteId(note.id)
                    )
                }

                R.id.action_view_reminders -> {
                    showRemindersDialog(note)
                }

                R.id.action_pin_note -> {
                    activityModel.pinNotes(note)
                }

                R.id.action_change_mode -> {
                    updateEditMode(!model.inEditMode)
                    if (model.inEditMode) requestFocusForFields(true) else view?.hideKeyboard()
                    setupMenuItems(note, note.reminders.isNotEmpty())
                }

                R.id.action_hide_note -> {
                    if (note.isHidden) activityModel.showNotes(note) else activityModel.hideNotes(note)
                }

                R.id.action_do_not_sync -> {
                    if (note.isLocalOnly) activityModel.makeNotesSyncable(note) else activityModel.makeNotesLocal(note)
                }

                R.id.action_change_color -> {
                    showColorChangeDialog()
                }

                R.id.action_export_note -> {
                    activityModel.notesToBackup = setOf(note)
                    exportNotesLauncher.launch(null)
                }

                R.id.action_share -> {
                    shareNote(requireContext(), note)
                }

                R.id.action_attach_file -> {
                    requestMediaLauncher.launch(null)
                }

                R.id.action_take_photo -> {
                    lifecycleScope.launch {
                        runCatching {
                            takePhotoLauncher.launch(activityModel.createImageFile())
                        }.getOrElse { Log.e(TAG, "Cannot launch camera app", it) }
                    }
                }

                R.id.action_record_audio -> {
                    clearFragmentResult(RECORD_CODE)
                    RecordAudioDialog().show(parentFragmentManager, null)
                }

                R.id.action_enable_disable_markdown -> {
                    if (note.isMarkdownEnabled) {
                        activityModel.disableMarkdown(note)
                    } else {
                        activityModel.enableMarkdown(note)
                    }
                }

                R.id.action_screen_always_on -> {
                    if (note.screenAlwaysOn) {
                        activityModel.disableScreenAlwaysOn(note)
                    } else {
                        activityModel.enableScreenAlwaysOn(note)
                    }
                    setupScreenAlwaysOn(!note.screenAlwaysOn)
                }

                R.id.action_uncheck_all_tasks -> {
                    uncheckAllTasks()
                    true
                }

                R.id.action_remove_all_checked_tasks -> {
                    removeAllCheckedTasks()
                    true
                }

                else -> false
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        model.selectedRange = with(binding.editTextContent) { selectionStart to selectionEnd }
        super.onPause()
    }

    override fun onDestroyView() {
        // Dismiss the snackbar which is shown for deleted notes
        snackbar?.dismiss()
        itemTouchHelper.attachToRecyclerView(null)
        attachmentsAdapter.listener = null
        tasksAdapter.listener = null
        setupScreenAlwaysOn(false)
        super.onDestroyView()
    }

    private fun jumpToNextTaskOrAdd(fromPosition: Int) {
        val next = tasksAdapter.tasks.getOrNull(fromPosition + 1)
        if (next == null || next.content.isNotEmpty()) {
            addTask(fromPosition + 1)
            return
        }
        (binding.recyclerTasks.findViewHolderForAdapterPosition(fromPosition + 1) as TaskViewHolder).requestFocus()
    }

    private fun setupTasksRecycler() {
        tasksAdapter = TasksAdapter(
            false,
            object : TaskRecyclerListener {
                override fun onDrag(viewHolder: TaskViewHolder) {
                    itemTouchHelper.startDrag(viewHolder)
                }

                override fun onTaskStatusChanged(position: Int, isDone: Boolean) {
                    updateTask(position = position, isDone = isDone)
                }

                override fun onTaskContentChanged(position: Int, content: String) {
                    updateTask(position = position, content = content)
                }

                override fun onNext(position: Int) {
                    jumpToNextTaskOrAdd(position)
                }
            },
            markwon = markwon,
        )

        binding.recyclerTasks.apply {
            isVisible = true
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = tasksAdapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun setupAttachmentsRecycler() = with(binding) {
        // Create the adapter
        val listener = object : AttachmentRecyclerListener {
            override fun onItemClick(position: Int, viewBinding: LayoutAttachmentBinding) {
                val attachment = attachmentsAdapter.getItemAtPosition(position)

                if (data.openMediaInternally) {
                    startActivity(
                        Intent(requireContext(), MediaActivity::class.java).apply {
                            putExtra(MediaActivity.ATTACHMENT, attachment)
                        }
                    )
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = attachment.uri(requireContext()) ?: return@apply
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        startActivity(this)
                    }
                }
            }

            override fun onLongClick(position: Int, viewBinding: LayoutAttachmentBinding): Boolean {
                if (data.note?.isDeleted == true) return false

                data.note?.id?.let { noteId ->
                    val attachment = attachmentsAdapter.getItemAtPosition(position)

                    BottomSheet.show(attachment.description, parentFragmentManager) {
                        action(R.string.attachments_edit_description, R.drawable.ic_pencil) {
                            EditAttachmentDialog.build(noteId, attachment.path).show(parentFragmentManager, null)
                        }
                        action(R.string.action_delete, R.drawable.ic_bin) {
                            model.deleteAttachment(attachment)
                        }
                        action(R.string.action_share, R.drawable.ic_share) {
                            shareAttachment(requireContext(), attachment)
                        }
                    }
                }
                return true
            }
        }

        attachmentsAdapter = AttachmentsAdapter(listener)
        // Configure the recycler view
        recyclerAttachments.apply {
            layoutManager = AttachmentsGridManager(requireContext())
            adapter = attachmentsAdapter
        }
    }

    private fun setMarkdownToolbarVisibility(note: Note? = data.note) = with(binding) {
        if (note == null) return@with

        containerBottomToolbar.isVisible = !isList && note.isMarkdownEnabled && model.inEditMode && contentHasFocus

        scrollView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val actionBarSize = requireContext().getDimensionAttribute(R.attr.actionBarSize) ?: 0
            bottomMargin = when {
                containerBottomToolbar.isVisible -> actionBarSize
                else -> 0
            }
        }
    }

    private fun setupEditTexts() = with(binding) {
        editTextTitle.apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

            setOnEditorActionListener { v, actionId, event ->
                when {
                    actionId == EditorInfo.IME_ACTION_NEXT && data.note?.isList == true -> {
                        jumpToNextTaskOrAdd(-1)
                        true
                    }

                    else -> false
                }
            }

            doOnTextChanged { text, start, before, count ->
                // Only listen for meaningful changes
                if (data.note == null) {
                    return@doOnTextChanged
                }

                model.setNoteTitle(text.toString().trim())
            }
        }

        editTextContent.apply {
            enableUndoRedo(this@EditorFragment)
            setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            doOnTextChanged { text, _, _, _ ->
                // Only listen for meaningful changes, we do not care about empty text
                if (data.note == null) {
                    return@doOnTextChanged
                }

                model.setNoteContent(text.toString().trim())
            }
            setOnFocusChangeListener { _, hasFocus ->
                contentHasFocus = hasFocus
                setMarkdownToolbarVisibility()
            }


            addTextChangedListener(object : TextWatcher {
                var changedText = ""
                private val listRegex = Regex("^((\\s*)([\\-+*] +)).*")
                private val checkRegex = Regex("^((\\s*)- *\\[([ x])] +).*")
                private val numListRegex = Regex("((\\s*)([1-9][0-9]*)[.] +).*")
                private val indentedLine = Regex("((\\s+)).*")

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    changedText = s?.substring(start, start + count).toString()
                }

                override fun afterTextChanged(s: Editable?) {
                    if (changedText.endsWith('\n')) {
                        val txt = text ?: return
                        val prevLine = txt.lines().getOrNull(currentLineIndex - 1) ?: return
                        when {
                            prevLine.matches(checkRegex) -> nextListLine(checkRegex, prevLine, txt, "- [ ] ")
                            prevLine.matches(listRegex) -> nextListLine(listRegex, prevLine, txt)
                            prevLine.matches(numListRegex) -> {
                                val nextNum = numListRegex.find(prevLine)?.groupValues?.get(3)?.toInt()?.inc() ?: 1
                                nextListLine(numListRegex, prevLine, txt, "$nextNum. ")
                            }

                            prevLine.matches(indentedLine) -> nextListLine(indentedLine, prevLine, txt)
                        }
                    }
                }

                private fun nextListLine(regex: Regex, line: String, text: Editable, suffix: String? = null) {
                    val groups = regex.find(line)?.groupValues
                    val matchedLine = groups?.getOrNull(1) ?: ""
                    if (matchedLine == line) {
                        text.delete(currentLineStartPos - line.length - 1, currentLineStartPos - 1)
                    } else {
                        val indent = groups?.getOrNull(2) ?: ""
                        text.insert(currentLineStartPos, "$indent${suffix ?: groups?.getOrNull(3) ?: ""}")
                    }
                }
            })

            setOnCanUndoRedoListener { canUndo, canRedo ->
                binding.bottomToolbar.menu?.run {
                    findItem(R.id.action_undo).isEnabled = canUndo
                    findItem(R.id.action_redo).isEnabled = canRedo
                }
            }
        }

        // Used to clear focus and hide the keyboard when touching outside of the edit texts
        linearLayout.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) root.hideKeyboard()
        }
    }

    private fun setupMenuItems(note: Note, hasReminders: Boolean) = mainMenu?.run {
        findItem(R.id.action_restore_note)?.isVisible = note.isDeleted
        findItem(R.id.action_delete_permanently_note)?.isVisible = note.isDeleted
        findItem(R.id.action_delete_note)?.isVisible = !note.isDeleted
        findItem(R.id.action_view_tags)?.isVisible = !note.isDeleted
        findItem(R.id.action_change_color)?.isVisible = !note.isDeleted
        findItem(R.id.action_attach_file)?.isVisible = !note.isDeleted
        findItem(R.id.action_record_audio)?.isVisible = !note.isDeleted
        findItem(R.id.action_take_photo)?.isVisible = !note.isDeleted
        findItem(R.id.action_convert_note)?.apply {
            title =
                if (note.isList) getString(R.string.action_convert_to_note) else getString(R.string.action_convert_to_list)
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_change_mode)?.apply {
            // if view/edit mode FAB isn't displayed (user pref) show it in the top menu
            if (!data.showFabChangeMode) {
                setIcon(if (model.inEditMode) R.drawable.ic_show else R.drawable.ic_pencil)

                isVisible = !note.isDeleted && !hasNoteEmptyContent(note)
            }
        }

        findItem(R.id.action_pin_note)?.apply {
            setIcon(if (note.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
            setTitle(if (note.isPinned) R.string.action_unpin else R.string.action_pin)
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_view_reminders)?.apply {
            setIcon(if (hasReminders) R.drawable.ic_bell_filled else R.drawable.ic_bell)
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_archive_note)?.apply {
            title = if (note.isArchived) getString(R.string.action_unarchive) else getString(R.string.action_archive)
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_enable_disable_markdown)?.apply {
            title =
                if (note.isMarkdownEnabled) getString(R.string.action_disable_markdown) else getString(R.string.action_enable_markdown)
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_hide_note)?.apply {
            isChecked = note.isHidden
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_do_not_sync)?.apply {
            isChecked = note.isLocalOnly
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_screen_always_on)?.apply {
            isChecked = note.screenAlwaysOn
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_uncheck_all_tasks)?.apply {
            isVisible = note.isList && !note.isDeleted
        }

        findItem(R.id.action_remove_all_checked_tasks)?.apply {
            isVisible = note.isList && !note.isDeleted
        }
    }

    private fun observeData() = with(binding) {
        model.data.collect(viewLifecycleOwner) { data ->
            if (data.note == null && data.isInitialized) {
                return@collect run { findNavController().navigateUp() }
            }

            if (!data.isInitialized || data.note == null) return@collect

            this@EditorFragment.data = data

            val isConverted = data.note.isList != isList
            val isMarkdownEnabled = data.note.isMarkdownEnabled
            val (dateFormat, timeFormat) = data.dateTimeFormats
            val screenAlwaysOn = data.note.screenAlwaysOn

            isList = data.note.isList
            isNoteDeleted = data.note.isDeleted

            if (isMarkdownEnabled) {
                enableMarkdownTextWatcher()
            } else {
                disableMarkdownTextWatcher()
            }

            setupScreenAlwaysOn(screenAlwaysOn)

            // Update Title and Content only the first the since they are EditTexts
            if (isFirstLoad) {

                // apply font size preference
                if (data.editorFontSize != -1) { // is customised
                    val fontSizeFloat = data.editorFontSize.toFloat()

                    textViewTitlePreview.textSize = fontSizeFloat
                    textViewContentPreview.textSize = fontSizeFloat

                    editTextTitle.textSize = fontSizeFloat
                    editTextContent.textSize = fontSizeFloat

                    if (isList) {
                        tasksAdapter.setFontSize(fontSizeFloat)
                    }
                }

                editTextTitle.withoutTextWatchers {
                    setText(data.note.title)
                }

                when {
                    isList -> tasksAdapter.submitList(data.note.taskList)
                    else -> {
                        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                            editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                                setText(data.note.content)
                            }
                            val (selStart, selEnd) = model.selectedRange
                            if (selStart >= 0 && selEnd <= editTextContent.length()) {
                                editTextContent.setSelection(selStart, selEnd)
                            }
                        }
                    }
                }

                nextTaskId = data.note.taskList.map { it.id }.maxOrNull()?.plus(1) ?: 0L
            }

            // We only want to update the task list when the user converts the note from text to list
            if (isConverted) {

                if (data.editorFontSize != -1) {
                    tasksAdapter.setFontSize(data.editorFontSize.toFloat())
                }

                tasksAdapter.tasks.clear()
                tasksAdapter.notifyDataSetChanged()
                tasksAdapter.submitList(data.note.taskList)
                editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                    setText(data.note.content)
                }
            }
            recyclerTasks.isVisible = isList

            updateEditMode(note = data.note)

            // Must be called after updateEditMode since that method changes the visibility of the inputs
            if (isFirstLoad) requestFocusForFields()

            // Also set text of preview textviews
            textViewTitlePreview.text = data.note.title.ifEmpty { getString(R.string.indicator_untitled) }

            if (isMarkdownEnabled) {
                // Seems to be crashing often without wrapping it in a post { } call
                textViewContentPreview.post {
                    markwon.applyTo(textViewContentPreview, data.note.content) {
                        tableReplacement = { Code(getString(R.string.message_cannot_preview_table)) }
                        maximumTableColumns = 15
                    }
                }
            } else {
                textViewContentPreview.text = data.note.content
            }

            setupMenuItems(data.note, data.note.reminders.isNotEmpty())

            // Update notebook indicator
            notebookView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                requireContext().getDrawableCompat(R.drawable.ic_notebook),
                null,
                requireContext().getDrawableCompat(if (data.notebook == null) R.drawable.ic_add else R.drawable.ic_swap),
                null
            )
            notebookView.text = data.notebook?.name ?: getString(R.string.notebooks_unassigned)

            // Update fragment background colour
            data.note.color.resId(requireContext())?.let { resId ->
                backgroundColor = resId
                root.setBackgroundColor(resId)
                containerBottomToolbar.setBackgroundColor(resId)
                toolbar.setBackgroundColor(resId)
            }

            // Update date
            val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
            val creationDate = LocalDateTime.ofEpochSecond(data.note.creationDate, 0, offset)
            val modifiedDate = LocalDateTime.ofEpochSecond(data.note.modifiedDate, 0, offset)

            formatter =
                DateTimeFormatter.ofPattern("${getString(dateFormat.patternResource)}, ${getString(timeFormat.patternResource)}")

            textViewDate.isVisible = data.showDates
            if (formatter != null && data.showDates) {
                textViewDate.text =
                    getString(
                        R.string.indicator_note_date,
                        creationDate.format(formatter),
                        modifiedDate.format(formatter)
                    )
            }

            // We want to start the transition only when everything is loaded
            binding.root.doOnPreDraw {
                startPostponedEnterTransition()
            }

            if (isNoteDeleted) {
                snackbar = Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
                    .setText(getString(R.string.indicator_deleted_note_cannot_be_edited))
                    .setAction(getString(R.string.action_restore)) { view ->
                        activityModel.restoreNotes(data.note)
                        activity?.onBackPressed()
                    }
                snackbar?.show()
                snackbar?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onShown(transientBottomBar: Snackbar?) {
                        super.onShown(transientBottomBar)
                        scrollView.apply {
                            setPadding(paddingLeft, paddingTop, paddingRight, snackbar?.view?.height ?: paddingBottom)
                        }
                    }
                })
            }

            // Update attachments
            attachmentsAdapter.submitList(data.note.attachments)

            // Update tags
            containerTags.removeAllViews()
            data.note.tags.forEach { tag ->
                containerTags.addView(
                    TextView(ContextThemeWrapper(requireContext(), R.style.TagChip)).apply {
                        text = "# ${tag.name}"
                    }
                )
            }

            isFirstLoad = false
        }
    }

    private fun setupListeners() = with(binding) {
        bottomToolbar.setOnMenuItemClickListener {

            val span = when (it.itemId) {
                R.id.action_insert_bold -> MarkdownSpan.BOLD
                R.id.action_insert_italics -> MarkdownSpan.ITALICS
                R.id.action_insert_strikethrough -> MarkdownSpan.STRIKETHROUGH
                R.id.action_insert_code -> MarkdownSpan.CODE
                R.id.action_insert_quote -> MarkdownSpan.QUOTE
                R.id.action_insert_heading -> MarkdownSpan.HEADING
                R.id.action_insert_highlight -> MarkdownSpan.HIGHLIGHT
                R.id.action_insert_link -> {
                    clearFragmentResult(MARKDOWN_DIALOG_RESULT)
                    InsertHyperlinkDialog
                        .build(editTextContent.selectedText ?: "")
                        .show(parentFragmentManager, null)
                    null
                }

                R.id.action_insert_image -> {
                    clearFragmentResult(MARKDOWN_DIALOG_RESULT)
                    InsertImageDialog
                        .build(editTextContent.selectedText ?: "")
                        .show(parentFragmentManager, null)
                    null
                }

                R.id.action_insert_table -> {
                    clearFragmentResult(MARKDOWN_DIALOG_RESULT)
                    InsertTableDialog().show(parentFragmentManager, null)
                    null
                }

                R.id.action_toggle_check_line -> {
                    editTextContent.toggleCheckmarkCurrentLine()
                    null
                }

                R.id.action_scroll_to_top -> {
                    scrollView.smoothScrollTo(0, 0)
                    editTextContent.setSelection(0)
                    null
                }

                R.id.action_scroll_to_bottom -> {
                    scrollView.smoothScrollTo(
                        0,
                        editTextContent.bottom + editTextContent.paddingBottom + editTextContent.marginBottom
                    )
                    editTextContent.setSelection(editTextContent.length())
                    null
                }

                R.id.action_undo -> {
                    editTextContent.undo()
                    null
                }

                R.id.action_redo -> {
                    editTextContent.redo()
                    null
                }

                else -> return@setOnMenuItemClickListener false
            }
            editTextContent.insertMarkdown(span ?: return@setOnMenuItemClickListener false)
            true
        }

        notebookView.setOnClickListener {
            data.note?.let { showMoveToNotebookDialog(it) }
        }

        actionAddTask.setOnClickListener {
            var addTaskIndex = tasksAdapter.tasks.size
            if (model.moveCheckedItems)
                addTaskIndex = tasksAdapter.tasks.indexOfLast { !it.isDone } + 1

            addTask(addTaskIndex)
        }
    }

    private fun setupMarkdown() {
        markwonTextWatcher = MarkwonEditorTextWatcher.withPreRender(
            markwonEditor, Executors.newCachedThreadPool(),
            binding.editTextContent
        )
    }

    private fun enableMarkdownTextWatcher() = with(binding) {
        if (markwonTextWatcher != null && !editTextContent.isMarkdownEnabled) {
            // TextWatcher is created and currently not attached to the EditText, we attach it
            editTextContent.addTextChangedListener(markwonTextWatcher)

            // Re-set text to notify the listener
            editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                setText(text)
            }

            editTextContent.isMarkdownEnabled = true
            setMarkdownToolbarVisibility()
        }
    }

    private fun disableMarkdownTextWatcher() = with(binding) {
        if (markwonTextWatcher != null && editTextContent.isMarkdownEnabled) {
            // TextWatcher is created and currently attached to the EditText, we detach it
            editTextContent.removeTextChangedListener(markwonTextWatcher)
            val text = editTextContent.text.toString()

            editTextContent.text?.clearSpans()
            editTextContent.withoutTextWatchers {
                setText(text)
            }

            editTextContent.isMarkdownEnabled = false
            setMarkdownToolbarVisibility()
        }
    }

    private fun setupScreenAlwaysOn(enable: Boolean) {
        if (enable) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun setupToolbar(): Unit = with(binding) {
        super.setupToolbar()
        val onBackPressedHandler = {
            if (findNavController().navigateUp()) {
                // This is needed because "Notes" label briefly appears
                // during the shared element transition when returning.
                // Todo: Needs a better fix
                toolbar.setTitleTextColor(Color.TRANSPARENT)

                // This is needed because the view jumps around
                // during the shared element transition when returning.
                // Todo: Needs a better fix
                notebookView.isVisible = false
            }
        }

        toolbar.setNavigationOnClickListener { onBackPressedHandler() }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            if (!onBackPressHandled) {
                onBackPressedHandler()
                onBackPressHandled = true
            }
        }
    }

    private fun addTask(position: Int = tasksAdapter.tasks.size) {
        tasksAdapter.tasks.add(position, NoteTask(nextTaskId, "", false))
        tasksAdapter.notifyItemInserted(tasksAdapter.tasks.size - 1)

        if (position < tasksAdapter.tasks.size - 1) {
            tasksAdapter.notifyItemRangeChanged(position, tasksAdapter.tasks.size - position + 1)
        }

        binding.recyclerTasks.doOnNextLayout {
            (binding.recyclerTasks.findViewHolderForAdapterPosition(position) as TaskViewHolder).requestFocus()
        }

        nextTaskId += 1
        model.updateTaskList(tasksAdapter.tasks)
    }

    private fun updateTask(position: Int, content: String? = null, isDone: Boolean? = null) {
        val tasks = tasksAdapter.tasks
        val oldTask = tasks[position]
        val newTask = tasks[position].copy(
            content = content ?: oldTask.content,
            isDone = isDone ?: oldTask.isDone
        )
        tasks[position] = newTask

        if (oldTask.isDone != newTask.isDone && model.moveCheckedItems) {
            if (newTask.isDone) {
                // Move to very end
                tasks.removeAt(position)
                tasks.add(newTask)

                tasksAdapter.notifyItemMoved(position, tasks.indexOf(newTask))
                tasksAdapter.notifyItemRangeChanged(position, tasks.size - position)
            } else {
                // Move to after last open task or to very beginning if all tasks are done
                val newPosition = tasks.indexOfLast { it.id != newTask.id && !it.isDone } + 1

                // Only move upwards; don't move further down
                if (newPosition < position) {
                    tasks.removeAt(position)
                    tasks.add(newPosition, newTask)

                    tasksAdapter.notifyItemMoved(position, newPosition)
                    tasksAdapter.notifyItemRangeChanged(newPosition, position - newPosition + 1)
                }
            }
        }

        model.updateTaskList(tasksAdapter.tasks)
    }

    private fun showColorChangeDialog() {
        val selected = NoteColor.values().indexOf(data.note?.color).coerceAtLeast(0)
        val dialog = BaseDialog.build(requireContext()) {
            setTitle(getString(R.string.action_change_color))
            setSingleChoiceItems(
                NoteColor.values().map { it.localizedName }.toTypedArray(),
                selected
            ) { dialog, which ->
                model.setColor(NoteColor.values()[which])
            }
            setPositiveButton(getString(R.string.action_done)) { dialog, which -> }
        }

        dialog.show()
    }

    private fun showRemindersDialog(note: Note) {
        BottomSheet.show(getString(R.string.reminders), parentFragmentManager) {
            data.note?.reminders?.forEach { reminder ->
                val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
                val reminderDate = LocalDateTime.ofEpochSecond(reminder.date, 0, offset)

                action(reminder.name + " (${reminderDate.format(formatter)})", R.drawable.ic_bell) {
                    if (checkSchedulePermission()) EditReminderDialog.build(note.id, reminder)
                        .show(parentFragmentManager, null)
                }
            }
            action(R.string.action_new_reminder, R.drawable.ic_add) {
                if (checkSchedulePermission()) EditReminderDialog.build(note.id, null).show(parentFragmentManager, null)
            }
        }
    }

    private fun checkSchedulePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context?.getSystemService(AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() != true) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context?.packageName, null)
                }
                context?.startActivity(intent)
                return false
            }
        }
        return true
    }

    /** Gives the focus to the note body if it is empty */
    private fun requestFocusForFields(forceFocus: Boolean = false) = with(binding) {
        if (editTextContent.text.isNullOrEmpty() || forceFocus) {
            editTextContent.requestFocusAndKeyboard()
        }
    }

    private fun updateEditMode(inEditMode: Boolean = model.inEditMode, note: Note? = data.note) = with(binding) {
        // If the note is empty the fragment should open in edit mode by default
        val noteHasEmptyContent = hasNoteEmptyContent(note)

        model.inEditMode = (inEditMode || noteHasEmptyContent) && !isNoteDeleted

        textViewTitlePreview.isVisible = !model.inEditMode
        editTextTitle.isVisible = model.inEditMode

        actionAddTask.isVisible = isList && model.inEditMode
        recyclerTasks.doOnPreDraw {
            for (pos in 0 until tasksAdapter.tasks.size) {
                (recyclerTasks.findViewHolderForAdapterPosition(pos) as? TaskViewHolder)?.isEnabled = model.inEditMode
            }
        }

        textViewContentPreview.isVisible = !model.inEditMode && !isList
        editTextContent.isVisible = model.inEditMode && !isList

        val shouldDisplayFAB = data.showFabChangeMode && !isNoteDeleted && !noteHasEmptyContent
        when {
            fabChangeMode.isVisible == shouldDisplayFAB -> { /* FAB is already like it should be, no reason to animate */
            }

            fabChangeMode.isVisible && !shouldDisplayFAB -> fabChangeMode.hide()
            else -> fabChangeMode.show()
        }

        fabChangeMode.setImageResource(if (model.inEditMode) R.drawable.ic_show else R.drawable.ic_pencil)
        setMarkdownToolbarVisibility(note)
    }

    private fun hasNoteEmptyContent(note: Note? = data.note): Boolean {
        return note?.content?.isBlank() == true || (note?.isList == true && note.taskList.isEmpty())
    }

    private fun uncheckAllTasks() {
        val updatedTasks = tasksAdapter.tasks.map { task ->
            task.copy(isDone = false)
        }
        tasksAdapter.submitList(updatedTasks)

        model.updateTaskList(updatedTasks)
    }

    private fun removeAllCheckedTasks() {
        val updatedTasks = tasksAdapter.tasks.filter { task ->
            !task.isDone
        }
        tasksAdapter.submitList(updatedTasks)

        model.updateTaskList(updatedTasks)
    }

    private val NoteColor.localizedName
        get() = getString(
            when (this) {
                NoteColor.Default -> R.string.default_string
                NoteColor.Green -> R.string.preferences_color_scheme_green
                NoteColor.Pink -> R.string.preferences_color_scheme_pink
                NoteColor.Blue -> R.string.preferences_color_scheme_blue
                NoteColor.Red -> R.string.preferences_color_scheme_red
                NoteColor.Orange -> R.string.preferences_color_scheme_orange
                NoteColor.Yellow -> R.string.preferences_color_scheme_yellow
            }
        )

    companion object {
        const val MARKDOWN_DIALOG_RESULT = "MARKDOWN_DIALOG_RESULT"
    }
}
