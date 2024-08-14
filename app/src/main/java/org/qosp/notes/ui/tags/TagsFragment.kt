package org.qosp.notes.ui.tags

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentTagsBinding
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

    private val args: TagsFragmentArgs by navArgs()
    private val model: TagsViewModel by viewModels()

    private lateinit var adapter: TagsRecyclerAdapter

    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_tags)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        model
            .getData(args.noteId.takeIf { it >= 0L })
            .collect(viewLifecycleOwner) {
                adapter.submitList(it)
            }

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
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tags, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_create_tag -> EditTagDialog.build(null).show(childFragmentManager, null)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        adapter.listener = null
        super.onDestroyView()
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
}
