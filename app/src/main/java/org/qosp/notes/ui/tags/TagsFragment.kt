package org.qosp.notes.ui.tags

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.msoul.datastore.defaultOf
import me.msoul.datastore.key
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Tag
import org.qosp.notes.databinding.FragmentTagsBinding
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.preferences.SortTagsMethod
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.common.recycler.onBackPressedHandler
import org.qosp.notes.ui.tags.dialog.EditTagDialog
import org.qosp.notes.ui.tags.recycler.TagsRecyclerAdapter
import org.qosp.notes.ui.tags.recycler.TagsRecyclerListener
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.viewBinding
import org.qosp.notes.ui.utils.views.BottomSheet

@AndroidEntryPoint
class TagsFragment : BaseFragment(R.layout.fragment_tags) {
    private val binding by viewBinding(FragmentTagsBinding::bind)
    val model: TagsViewModel by viewModels()

    protected var mainMenu: Menu? = null

    private val args: TagsFragmentArgs by navArgs()

    private lateinit var adapter: TagsRecyclerAdapter

    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_tags)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        enlistTags()

        binding.recyclerTags.liftAppBarOnScroll(
            binding.layoutAppBar.appBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )

        binding.layoutAppBar.toolbarSelection.apply {
            inflateMenu(R.menu.tags_selected)
            setNavigationOnClickListener { onBackPressedHandler(adapter) }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete_selected -> model.delete(
                        *adapter.getSelectedItems().map { it.tag }
                            .toTypedArray()
                    )
                    R.id.action_select_all -> adapter.selectAll()
                }
                true
            }
        }

        binding.layoutAppBar.toolbarSelection.apply {
            setOnMenuItemClickListener {

                true
            }
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tags, menu)
        mainMenu = menu
        selectSortMethodItem()
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_create_tag -> EditTagDialog.build(null).show(childFragmentManager, null)
            R.id.action_sort_tags_created_asc -> activityModel.setSortTagsMethod(SortTagsMethod.CREATION_ASC)
            R.id.action_sort_tags_created_desc -> activityModel.setSortTagsMethod(SortTagsMethod.CREATION_DESC)
            R.id.action_sort_tags_name_asc -> activityModel.setSortTagsMethod(SortTagsMethod.TITLE_ASC)
            R.id.action_sort_tags_name_desc -> activityModel.setSortTagsMethod(SortTagsMethod.TITLE_DESC)
        }
        enlistTags()
        selectSortMethodItem()
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        adapter.listener = null
        super.onDestroyView()
    }

    private fun enlistTags() {
        // Reading the settings: which tags sorting method is active?
        val sort = model.getSortTagsMethod()

        // Applying tags sorting.
        val sortedTagsList = model
            .getData(args.noteId.takeIf { it >= 0L })
            .map {
                when (sort) {
                    SortTagsMethod.CREATION_ASC.name -> it.sortedBy { it.tag.id }
                    SortTagsMethod.CREATION_DESC.name -> it.sortedByDescending { it.tag.id }
                    SortTagsMethod.TITLE_ASC.name -> it.sortedBy { it.tag.name }
                    SortTagsMethod.TITLE_DESC.name -> it.sortedByDescending { it.tag.name }
                    else -> it.sortedBy { it.tag.name }
                }
            }

        // Displaying the tags.
        sortedTagsList.collect(viewLifecycleOwner) {
            adapter.submitList(it)
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerTags.layoutManager = LinearLayoutManager(requireContext())

        adapter = TagsRecyclerAdapter(
            noteId = args.noteId.takeIf { it > 0L },
            object : TagsRecyclerListener {
                override fun onItemClick(position: Int) {
                    val tagData = adapter.getItemAtPosition(position)

                    if (adapter.selectedItemIds.isNotEmpty()) {
                        return adapter.toggleSelectionForItem(tagData.tag.id)
                    }

                    if (args.noteId <= 0L) {
                        return findNavController()
                            .navigateSafely(
                                TagsFragmentDirections.actionTagsToSearch().setSearchQuery(tagData.tag.name)
                            )
                    }

                    if (tagData.inNote) {
                        model.deleteTagFromNote(tagData.tag.id, args.noteId)
                    } else {
                        model.addTagToNote(tagData.tag.id, args.noteId)
                    }
                }

                override fun onLongClick(position: Int): Boolean {
                    if (adapter.selectedItemIds.isNotEmpty()) {
                        return false
                    }

                    val tagData = adapter.getItemAtPosition(position)

                    BottomSheet.show(tagData.tag.name, parentFragmentManager) {
                        action(R.string.action_rename_tag, R.drawable.ic_pencil) {
                            EditTagDialog.build(tagData.tag).show(parentFragmentManager, null)
                        }
                        action(R.string.action_delete, R.drawable.ic_bin) {
                            model.delete(tagData.tag)
                        }
                        action(R.string.action_select_more, R.drawable.ic_select_more) {
                            adapter.toggleSelectionForItem(tagData.tag.id)
                        }
                    }

                    return true
                }

                override fun checkTagOnClick(): Boolean = (args.noteId > 0L) && adapter.selectedItemIds.isEmpty()
            }
        )

        adapter.enableSelection(this@TagsFragment) {
            if (it.isNotEmpty()) {
                toolbar.isVisible = false
                binding.layoutAppBar.toolbarSelection.isVisible = true
                binding.layoutAppBar.toolbarSelection.title = resources.getQuantityString(R.plurals.selected_tags, it.size, it.size)
                return@enableSelection
            }

            binding.layoutAppBar.toolbarSelection.isVisible = false
            toolbar.isVisible = true
        }

        adapter.setOnListChangedListener {
            binding.indicatorTagsEmpty.isVisible = it.isEmpty()
        }

        binding.recyclerTags.adapter = adapter
        postponeEnterTransition()
        binding.recyclerTags.doOnPreDraw { startPostponedEnterTransition() }
    }

    private fun selectSortMethodItem() {
        mainMenu?.findItem(
            when (model.getSortTagsMethod()) {
                SortTagsMethod.TITLE_ASC.name -> R.id.action_sort_tags_name_asc
                SortTagsMethod.TITLE_DESC.name -> R.id.action_sort_tags_name_desc
                SortTagsMethod.CREATION_ASC.name -> R.id.action_sort_tags_created_asc
                SortTagsMethod.CREATION_DESC.name -> R.id.action_sort_tags_created_desc
                else -> R.id.action_sort_tags_name_asc
            }
        )?.isChecked = true
    }

}
