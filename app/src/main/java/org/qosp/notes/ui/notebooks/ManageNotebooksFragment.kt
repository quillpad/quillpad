package org.qosp.notes.ui.notebooks

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.databinding.FragmentManageNotebooksBinding
import org.qosp.notes.preferences.SortNavdrawerNotebooksMethod
import org.qosp.notes.preferences.SortNavdrawerNotebooksMethod.*
import org.qosp.notes.preferences.SortTagsMethod
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.common.recycler.onBackPressedHandler
import org.qosp.notes.ui.notebooks.dialog.EditNotebookDialog
import org.qosp.notes.ui.notebooks.recycler.NotebooksRecyclerAdapter
import org.qosp.notes.ui.notebooks.recycler.NotebooksRecyclerListener
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.viewBinding
import org.qosp.notes.ui.utils.views.BottomSheet

@AndroidEntryPoint
class ManageNotebooksFragment : BaseFragment(R.layout.fragment_manage_notebooks) {
    private val binding by viewBinding(FragmentManageNotebooksBinding::bind)

    protected var mainMenu: Menu? = null

    private val model: ManageNotebooksViewModel by viewModels()
    private lateinit var adapter: NotebooksRecyclerAdapter

    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_notebooks)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        enlistNotebooks()

        binding.layoutAppBar.toolbarSelection.apply {
            inflateMenu(R.menu.manage_notebooks_selected)
            setNavigationOnClickListener { onBackPressedHandler(adapter) }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_delete_selected -> model.deleteNotebooks(*adapter.getSelectedItems().toTypedArray())
                    R.id.action_select_all -> adapter.selectAll()
                }
                true
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.manage_notebooks, menu)
        mainMenu = menu
        selectSortMethodItem()
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_create_notebook -> EditNotebookDialog.build(null).show(childFragmentManager, null)
            R.id.action_sort_navdrawer_notebook_created_asc -> activityModel.setSortNavdrawerNotebooksMethod(
                CREATION_ASC
            )

            R.id.action_sort_navdrawer_notebook_created_desc -> activityModel.setSortNavdrawerNotebooksMethod(
                CREATION_DESC
            )

            R.id.action_sort_navdrawer_notebook_name_asc -> activityModel.setSortNavdrawerNotebooksMethod(TITLE_ASC)
            R.id.action_sort_navdrawer_notebook_name_desc -> activityModel.setSortNavdrawerNotebooksMethod(TITLE_DESC)
        }
        enlistNotebooks()
        selectSortMethodItem()
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        adapter.listener = null
        super.onDestroyView()
    }

    private fun setupRecyclerView() {
        binding.recyclerNotebooks.layoutManager = LinearLayoutManager(requireContext())
        adapter = NotebooksRecyclerAdapter(
            object : NotebooksRecyclerListener {
                override fun onItemClick(position: Int) {
                    val notebook = adapter.getItemAtPosition(position)
                    if (adapter.selectedItemIds.isNotEmpty()) {
                        return adapter.toggleSelectionForItem(notebook.id)
                    }
                    findNavController().navigateSafely(
                        R.id.fragment_notebook,
                        bundleOf(
                            "notebookId" to notebook.id,
                            "notebookName" to notebook.name,
                        )
                    )
                }

                override fun onLongClick(position: Int): Boolean {
                    if (adapter.selectedItemIds.isNotEmpty()) {
                        return false
                    }

                    val notebook = adapter.getItemAtPosition(position)

                    BottomSheet.show(notebook.name, parentFragmentManager) {
                        action(R.string.action_rename_notebook, R.drawable.ic_pencil) {
                            EditNotebookDialog.build(notebook).show(parentFragmentManager, null)
                        }
                        action(R.string.action_delete, R.drawable.ic_bin) {
                            model.deleteNotebooks(notebook)
                        }
                        action(R.string.action_select_more, R.drawable.ic_select_more) {
                            adapter.toggleSelectionForItem(notebook.id)
                        }
                    }

                    return true
                }
            }
        )

        adapter.enableSelection(this@ManageNotebooksFragment) {
            if (it.isNotEmpty()) {
                toolbar.isVisible = false
                binding.layoutAppBar.toolbarSelection.isVisible = true
                binding.layoutAppBar.toolbarSelection.title =
                    resources.getQuantityString(R.plurals.selected_notebooks, it.size, it.size)
                return@enableSelection
            }

            binding.layoutAppBar.toolbarSelection.isVisible = false
            toolbar.isVisible = true
        }

        adapter.setOnListChangedListener {
            binding.indicatorNotebooksEmpty.isVisible = it.isEmpty()
        }

        binding.recyclerNotebooks.adapter = adapter
        postponeEnterTransition()
        binding.recyclerNotebooks.doOnPreDraw { startPostponedEnterTransition() }
        binding.recyclerNotebooks.liftAppBarOnScroll(
            binding.layoutAppBar.appBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )
    }

    private fun enlistNotebooks() {
        // Get the current sort method preferences.
        val sort = model.getSortNavdrawerNotebooksMethod()

        activityModel.notebooks.collect(viewLifecycleOwner) { (_, notebooks) ->
            // Apply sorting to the list of notebooks.
            val sortedNotebooks: List<Notebook> = when (sort) {
                TITLE_ASC.name -> notebooks.sortedBy { it.name }
                TITLE_DESC.name -> notebooks.sortedByDescending { it.name }
                CREATION_ASC.name -> notebooks.sortedBy { it.id }
                CREATION_DESC.name -> notebooks.sortedByDescending { it.id }
                else -> notebooks.sortedBy { it.name }
            }

            // Displaying the sorted list of notebooks in the view.
            adapter.submitList(sortedNotebooks)
        }
    }

    private fun selectSortMethodItem() {
        mainMenu?.findItem(
            when (model.getSortNavdrawerNotebooksMethod()) {
                TITLE_ASC.name -> R.id.action_sort_navdrawer_notebook_name_asc
                TITLE_DESC.name -> R.id.action_sort_navdrawer_notebook_name_desc
                CREATION_ASC.name -> R.id.action_sort_navdrawer_notebook_created_asc
                CREATION_DESC.name -> R.id.action_sort_navdrawer_notebook_created_desc
                else -> R.id.action_sort_navdrawer_notebook_name_asc
            }
        )?.isChecked = true
    }
}
