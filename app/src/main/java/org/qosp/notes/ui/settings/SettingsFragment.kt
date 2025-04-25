package org.qosp.notes.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentSettingsBinding
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.DarkThemeMode
import org.qosp.notes.preferences.DateFormat
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.ThemeMode
import org.qosp.notes.preferences.TimeFormat
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.utils.RestoreNotesContract
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.viewBinding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class SettingsFragment : BaseFragment(resId = R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val model: SettingsViewModel by viewModels()

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
        setupOpenMediaInListener()
        setupNoteDeletionTimeListener()
        setupBackupStrategyListener()
        setupShowDateListener()
        setupShowFontSizeListener()
        setupShowFabChangeModeListener()
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
                binding.settingGoToSyncSettings.subText = when (cloudService) {
                    CloudService.DISABLED -> getString(R.string.preferences_currently_not_syncing)
                    else -> getString(R.string.preferences_currently_syncing_with, getString(cloudService.nameResource))
                }
                binding.settingColorScheme.subText = getString(colorScheme.nameResource)
                binding.settingThemeMode.subText = getString(themeMode.nameResource)
                binding.settingDarkThemeMode.subText = getString(darkThemeMode.nameResource)
                binding.settingLayoutMode.subText = getString(layoutMode.nameResource)
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

                with(DateTimeFormatter.ofPattern(getString(dateFormat.patternResource))) {
                    binding.settingDateFormat.subText = format(LocalDate.now())
                }
                with(DateTimeFormatter.ofPattern(getString(timeFormat.patternResource))) {
                    binding.settingTimeFormat.subText = format(LocalTime.now())
                }
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
        showPreferenceDialog(R.string.preferences_group_notes_without_notebook, appPreferences.groupNotesWithoutNotebook) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupMoveCheckedItemsListener() = binding.settingMoveCheckedItems.setOnClickListener {
        showPreferenceDialog(R.string.preferences_move_checked_items, appPreferences.moveCheckedItems) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupOpenMediaInListener() = binding.settingOpenMedia.setOnClickListener {
        showPreferenceDialog(R.string.preferences_open_media_in, appPreferences.openMediaIn) { selected ->
            model.setPreference(selected)
        }
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
}
