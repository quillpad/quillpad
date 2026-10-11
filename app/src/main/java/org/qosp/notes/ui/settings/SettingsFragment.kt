package org.qosp.notes.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentSettingsBinding
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.DarkThemeMode
import org.qosp.notes.preferences.DateFormat
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.ThemeMode
import org.qosp.notes.preferences.TimeFormat
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.editor.EditorBottomToolbarConfig
import org.qosp.notes.ui.editor.EditorBottomToolbarItemState
import org.qosp.notes.ui.utils.RestoreNotesContract
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.viewBinding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsFragment : BaseFragment(resId = R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val model: SettingsViewModel by viewModel()

    private var appPreferences = AppPreferences()

    override val hasMenu = false
    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_settings)

    private val loadBackupLauncher = registerForActivityResult(RestoreNotesContract) { uri ->
        if (uri == null) return@registerForActivityResult
        (activity as MainActivity).restoreNotes(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPreferenceObservers()
        setupColorSchemeListener()
        setupThemeModeListener()
        setupDarkThemeModeListener()
        setupLayoutModeListener()
        setupSortMethodListener()
        setupSortTagsMethodListener()
        setupSortNavdrawerMethodListener()
        setupGroupNotesWithoutNotebookListener()
        setupMoveCheckedItemsListener()
        setupEditBottomToolbarListener()
        setupOpenMediaInListener()
        setupNoteDeletionTimeListener()
        setupBackupStrategyListener()
        setupShowDateListener()
        setupShowFontSizeListener()
        setupShowFabChangeModeListener()
        setupDefaultEditorModeListener()
        setupDateFormatListener()
        setupTimeFormatListener()
        setupSyncSettingsListener()

        binding.scrollView.liftAppBarOnScroll(
            binding.layoutAppBar.appBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )

        binding.settingRestoreNotes.setOnClickListener { loadBackupLauncher.launch(null) }

        binding.settingBackupNotes.setOnClickListener {
            activityModel.notesToBackup = null
            exportNotesLauncher.launch(null)
        }
    }

    private fun setupPreferenceObservers() {
        model.appPreferences.collect(viewLifecycleOwner) {
            appPreferences = it

            with(appPreferences) {
                val goToSync = when (cloudService) {
                    CloudService.DISABLED -> getString(R.string.preferences_currently_not_syncing)
                    else -> getString(R.string.preferences_currently_syncing_with, getString(cloudService.nameResource))
                }
                binding.settingGoToSyncSettings.subText = goToSync
                binding.settingLayoutMode.setIcon(
                    when (layoutMode) {
                        LayoutMode.GRID -> R.drawable.ic_grid
                        LayoutMode.LIST -> R.drawable.ic_list
                    }
                )
                binding.settingSortMethod.subText = getString(sortMethod.nameResource)
                binding.settingSortTagsMethod.subText = getString(sortTagsMethod.nameResource)
                binding.settingSortNavdrawerMethod.subText = getString(sortNavdrawerNotebooksMethod.nameResource)
                binding.settingBackupStrategy.subText = getString(backupStrategy.nameResource)
                binding.settingOpenMedia.subText = getString(openMediaIn.nameResource)
                binding.settingNoteDeletion.subText = getString(noteDeletionTime.nameResource)

                binding.settingGroupNotesWithoutNotebook.subText = getString(groupNotesWithoutNotebook.nameResource)
                binding.settingMoveCheckedItems.subText = getString(moveCheckedItems.nameResource)
                binding.settingShowDate.subText = getString(showDate.nameResource)
                binding.settingFontSize.subText = getString(editorFontSize.nameResource)
                binding.settingShowFab.subText = getString(showFabChangeMode.nameResource)
                binding.settingDefaultEditorMode.subText = getString(defaultEditorMode.nameResource)
                with(DateTimeFormatter.ofPattern(getString(dateFormat.patternResource))) {
                    binding.settingDateFormat.subText = format(LocalDate.now())
                }
                with(DateTimeFormatter.ofPattern(getString(timeFormat.patternResource))) {
                    binding.settingTimeFormat.subText = format(LocalTime.now())
                }

                binding.settingThemeMode.subText = getString(themeMode.nameResource)
                binding.settingDarkThemeMode.subText = getString(darkThemeMode.nameResource)
                binding.settingColorScheme.subText = getString(colorScheme.nameResource)
                binding.settingLayoutMode.subText = getString(layoutMode.nameResource)

            }
        }
    }

    private fun setupSyncSettingsListener() = binding.settingGoToSyncSettings.setOnClickListener {
        findNavController().navigateSafely(SettingsFragmentDirections.actionMainSettingsToSync())
    }

    private fun setupColorSchemeListener() = binding.settingColorScheme.setOnClickListener {
        showPreferenceDialog(R.string.preferences_color_scheme, appPreferences.colorScheme) { selected ->
            lifecycleScope.launch {
                model.setPreferenceSuspending(selected)
                activity?.recreate()
            }
        }
    }

    private fun setupThemeModeListener() = binding.settingThemeMode.setOnClickListener {
        showPreferenceDialog(R.string.preferences_theme_mode, appPreferences.themeMode) { selected ->
            lifecycleScope.launch {
                model.setPreferenceSuspending(selected)
                if (selected.mode != AppCompatDelegate.getDefaultNightMode()) {
                    AppCompatDelegate.setDefaultNightMode(selected.mode)

                    if (selected != ThemeMode.LIGHT && appPreferences.darkThemeMode == DarkThemeMode.BLACK) {
                        activity?.recreate()
                    }
                }
            }
        }
    }

    private fun setupDarkThemeModeListener() = binding.settingDarkThemeMode.setOnClickListener {
        showPreferenceDialog(R.string.preferences_dark_theme_mode, appPreferences.darkThemeMode) { selected ->
            lifecycleScope.launch {
                model.setPreferenceSuspending(selected)
                activity?.recreate()
            }
        }
    }

    private fun setupLayoutModeListener() = binding.settingLayoutMode.setOnClickListener {
        showPreferenceDialog(R.string.preferences_layout_mode, appPreferences.layoutMode) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupSortMethodListener() = binding.settingSortMethod.setOnClickListener {
        showPreferenceDialog(R.string.preferences_sort_method, appPreferences.sortMethod) { selected ->
            model.setPreference(selected)
        }
    }

    /* Changes the sorting method of tags list. */
    private fun setupSortTagsMethodListener() = binding.settingSortTagsMethod.setOnClickListener {
        showPreferenceDialog(R.string.preferences_sort_tags_method, appPreferences.sortTagsMethod) { selected ->
            model.setPreference(selected)
        }
    }

    /* Changes the sorting method of notebooks list in the navigation drawer. */
    private fun setupSortNavdrawerMethodListener() = binding.settingSortNavdrawerMethod.setOnClickListener {
        showPreferenceDialog(R.string.preferences_sort_navdrawer_method, appPreferences.sortNavdrawerNotebooksMethod) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupBackupStrategyListener() = binding.settingBackupStrategy.setOnClickListener {
        showPreferenceDialog(R.string.preferences_backup_strategy, appPreferences.backupStrategy) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupGroupNotesWithoutNotebookListener() = binding.settingGroupNotesWithoutNotebook.setOnClickListener {
        showPreferenceDialog(
            R.string.preferences_group_notes_without_notebook,
            appPreferences.groupNotesWithoutNotebook
        ) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupMoveCheckedItemsListener() = binding.settingMoveCheckedItems.setOnClickListener {
        showPreferenceDialog(R.string.preferences_move_checked_items, appPreferences.moveCheckedItems) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupEditBottomToolbarListener() = binding.settingEditBottomToolbar.setOnClickListener {
        lifecycleScope.launch {
            val items = EditorBottomToolbarConfig
                .parse(model.getEncryptedString(PreferenceRepository.EDITOR_BOTTOM_TOOLBAR_CONFIG).first())
                .toMutableList()
            showEditBottomToolbarDialog(items)
        }
    }

    private fun setupOpenMediaInListener() = binding.settingOpenMedia.setOnClickListener {
        showPreferenceDialog(R.string.preferences_open_media_in, appPreferences.openMediaIn) { selected ->
            model.setPreference(selected)
        }
    }

    private fun showEditBottomToolbarDialog(items: MutableList<EditorBottomToolbarItemState>) {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp, 0, 8.dp)
        }

        fun refreshRows() {
            content.removeAllViews()
            items.forEachIndexed { index, state ->
                content.addView(createToolbarItemRow(items, index, state, ::refreshRows))
            }
        }

        refreshRows()

        val scrollView = ScrollView(requireContext()).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.preferences_edit_bottom_toolbar)
            .setView(scrollView)
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.action_reset, null)
            .setPositiveButton(R.string.action_done, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                items.clear()
                items.addAll(EditorBottomToolbarConfig.defaultItems)
                refreshRows()
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                model.setEncryptedString(
                    PreferenceRepository.EDITOR_BOTTOM_TOOLBAR_CONFIG,
                    EditorBottomToolbarConfig.serialize(items),
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun createToolbarItemRow(
        items: MutableList<EditorBottomToolbarItemState>,
        index: Int,
        state: EditorBottomToolbarItemState,
        refreshRows: () -> Unit,
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 6.dp, 8.dp, 6.dp)
        }

        val checkbox = AppCompatCheckBox(requireContext()).apply {
            isChecked = state.visible
            setOnCheckedChangeListener { _, isChecked ->
                items[index] = items[index].copy(visible = isChecked)
            }
        }

        val icon = AppCompatImageView(requireContext()).apply {
            setImageResource(state.item.iconRes)
            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply {
                marginEnd = 12.dp
            }
        }

        val label = AppCompatTextView(requireContext()).apply {
            text = getString(state.item.titleRes)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val upButton = AppCompatImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_up)
            contentDescription = getString(R.string.action_move_up)
            isEnabled = index > 0
            setOnClickListener {
                java.util.Collections.swap(items, index, index - 1)
                refreshRows()
            }
        }

        val downButton = AppCompatImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_down)
            contentDescription = getString(R.string.action_move_down)
            isEnabled = index < items.lastIndex
            setOnClickListener {
                java.util.Collections.swap(items, index, index + 1)
                refreshRows()
            }
        }

        listOf(checkbox, icon, label, upButton, downButton).forEach(row::addView)
        return row
    }

    private fun setupNoteDeletionTimeListener() = binding.settingNoteDeletion.setOnClickListener {
        showPreferenceDialog(R.string.preferences_note_deletion_time, appPreferences.noteDeletionTime) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupShowDateListener() = binding.settingShowDate.setOnClickListener {
        showPreferenceDialog(R.string.preferences_show_date, appPreferences.showDate) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupShowFontSizeListener() = binding.settingFontSize.setOnClickListener {
        showPreferenceDialog(R.string.preferences_font_size, appPreferences.editorFontSize) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupShowFabChangeModeListener() = binding.settingShowFab.setOnClickListener {
        showPreferenceDialog(R.string.preferences_show_fab_change_mode, appPreferences.showFabChangeMode) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupDefaultEditorModeListener() = binding.settingDefaultEditorMode.setOnClickListener {
        showPreferenceDialog(R.string.preferences_default_editor_mode, appPreferences.defaultEditorMode) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupTimeFormatListener() = binding.settingTimeFormat.setOnClickListener {
        val localTime = LocalTime.now()
        val items = TimeFormat.entries
            .map {
                DateTimeFormatter.ofPattern(getString(it.patternResource)).format(localTime)
            }
            .toTypedArray()

        showPreferenceDialog(R.string.preferences_time_format, appPreferences.timeFormat, items = items) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupDateFormatListener() = binding.settingDateFormat.setOnClickListener {
        val localDate = LocalDate.now()
        val items = DateFormat.entries
            .map {
                DateTimeFormatter.ofPattern(getString(it.patternResource)).format(localDate)
            }
            .toTypedArray()

        showPreferenceDialog(R.string.preferences_date_format, appPreferences.dateFormat, items = items) { selected ->
            model.setPreference(selected)
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
