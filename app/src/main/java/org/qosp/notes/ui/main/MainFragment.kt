package org.qosp.notes.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.clearFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.databinding.FragmentMainBinding
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.preferences.SortTagsMethod
import org.qosp.notes.ui.attachments.fromUri
import org.qosp.notes.ui.common.AbstractNotesFragment
import org.qosp.notes.ui.recorder.RECORDED_ATTACHMENT
import org.qosp.notes.ui.recorder.RECORD_CODE
import org.qosp.notes.ui.recorder.RecordAudioDialog
import org.qosp.notes.ui.utils.ChooseFilesContract
import org.qosp.notes.ui.utils.TakePictureContract
import org.qosp.notes.ui.utils.launch
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
open class MainFragment : AbstractNotesFragment(R.layout.fragment_main) {
    private val binding by viewBinding(FragmentMainBinding::bind)

    override val currentDestinationId: Int = R.id.fragment_main
    override val model: MainViewModel by viewModels()

    open val notebookId: Long? = null

    private val chooseFileLauncher = registerForActivityResult(ChooseFilesContract) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult

        val attachments = uris.map {
            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Attachment.fromUri(requireContext(), it)
        }

        goToEditor(
            attachments = attachments,
            sharedElement = binding.fabCreateNote
        )
    }

    private val takePhotoLauncher = registerForActivityResult(TakePictureContract) { saved ->
        if (!saved) return@registerForActivityResult

        activityModel.tempPhotoUri?.toString()?.let { path ->
            goToEditor(
                attachments = listOf(Attachment(Attachment.Type.IMAGE, path = path)),
                sharedElement = binding.fabCreateNote
            )
        }
        activityModel.tempPhotoUri = null
    }

    override val recyclerView: RecyclerView
        get() = binding.recyclerMain
    override val swipeRefreshLayout: SwipeRefreshLayout
        get() = binding.layoutSwipeRefresh
    override val snackbarLayout: View
        get() = binding.fabCreateNote
    override val snackbarAnchor: View
        get() = binding.fabCreateNote
    override val emptyIndicator: View
        get() = binding.indicatorNotesEmpty
    override val appBarLayout: AppBarLayout
        get() = binding.layoutAppBar.appBar
    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_notes)
    override val secondaryToolbar: Toolbar
        get() = binding.layoutAppBar.toolbarSelection
    override val secondaryToolbarMenuRes: Int = R.menu.main_selected_notes

    @Inject
    lateinit var syncManager: SyncManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.initialize(notebookId)

        setupFab()
        setupBottomAppBar()
        setFragmentResultListener(RECORD_CODE) { s, bundle ->
            val attachment = bundle.getParcelable<Attachment>(RECORDED_ATTACHMENT) ?: return@setFragmentResultListener
            goToEditor(
                attachments = listOf(attachment),
                sharedElement = binding.fabCreateNote
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.main_top, menu)
        mainMenu = menu
        setHiddenNotesItemActionText()
        setLayoutChangeActionIcon()
        selectSortMethodItem()
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> findNavController().navigateSafely(actionToSearch())
            R.id.action_layout_mode -> toggleLayoutMode()
            R.id.action_sort_name_asc -> activityModel.setSortMethod(SortMethod.TITLE_ASC)
            R.id.action_sort_name_desc -> activityModel.setSortMethod(SortMethod.TITLE_DESC)
            R.id.action_sort_created_asc -> activityModel.setSortMethod(SortMethod.CREATION_ASC)
            R.id.action_sort_created_desc -> activityModel.setSortMethod(SortMethod.CREATION_DESC)
            R.id.action_sort_modified_asc -> activityModel.setSortMethod(SortMethod.MODIFIED_ASC)
            R.id.action_sort_modified_desc -> activityModel.setSortMethod(SortMethod.MODIFIED_DESC)
            R.id.action_show_hidden_notes -> toggleHiddenNotes()
            R.id.action_select_all -> selectAllNotes()
        }
        return super.onOptionsItemSelected(item)
    }

    open fun actionToEditor(
        transitionName: String,
        noteId: Long,
        attachments: List<Attachment> = listOf(),
        isList: Boolean = false,
    ): NavDirections =
        MainFragmentDirections.actionMainToEditor(transitionName)
            .setNoteId(noteId)
            .setNewNoteAttachments(attachments.toTypedArray())
            .setNewNoteIsList(isList)
            .setNewNoteNotebookId(notebookId ?: 0L)

    open fun actionToSearch(searchQuery: String = ""): NavDirections =
        MainFragmentDirections.actionMainToSearch().setSearchQuery(searchQuery)

    override fun onNoteClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding) {
        goToEditor(noteId, sharedElement = viewBinding.root, fromPosition = position)
    }

    override fun onNoteLongClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding): Boolean {
        showMenuForNote(position)
        return true
    }

    override fun onSelectionChanged(selectedIds: List<Long>) {
        super.onSelectionChanged(selectedIds)

        val inSelectionMode = selectedIds.isNotEmpty()
        binding.bottomAppBar.isVisible = !inSelectionMode
        binding.fabCreateNote.isVisible = !inSelectionMode
    }

    override fun onLayoutModeChanged() {
        super.onLayoutModeChanged()
        setLayoutChangeActionIcon()
    }

    override fun onSortMethodChanged() {
        super.onSortMethodChanged()
        selectSortMethodItem()
    }

    private fun goToEditor(
        noteId: Long? = null,
        attachments: List<Attachment> = listOf(),
        isList: Boolean = false,
        fromPosition: Int? = null,
        sharedElement: View,
    ) {
        applyNavToEditorAnimation(fromPosition)
        when (noteId) {
            null -> {
                findNavController().navigateSafely(
                    actionToEditor(
                        transitionName = "editor_create",
                        noteId = 0L,
                        attachments = attachments,
                        isList = isList
                    ),
                    FragmentNavigatorExtras(sharedElement to "editor_create")
                )
            }
            else -> {
                findNavController().navigateSafely(
                    actionToEditor("editor_$noteId", noteId),
                    FragmentNavigatorExtras(sharedElement to "editor_$noteId")
                )
            }
        }
    }

    private fun setupFab() {
        ViewCompat.setTransitionName(binding.fabCreateNote, "editor_create")
        binding.fabCreateNote.setOnClickListener { goToEditor(sharedElement = binding.fabCreateNote) }
    }

    private fun setLayoutChangeActionIcon() {
        mainMenu?.findItem(R.id.action_layout_mode)?.apply {
            isVisible = true
            setIcon(if (data.layoutMode == LayoutMode.GRID) R.drawable.ic_list else R.drawable.ic_grid)
        }
    }

    private fun selectSortMethodItem() {
        mainMenu?.findItem(
            when (data.sortMethod) {
                SortMethod.TITLE_ASC -> R.id.action_sort_name_asc
                SortMethod.TITLE_DESC -> R.id.action_sort_name_desc
                SortMethod.CREATION_ASC -> R.id.action_sort_created_asc
                SortMethod.CREATION_DESC -> R.id.action_sort_created_desc
                SortMethod.MODIFIED_ASC -> R.id.action_sort_modified_asc
                SortMethod.MODIFIED_DESC -> R.id.action_sort_modified_desc
            }
        )?.isChecked = true
    }

    private fun setupBottomAppBar() {
        binding.bottomAppBar.setOnMenuItemClickListener { it ->
            when (it.itemId) {
                R.id.action_create_list -> {
                    goToEditor(
                        isList = true,
                        sharedElement = binding.fabCreateNote
                    )
                    true
                }
                R.id.action_record_audio -> {
                    clearFragmentResult(RECORD_CODE)
                    RecordAudioDialog().show(parentFragmentManager, null)
                    true
                }
                R.id.action_attach_file -> {
                    chooseFileLauncher.launch(null)
                    true
                }
                R.id.action_take_photo -> {
                    lifecycleScope.launch {
                        runCatching {
                            takePhotoLauncher.launch(activityModel.createImageFile())
                        }.getOrElse { Log.e(TAG, "Cannot launch camera app", it) }
                    }
                    true
                }
                else -> false
            }
        }
    }
}
