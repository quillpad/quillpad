package org.qosp.notes.ui.common

import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.clearFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialElevationScale
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.data.sync.core.ServerNotSupported
import org.qosp.notes.data.sync.core.Unauthorized
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.ui.common.recycler.NoteRecyclerAdapter
import org.qosp.notes.ui.common.recycler.NoteRecyclerListener
import org.qosp.notes.ui.common.recycler.onBackPressedHandler
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.shareNote
import org.qosp.notes.ui.utils.views.BottomSheet
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private typealias Data = AbstractNotesViewModel.Data

@AndroidEntryPoint
abstract class AbstractNotesFragment(@LayoutRes resId: Int) : BaseFragment(resId) {
    abstract val currentDestinationId: Int
    abstract val recyclerView: RecyclerView
    abstract val model: AbstractNotesViewModel
    abstract val emptyIndicator: View
    abstract val swipeRefreshLayout: SwipeRefreshLayout

    open val isSelectionEnabled = true
    open val appBarLayout: AppBarLayout? = null
    open val secondaryToolbar: Toolbar? = null
    open val secondaryToolbarMenuRes: Int = 0

    open val snackbarLayout: View? = null
    open val snackbarAnchor: View? = null

    protected lateinit var recyclerAdapter: NoteRecyclerAdapter
    protected val inSelectionMode get() = recyclerAdapter.selectedItemIds.isNotEmpty()
    protected var mainMenu: Menu? = null

    protected var data = Data()

    private var snackbar: Snackbar? = null
    private var showHiddenNotes: Boolean
        get() = activityModel.showHiddenNotes
        set(value) {
            activityModel.showHiddenNotes = value
            recyclerAdapter.showHiddenNotes = value
        }

    @Inject
    lateinit var markwon: Markwon

    // Bug:
    //
    // When clicking on a note but then quickly pressing the back button before the animation takes place
    // makes the toolbar unusable. This is because the editor fragment gets created and calls setupToolbar() which
    // makes the editor toolbar active. However clicking the back button before the animation means that the current
    // fragment is not destroyed yet and thus will not be recreated which means that this fragment's setupToolbar() function
    // will not be called.
    //
    // The fix:
    //
    // This fix adds an onDestinationChangedListener to the NavController, which sets the toolbar up
    // if it's navigating back to the same fragment. This listener gets removed when the view gets destroyed.
    // For this to work however, each fragment that wants to inherit from this fragment must override currentDestinationId.
    private var isListenerSet = false
    private val destinationChangedListener = object : NavController.OnDestinationChangedListener {
        override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
            if (destination.id == currentDestinationId) {
                setupToolbar()
                controller.removeOnDestinationChangedListener(this)
                isListenerSet = false
            }
        }
    }

    private fun BaseResult.showToastOnCriticalError() {
        val resId = when (this) {
            ServerNotSupported -> R.string.message_server_not_compatible
            Unauthorized -> R.string.message_invalid_credentials
            else -> null
        }

        resId?.let {
            Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSecondaryToolbar()

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) { onBackPressedHandler(recyclerAdapter) }

        setFragmentResultListener(FRAGMENT_MESSAGE) { _, bundle ->
            val message = bundle.getString(FRAGMENT_MESSAGE) ?: return@setFragmentResultListener
            onMessageAvailable(message)
            clearFragmentResult(FRAGMENT_MESSAGE)
        }

        // Setup recycler view
        val listener = object : NoteRecyclerListener {
            override fun onItemClick(position: Int, viewBinding: LayoutNoteBinding) {
                val noteId = recyclerAdapter.getItemAtPosition(position).id

                if (isSelectionEnabled && inSelectionMode) {
                    toggleNoteSelected(noteId)
                } else {
                    this@AbstractNotesFragment.onNoteClick(noteId, position, viewBinding)
                }
            }

            override fun onLongClick(position: Int, viewBinding: LayoutNoteBinding): Boolean {
                val noteId = recyclerAdapter.getItemAtPosition(position).id

                return if (isSelectionEnabled && inSelectionMode) {
                    false
                } else this@AbstractNotesFragment.onNoteLongClick(noteId, position, viewBinding)
            }
        }

        recyclerAdapter = NoteRecyclerAdapter(
            listener = listener,
            markwon = markwon,
        ).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            showHiddenNotes = this@AbstractNotesFragment.showHiddenNotes

            setOnListChangedListener {
                val shouldDisplayIndicator = it.isEmpty()

                if (emptyIndicator.isVisible != shouldDisplayIndicator) {
                    if (shouldDisplayIndicator) {
                        emptyIndicator.isVisible = true
                        emptyIndicator.alpha = 0F
                        emptyIndicator.animate()
                            .alpha(1F)
                            .setDuration(200L)
                            .start()
                    } else {
                        emptyIndicator.isVisible = false
                    }
                }

                recyclerView.doOnLayout {
                    startPostponedEnterTransition()
                }
            }

            if (isSelectionEnabled) {
                enableSelection(this@AbstractNotesFragment, ::onSelectionChanged)
            }
        }

        // Configure the recycler view
        recyclerView.apply {
            adapter = recyclerAdapter

            val padding = resources.getDimensionPixelSize(R.dimen.recycler_padding) / 2
            setPadding(
                recyclerView.paddingLeft + padding,
                recyclerView.paddingTop + padding,
                recyclerView.paddingRight + padding,
                recyclerView.paddingBottom + padding,
            )

            clipToPadding = false
            clipChildren = false

            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.set(padding, padding, padding, padding)
                }
            })
        }

        // Lift app bar during scrolling
        appBarLayout?.let {
            recyclerView.liftAppBarOnScroll(
                it,
                requireContext().resources.getDimension(R.dimen.app_bar_elevation)
            )
        }

        // Set up an observer to change the notes list whenever they update
        model.data.collect(viewLifecycleOwner, ::onDataChanged)

        // Sync on refresh
        swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope
                .launch {
                    activityModel
                        .syncAsync()
                        .await()
                        .showToastOnCriticalError()
                    swipeRefreshLayout.isRefreshing = false
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                swipeRefreshLayout.isRefreshing = false

                val discardedNotes = activityModel
                    .discardEmptyNotesAsync()
                    .await()

                if (discardedNotes) {
                    sendMessage(getString(R.string.indicator_empty_note_discarded))
                }

                if (model.isSyncingEnabled() && !recyclerAdapter.searchMode) {
                    activityModel
                        .syncAsync()
                        .await()
                        .showToastOnCriticalError()
                }
                swipeRefreshLayout.isRefreshing = false
            }
        }
        postponeEnterTransition(1500L, TimeUnit.MILLISECONDS)
    }

    override fun onDestroyView() {
        // Bug fix. See the the comments at the declaration of destinationChangedListener for more info.
        isListenerSet = false
        findNavController().removeOnDestinationChangedListener(destinationChangedListener)

        recyclerAdapter.listener = null

        mainMenu = null

        snackbar?.dismiss()
        snackbar?.anchorView = null

        snackbar = null
        super.onDestroyView()
    }

    @CallSuper
    open fun onDataChanged(data: Data) {
        this.data = data

        // Submit the list to the adapter
        onNotesChanged(data.notes)

        // Update recycler layout and note order
        recyclerView.layoutManager = when (data.layoutMode) {
            LayoutMode.LIST -> LinearLayoutManager(requireContext())
            LayoutMode.GRID -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        }
        onLayoutModeChanged()
        onSortMethodChanged()
    }

    open fun onNotesChanged(notes: List<Note>) {
        recyclerAdapter.submitList(notes)
    }

    open fun onLayoutModeChanged() {}

    open fun onSortMethodChanged() {}

    open fun onNoteClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding) {}

    open fun onNoteLongClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding) = false

    @CallSuper
    open fun onSelectionChanged(selectedIds: List<Long>) {
        setSelectionModeUI(selectedIds.size)
    }

    @CallSuper
    open fun onMessageAvailable(text: CharSequence) {
        snackbar = Snackbar.make(snackbarLayout ?: return, text, BaseTransientBottomBar.LENGTH_SHORT).apply {
            if (snackbarAnchor != null) anchorView = snackbarAnchor

            // setAnchorView() causes the snackbar to leak the root layout
            // this seems to be fixing it
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    snackbar?.anchorView = null
                    snackbar = null
                }
            })
        }
        snackbar?.show()
    }

    private fun setupSecondaryToolbar() {
        val secondaryToolbar = secondaryToolbar ?: return
        secondaryToolbar.inflateMenu(secondaryToolbarMenuRes)
        secondaryToolbar.setNavigationOnClickListener { onBackPressedHandler(recyclerAdapter) }
        secondaryToolbar.setOnMenuItemClickListener { item ->
            var clearSelectionAfterAction = true
            val selectedNotes = recyclerAdapter.getSelectedItems().toTypedArray()

            when (item.itemId) {
                R.id.action_pin_selected -> activityModel.pinNotes(*selectedNotes)
                R.id.action_compact_preview_selected -> activityModel.compactPreviewNotes(*selectedNotes)
                R.id.action_full_preview_selected -> activityModel.fullPreviewNotes(*selectedNotes)
                R.id.action_archive_selected -> {
                    activityModel.archiveNotes(*selectedNotes)
                    sendMessage(
                        if (selectedNotes.size > 1) getString(R.string.indicator_archived_notes) else getString(
                            R.string.indicator_archive_note
                        )
                    )
                }
                R.id.action_unarchive -> activityModel.unarchiveNotes(*selectedNotes)
                R.id.action_restore_selected -> activityModel.restoreNotes(*selectedNotes)
                R.id.action_delete_selected -> {
                    activityModel.deleteNotes(*selectedNotes)
                    lifecycleScope.launch {
                        if (data.noteDeletionTimeInDays == 0L) {
                            sendMessage(getString(R.string.indicator_deleted_notes_permanently))
                        } else {
                            sendMessage(getString(R.string.indicator_moved_notes_to_bin))
                        }
                    }
                }
                R.id.action_delete_permanently_selected -> {
                    activityModel.deleteNotesPermanently(*selectedNotes)
                    sendMessage(
                        if (selectedNotes.size > 1) getString(R.string.indicator_deleted_notes_permanently) else getString(
                            R.string.indicator_deleted_note_permanently
                        )
                    )
                }
                R.id.action_hide_selected -> activityModel.hideNotes(*selectedNotes)
                R.id.action_duplicate_selected -> activityModel.duplicateNotes(*selectedNotes)
                R.id.action_move_selected -> showMoveToNotebookDialog(*selectedNotes)
                R.id.action_export_selected -> {
                    activityModel.notesToBackup = selectedNotes.toSet()
                    exportNotesLauncher.launch(null)
                }
                R.id.action_select_all -> {
                    selectAllNotes()
                    clearSelectionAfterAction = false
                }
            }
            if (clearSelectionAfterAction) clearSelection()
            true
        }
    }

    private fun setSelectionModeUI(selectedNotesCount: Int) {
        if (selectedNotesCount > 0) {
            toolbar?.isVisible = false
            secondaryToolbar?.isVisible = true
            secondaryToolbar?.title =
                resources.getQuantityString(R.plurals.selected_notes, selectedNotesCount, selectedNotesCount)
        } else {
            secondaryToolbar?.isVisible = false
            toolbar?.isVisible = true
        }
    }

    fun toggleNoteSelected(id: Long) {
        recyclerAdapter.toggleSelectionForItem(id)
    }

    private fun clearSelection() {
        recyclerAdapter.clearSelection()
    }

    protected fun selectAllNotes() {
        recyclerAdapter.selectAll()
    }

    protected fun toggleLayoutMode() {
        when (data.layoutMode) {
            LayoutMode.LIST -> activityModel.setLayoutMode(LayoutMode.GRID)
            LayoutMode.GRID -> activityModel.setLayoutMode(LayoutMode.LIST)
        }
    }

    protected fun toggleHiddenNotes() {
        showHiddenNotes = !showHiddenNotes
        setHiddenNotesItemActionText()
    }

    fun setHiddenNotesItemActionText() {
        if (hasMenu) mainMenu?.findItem(R.id.action_show_hidden_notes)?.isChecked = showHiddenNotes
    }

    protected fun applyNavToEditorAnimation(position: Int?) {
        // Bug fix. See the the comments at the declaration of destinationChangedListener for more info.
        if (!isListenerSet) {
            findNavController().addOnDestinationChangedListener(destinationChangedListener)
            isListenerSet = true
        }

        // Shared element transition causes the item to overlap with the app bar
        // By scrolling to the item position, the transition will happen inside the RV's bounds
        if (position != null) recyclerView.scrollToPosition(position)

        exitTransition = MaterialElevationScale(false).apply {
            duration = 300L
        }
        reenterTransition = MaterialElevationScale(true).apply {
            duration = 300L
        }
    }

    fun showMenuForNote(position: Int, isSelectionEnabled: Boolean = true) {
        val note = recyclerAdapter.getItemAtPosition(position)
        val isNormal = !note.isDeleted && !note.isArchived

        BottomSheet.show(note.title, parentFragmentManager) {
            action(R.string.action_unpin, R.drawable.ic_unpin, condition = note.isPinned && isNormal) {
                activityModel.pinNotes(note)
            }
            action(R.string.action_pin, R.drawable.ic_pin, condition = !note.isPinned && isNormal) {
                activityModel.pinNotes(note)
            }
            action(R.string.action_restore, R.drawable.ic_restore, condition = note.isDeleted) {
                activityModel.restoreNotes(note)
            }
            action(R.string.action_delete_permanently, R.drawable.ic_bin, condition = note.isDeleted) {
                activityModel.deleteNotesPermanently(note)
                sendMessage(getString(R.string.indicator_deleted_note_permanently))
            }
            action(R.string.action_archive, R.drawable.ic_archive_action, condition = !note.isArchived && isNormal) {
                activityModel.archiveNotes(note)
                sendMessage(getString(R.string.indicator_archive_note))
            }
            action(R.string.action_unarchive, R.drawable.ic_unarchive, condition = note.isArchived) {
                activityModel.unarchiveNotes(note)
            }
            action(R.string.action_move_to, R.drawable.ic_notebook_swap, condition = isNormal) {
                showMoveToNotebookDialog(note)
            }
            action(R.string.action_delete, R.drawable.ic_bin, condition = !note.isDeleted) {
                activityModel.deleteNotes(note)
                lifecycleScope.launch {
                    if (data.noteDeletionTimeInDays == 0L) {
                        sendMessage(getString(R.string.indicator_deleted_note_permanently))
                    } else {
                        sendMessage(getString(R.string.indicator_moved_note_to_bin))
                    }
                }
            }
            action(R.string.action_show, R.drawable.ic_show, condition = note.isHidden) {
                activityModel.showNotes(note)
            }
            action(R.string.action_hide, R.drawable.ic_hidden, condition = !note.isHidden) {
                activityModel.hideNotes(note)
            }
            action(R.string.action_compact_preview, R.drawable.ic_preview, condition = !note.isCompactPreview) {
                activityModel.makeNotesCompactPreview(note)
            }
            action(R.string.action_full_preview, R.drawable.ic_preview, condition = note.isCompactPreview) {
                activityModel.makeNotesFullPreview(note)
            }
            action(R.string.action_disable_screen_always_on, R.drawable.ic_pin, condition = !note.isDeleted && note.screenAlwaysOn) {
                activityModel.disableScreenAlwaysOn(note)
            }
            action(R.string.action_enable_screen_always_on, R.drawable.ic_pin, condition = !note.isDeleted && !note.screenAlwaysOn) {
                activityModel.enableScreenAlwaysOn(note)
            }
            action(R.string.action_disable_markdown, R.drawable.ic_markdown, condition = !note.isDeleted && note.isMarkdownEnabled) {
                activityModel.disableMarkdown(note)
            }
            action(R.string.action_enable_markdown, R.drawable.ic_markdown, condition = !note.isDeleted && !note.isMarkdownEnabled) {
                activityModel.enableMarkdown(note)
            }
            action(R.string.action_duplicate, R.drawable.ic_duplicate, condition = isNormal) {
                activityModel.duplicateNotes(note)
            }
            action(R.string.action_export, R.drawable.ic_export_note) {
                activityModel.notesToBackup = setOf(note)
                exportNotesLauncher.launch(null)
            }
            action(R.string.action_share, R.drawable.ic_share) {
                shareNote(requireContext(), note)
            }
            action(R.string.action_select_more, R.drawable.ic_select_more, condition = isSelectionEnabled) {
                toggleNoteSelected(note.id)
            }
        }
    }
}
