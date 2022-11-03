package org.qosp.notes.ui.common

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.utils.navigateSafely

fun BaseFragment.showMoveToNotebookDialog(vararg notes: Note) {
    lifecycleScope.launch {
        val (_, notebooks) = activityModel.notebooks.value
        var selected = 0
        val notebooksMap: MutableMap<Long?, String> =
            mutableMapOf(null to requireContext().getString(R.string.notebooks_unassigned))

        // If notes are in the same notebook (or if it's just a single note)
        // we will display the selected notebook
        val notesInSameNotebook = notes.all { it.notebookId == notes[0].notebookId }
        notebooks.forEachIndexed { index, notebook ->
            notebooksMap[notebook.id] = notebook.name
            if (notesInSameNotebook && notes[0].notebookId == notebook.id) selected = index + 1
        }

        val dialog = BaseDialog.build(requireContext()) {
            if (!notesInSameNotebook) setItems(notebooksMap.values.toTypedArray()) { dialog, which ->
                activityModel.moveNotes(notebooksMap.keys.toTypedArray()[which], *notes)
                dialog.dismiss()
            }
            else setSingleChoiceItems(notebooksMap.values.toTypedArray(), selected) { dialog, which ->
                activityModel.moveNotes(notebooksMap.keys.elementAt(which), *notes)
                dialog.dismiss()
            }
            setTitle(requireContext().getString(R.string.action_move_to))
            setNeutralButton(requireContext().getString(R.string.nav_your_notebooks)) { dialog, which ->
                findNavController().navigateSafely(R.id.fragment_manage_notebooks)
            }
            setPositiveButton(requireContext().getString(R.string.action_done)) { dialog, which -> }
        }

        dialog.show()
    }
}
