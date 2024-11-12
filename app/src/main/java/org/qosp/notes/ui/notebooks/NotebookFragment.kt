package org.qosp.notes.ui.notebooks

import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.ui.main.MainFragment

class NotebookFragment : MainFragment() {
    override val currentDestinationId: Int = R.id.fragment_notebook
    private val args: NotebookFragmentArgs by navArgs()

    override val notebookId: Long?
        get() = args.notebookId.takeIf { it >= 0L || it == R.id.nav_default_notebook.toLong() }
    override val toolbarTitle: String
        get() = args.notebookName

    override fun actionToEditor(
        transitionName: String,
        noteId: Long,
        attachments: List<Attachment>,
        isList: Boolean
    ): NavDirections =
        NotebookFragmentDirections.actionNotebookToEditor(transitionName)
            .setNoteId(noteId)
            .setNewNoteAttachments(attachments.toTypedArray())
            .setNewNoteIsList(isList)
            .setNewNoteNotebookId(notebookId.takeUnless { it == R.id.nav_default_notebook.toLong() } ?: 0L)

    override fun actionToSearch(searchQuery: String) =
        NotebookFragmentDirections.actionNotebookToSearch().setSearchQuery(searchQuery)

    override fun onResume() {
        super.onResume()

        // Check if notebook exists in database. If it doesn't then go back
        lifecycleScope.launch {
            if (!model.notebookExists(args.notebookId) && args.notebookId != R.id.nav_default_notebook.toLong()) {
                findNavController().navigateUp()
            }
        }
    }
}
