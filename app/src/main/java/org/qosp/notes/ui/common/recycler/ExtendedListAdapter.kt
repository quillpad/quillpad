package org.qosp.notes.ui.common.recycler

import androidx.annotation.CallSuper
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import org.qosp.notes.ui.common.BaseFragment

private const val STATE_SELECTED_IDS = "STATE_SELECTED_IDS"

interface SelectableViewHolder {
    fun onSelectedStatusChanged(isSelected: Boolean)
}

val BaseFragment.onBackPressedHandler: (ExtendedListAdapter<*, *>) -> Unit
    get() = { adapter ->
        if (adapter.getSelectedItems().isNotEmpty()) adapter.clearSelection()
        else if (!findNavController().navigateUp()) {
            activity?.finish()
        }
    }

abstract class ExtendedListAdapter<T, VH : RecyclerView.ViewHolder?>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, VH>(diffCallback) {

    private var recyclerView: RecyclerView? = null
    private var isSelectionEnabled = false

    private var registry: SavedStateRegistry? = null
    private val _selectedItemIds: MutableList<Long> = mutableListOf()
    val selectedItemIds: List<Long> get() = _selectedItemIds

    private var listChangedListener: (List<T>) -> Unit = {}
    private var selectionChangedListener: (List<Long>) -> Unit = {}

    abstract override fun getItemId(position: Int): Long

    fun enableSelection(
        owner: SavedStateRegistryOwner,
        onSelectionChangedListener: (List<Long>) -> Unit = {}
    ) {
        if (owner is Fragment) {
            owner.viewLifecycleOwnerLiveData.observe(owner) { viewLifecycleOwner ->
                viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        releaseReferences()
                    }
                })
            }
        }

        registry = owner.savedStateRegistry
        selectionChangedListener = onSelectionChangedListener

        setHasStableIds(true)

        isSelectionEnabled = true

        val bundle = registry?.consumeRestoredStateForKey(STATE_SELECTED_IDS) ?: return
        val savedItemIds = bundle.getLongArray(STATE_SELECTED_IDS)?.toList() ?: return

        _selectedItemIds.clear()
        _selectedItemIds.addAll(savedItemIds)

        saveSelectedIds()
        onSelectionChanged()
    }

    fun setOnListChangedListener(listener: (List<T>) -> Unit) {
        listChangedListener = listener
    }

    fun getItemAtPosition(position: Int): T = getItem(position)

    open fun getSelectedItems(): List<T> {
        if (currentList.isEmpty() || !isSelectionEnabled) return listOf()
        val items = mutableListOf<T>()

        _selectedItemIds.forEach {
            val pos = getPositionById(it) ?: return@forEach
            val item = currentList.getOrElse(pos) { return@forEach }
            items.add(item)
        }

        return items
    }

    fun toggleSelectionForItem(itemId: Long) {
        if (!isSelectionEnabled) return

        when {
            _selectedItemIds.contains(itemId) -> _selectedItemIds.remove(itemId)
            else -> _selectedItemIds.add(itemId)
        }

        onItemSelectedStatusChanged(itemId)
        onSelectionChanged()
    }

    fun selectAll() {
        if (!isSelectionEnabled) return

        for (pos in 0 until itemCount) {
            val itemId = getItemId(pos)
            if (!_selectedItemIds.contains(itemId)) {
                _selectedItemIds.add(itemId)
                onItemSelectedStatusChanged(itemId)
            }
        }

        onSelectionChanged()
    }

    fun clearSelection() {
        if (!isSelectionEnabled) return

        val iter = _selectedItemIds.iterator()
        while (iter.hasNext()) {
            val id = iter.next()
            iter.remove()
            onItemSelectedStatusChanged(id)
        }

        onSelectionChanged()
    }

    private fun getPositionById(itemId: Long): Int? {
        for (pos in 0 until itemCount) {
            val id = getItemId(pos)
            if (id == itemId) return pos
        }
        return null
    }

    private fun onItemSelectedStatusChanged(itemId: Long, holder: VH? = null) {
        val holder = holder ?: (recyclerView?.findViewHolderForItemId(itemId) ?: return)
        if (holder is SelectableViewHolder) {
            holder.onSelectedStatusChanged(_selectedItemIds.contains(itemId))
        }
    }

    private fun saveSelectedIds() {
        registry?.unregisterSavedStateProvider(STATE_SELECTED_IDS)
        registry?.registerSavedStateProvider(STATE_SELECTED_IDS) {
            bundleOf(STATE_SELECTED_IDS to _selectedItemIds.toLongArray())
        }
    }

    private fun updateSelectedIds(newList: List<T>) {
        val newListIds = newList.mapIndexed { index, _ -> getItemId(index) }
        _selectedItemIds.removeIf { it !in newListIds }
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        saveSelectedIds()
        selectionChangedListener(_selectedItemIds)
    }

    override fun onCurrentListChanged(previousList: MutableList<T>, currentList: MutableList<T>) {
        super.onCurrentListChanged(previousList, currentList)
        updateSelectedIds(currentList)
        listChangedListener(currentList)
    }

    @CallSuper
    override fun onBindViewHolder(holder: VH, position: Int) {
        if (isSelectionEnabled) onItemSelectedStatusChanged(getItemId(position), holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (isSelectionEnabled) this.recyclerView = recyclerView
    }

    private fun releaseReferences() {
        recyclerView?.adapter = null
        recyclerView = null
        listChangedListener = {}
        selectionChangedListener = {}
    }
}
