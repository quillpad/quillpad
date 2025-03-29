package org.qosp.notes.ui.deleted

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentDeletedBinding
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.ui.common.AbstractNotesFragment
import org.qosp.notes.ui.common.AbstractNotesViewModel
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.viewBinding

class DeletedFragment : AbstractNotesFragment(R.layout.fragment_deleted) {
    private val binding by viewBinding(FragmentDeletedBinding::bind)

    override val currentDestinationId: Int = R.id.fragment_deleted
    override val model: DeletedViewModel by viewModel()

    override val recyclerView: RecyclerView
        get() = binding.recyclerDeleted
    override val swipeRefreshLayout: SwipeRefreshLayout
        get() = binding.layoutSwipeRefresh
    override val snackbarLayout: View
        get() = binding.layoutCoordinator
    override val emptyIndicator: View
        get() = binding.indicatorDeletedEmpty
    override val appBarLayout: AppBarLayout
        get() = binding.layoutAppBar.appBar
    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_deleted)
    override val secondaryToolbar: Toolbar
        get() = binding.layoutAppBar.toolbarSelection
    override val secondaryToolbarMenuRes = R.menu.deleted_selected_notes

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.deleted, menu)
        mainMenu = menu
        setHiddenNotesItemActionText()
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_empty_bin -> showEmptyBinDialog()
            R.id.action_show_hidden_notes -> toggleHiddenNotes()
            R.id.action_select_all -> selectAllNotes()
            R.id.action_search -> findNavController().navigateSafely(DeletedFragmentDirections.actionDeletedToSearch())
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDataChanged(data: AbstractNotesViewModel.Data) {
        super.onDataChanged(data)

        val days = data.noteDeletionTimeInDays

        binding.indicatorDeletedEmptyText.text = when (days) {
            -1L -> getString(R.string.indicator_bin_forever)
            0L -> getString(R.string.indicator_bin_disabled)
            else -> getString(R.string.indicator_deleted_empty, days)
        }
    }

    override fun onNoteClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding) {
        applyNavToEditorAnimation(position)
        findNavController().navigateSafely(
            DeletedFragmentDirections.actionDeletedToEditor("editor_$noteId").setNoteId(noteId),
            FragmentNavigatorExtras(viewBinding.root to "editor_$noteId")
        )
    }

    override fun onNoteLongClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding): Boolean {
        showMenuForNote(position)
        return true
    }

    private fun showEmptyBinDialog() {
        BaseDialog.build(requireContext()) {
            setTitle(R.string.empty_bin_warning_title)
            setMessage(R.string.empty_bin_warning_text)
            setPositiveButton(R.string.yes) { di, _ ->
                model.permanentlyDeleteNotesInBin()
                di.dismiss()
            }
            setNegativeButton(R.string.no) { di, _ -> di.dismiss() }
        }
            .show()
    }
}
