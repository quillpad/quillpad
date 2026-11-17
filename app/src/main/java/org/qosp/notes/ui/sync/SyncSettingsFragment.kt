package org.qosp.notes.ui.sync

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.qosp.notes.R
import org.qosp.notes.data.sync.davx5.Davx5CalendarInfo
import org.qosp.notes.data.sync.davx5.Davx5Contract
import org.qosp.notes.data.sync.davx5.Davx5Helper
import org.qosp.notes.data.sync.fs.toFriendlyString
import org.qosp.notes.databinding.DialogDavx5AccountBinding
import org.qosp.notes.databinding.FragmentSyncSettingsBinding
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.settings.SettingsViewModel
import org.qosp.notes.ui.settings.showPreferenceDialog
import org.qosp.notes.ui.sync.nextcloud.NextcloudAccountDialog
import org.qosp.notes.ui.sync.nextcloud.NextcloudServerDialog
import org.qosp.notes.ui.utils.StorageLocationContract
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.viewBinding

class SyncSettingsFragment : BaseFragment(R.layout.fragment_sync_settings) {
    private val binding by viewBinding(FragmentSyncSettingsBinding::bind)
    private val model: SettingsViewModel by activityViewModel()

    override val hasMenu = false
    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.preferences_header_syncing)

    private var appPreferences = AppPreferences()
    private var nextcloudUrl = ""
    private var storageLocation: Uri? = null
    private var davx5AccountName: String = ""
    private var davx5CollectionId: Long = 0L
    private var pendingCalendarAction: (() -> Unit)? = null

    private val locationListener = registerForActivityResult(StorageLocationContract) { uri ->
        uri?.let {
            val previousLocation = storageLocation?.toString() ?: ""
            val newLocation = it.toString()

            model.setEncryptedString(PreferenceRepository.STORAGE_LOCATION, newLocation)
            Log.i(TAG, "Storing location: $it")

            model.removeFileStorageIdMappingsIfNeeded(newLocation, previousLocation)

            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context?.contentResolver?.takePersistableUriPermission(it, takeFlags)
        }
    }

    private val chooseAccountLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                model.setDavx5Account(accountName)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.indicator_davx5_currently_synced_with, accountName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val requestCalendarPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val ctx = context
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] == true ||
            (ctx != null && Davx5Helper.hasReadCalendarPermission(ctx))
        if (readGranted) {
            val action = pendingCalendarAction
            pendingCalendarAction = null
            action?.invoke()
        } else {
            pendingCalendarAction = null
            if (ctx != null) {
                Toast.makeText(
                    ctx,
                    R.string.preferences_davx5_calendar_permission_required,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.scrollView.liftAppBarOnScroll(
            binding.layoutAppBar.appBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )

        setupPreferenceObservers()
        setupSyncServiceListener()
        setupSyncModeListener()
        setupBackgroundSyncListener()
        setupNewNotesSyncableListener()

        setupNextcloudServerListener()
        setupNextcloudAccountListener()
        setupClearNextcloudCredentialsListener()
        setupTrustCertificatesListener()

        setupDavx5Listeners()
        setupLocalLocationListener()
    }

    override fun onResume() {
        super.onResume()
        updateDavx5InstallationStatus()
        binding.settingDavx5Collection.subText =
            getCollectionDisplayName(davx5CollectionId) ?: getString(R.string.preferences_davx5_collection_default)
    }

    private fun View.show(visible: Boolean) = if (visible) visibility = View.VISIBLE else visibility = View.GONE

    private fun setupPreferenceObservers() {
        model.appPreferences.collect(viewLifecycleOwner) { prefs ->
            appPreferences = prefs

            val isNextcloud = prefs.cloudService == CloudService.NEXTCLOUD
            val isDavx5 = prefs.cloudService == CloudService.DAVX5
            val isNetworkService = isNextcloud || isDavx5

            // Update visibility of layouts based on cloud service
            binding.layoutGenericSettings.show(isNetworkService)
            binding.layoutNextcloudSettings.show(isNextcloud)
            binding.layoutDavx5Settings.show(isDavx5)
            binding.layoutStorageSettings.show(prefs.cloudService == CloudService.FILE_STORAGE)
            binding.settingSyncMode.show(isNetworkService)
            binding.settingBackgroundSync.show(isNetworkService)
            binding.settingNotesSyncableByDefault.show(isNetworkService)
            binding.settingTrustSelfSignedCertificate.show(isNextcloud)

            binding.settingSyncProvider.subText = getString(prefs.cloudService.nameResource)
            binding.settingSyncMode.subText = getString(prefs.syncMode.nameResource)
            binding.settingBackgroundSync.subText = getString(prefs.backgroundSync.nameResource)
            binding.settingNotesSyncableByDefault.subText = getString(prefs.newNotesSyncable.nameResource)
            binding.settingTrustSelfSignedCertificate.subText = getString(prefs.trustSelfSignedCertificate.nameResource)

            if (isDavx5) {
                updateDavx5InstallationStatus()
            }
        }

        // ENCRYPTED
        model.getEncryptedString(PreferenceRepository.NEXTCLOUD_INSTANCE_URL).collect(viewLifecycleOwner) {
            nextcloudUrl = it
            binding.settingNextcloudServer.subText =
                nextcloudUrl.ifEmpty { getString(R.string.preferences_nextcloud_set_server_url) }
        }

        model.loggedInUsername.collect(viewLifecycleOwner) {
            binding.settingNextcloudAccount.subText = if (it != null) {
                getString(R.string.indicator_nextcloud_currently_logged_in_as, it)
            } else {
                getString(R.string.preferences_nextcloud_set_your_credentials)
            }
        }

        model.davx5AccountName.collect(viewLifecycleOwner) { account ->
            davx5AccountName = account
            binding.settingDavx5Account.subText = if (account.isNotEmpty()) {
                getString(R.string.indicator_davx5_currently_synced_with, account)
            } else {
                getString(R.string.preferences_davx5_no_account_selected)
            }
        }

        model.davx5CollectionId.collect(viewLifecycleOwner) { colIdStr ->
            val colId = colIdStr.toLongOrNull() ?: 0L
            davx5CollectionId = colId
            val collectionName = getCollectionDisplayName(colId)
            binding.settingDavx5Collection.subText = collectionName ?: getString(R.string.preferences_davx5_collection_default)
        }

        model.getEncryptedString(PreferenceRepository.STORAGE_LOCATION).collect(viewLifecycleOwner) { u ->
            val uri = u.toUri()
            storageLocation = uri
            val appName = if (u.isNotBlank()) context?.let { uri.toFriendlyString(it) } else null
            binding.settingStorageLocation.subText = appName ?: getString(R.string.preferences_file_storage_select)
        }
    }

    private fun updateDavx5InstallationStatus() {
        val context = context ?: return
        val isInstalled = Davx5Helper.isDavx5Installed(context)
        binding.settingDavx5Status.subText = getString(
            if (isInstalled) R.string.preferences_davx5_status_installed
            else R.string.preferences_davx5_status_not_installed
        )
    }

    private fun setupDavx5Listeners() {
        binding.settingDavx5Open.setOnClickListener {
            context?.let { Davx5Helper.openDavx5(it) }
        }

        binding.settingDavx5AddAccount.setOnClickListener {
            context?.let { Davx5Helper.openDavx5Login(it) }
        }

        binding.settingDavx5Account.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (Davx5Helper.hasCalendarPermissions(ctx)) {
                showDavx5AccountDialog()
            } else {
                pendingCalendarAction = { showDavx5AccountDialog() }
                requestCalendarPermissionsLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }
        }

        binding.settingDavx5Collection.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (Davx5Helper.hasCalendarPermissions(ctx)) {
                showDavx5CollectionDialog()
            } else {
                pendingCalendarAction = { showDavx5CollectionDialog() }
                requestCalendarPermissionsLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }
        }

        binding.settingDavx5SyncNow.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val triggerSync = {
                model.syncDavx5Now()
                Davx5Helper.requestSync(ctx, davx5AccountName.ifEmpty { null }, davx5CollectionId)
                Toast.makeText(ctx, R.string.preferences_davx5_sync_requested, Toast.LENGTH_SHORT).show()
            }
            if (davx5CollectionId > 0L && !Davx5Helper.hasCalendarPermissions(ctx)) {
                pendingCalendarAction = triggerSync
                requestCalendarPermissionsLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            } else {
                triggerSync()
            }
        }
    }

    private fun showDavx5AccountDialog() {
        val context = context ?: return
        val accounts = Davx5Helper.getDavx5Accounts(context)

        if (accounts.isNotEmpty()) {
            val accountNames = accounts.map { it.name }.toMutableList()
            val selectedIndex = if (davx5AccountName.isNotEmpty()) {
                val idx = accountNames.indexOf(davx5AccountName)
                if (idx >= 0) {
                    idx
                } else {
                    accountNames.add(0, davx5AccountName)
                    0
                }
            } else {
                -1
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.preferences_davx5_select_account)
                .setSingleChoiceItems(accountNames.toTypedArray(), selectedIndex) { dialog, which ->
                    val chosen = accountNames[which]
                    model.setDavx5Account(chosen)
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.preferences_davx5_choose_system) { _, _ ->
                    launchSystemAccountChooser()
                }
                .setNeutralButton(R.string.preferences_davx5_enter_manually) { _, _ ->
                    showManualAccountInputDialog()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            val options = arrayOf(
                getString(R.string.preferences_davx5_action_system_chooser),
                getString(R.string.preferences_davx5_action_enter_manually)
            )

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.preferences_davx5_account)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> launchSystemAccountChooser()
                        1 -> showManualAccountInputDialog()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun launchSystemAccountChooser() {
        val context = context ?: return
        try {
            val intent = Davx5Helper.createChooseAccountIntent(davx5AccountName.ifEmpty { null })
            chooseAccountLauncher.launch(intent)
        } catch (e: Exception) {
            Log.w(TAG, "createChooseAccountIntent failed, trying generic chooser", e)
            try {
                val genericIntent = Davx5Helper.createGenericChooseAccountIntent(davx5AccountName.ifEmpty { null })
                chooseAccountLauncher.launch(genericIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "All chooseAccountIntents failed", e2)
                showManualAccountInputDialog()
            }
        }
    }

    private fun showManualAccountInputDialog() {
        val context = context ?: return
        val dialogBinding = DialogDavx5AccountBinding.inflate(layoutInflater)
        dialogBinding.editTextDavx5Account.setText(davx5AccountName)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.preferences_davx5_manual_account_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val typedName = dialogBinding.editTextDavx5Account.text?.toString()?.trim().orEmpty()
                if (typedName.isNotEmpty()) {
                    model.setDavx5Account(typedName)
                    Toast.makeText(
                        context,
                        getString(R.string.indicator_davx5_currently_synced_with, typedName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDavx5CollectionDialog() {
        val context = context ?: return
        val localItem = Davx5CalendarInfo(
            id = 0L,
            displayName = getString(R.string.preferences_davx5_collection_default),
            accountName = "",
            accountType = ""
        )
        val remoteCalendars = Davx5Helper.getDavx5Calendars(context, davx5AccountName.ifEmpty { null })
            .filter { it.id > 0L && it.accountName != "Local" && it.accountName != "DAVx5" }

        val allCalendars = listOf(localItem) + remoteCalendars

        val currentSelectedId = davx5CollectionId
        val calendarNames = allCalendars.map { cal ->
            if (cal.id > 0L && cal.accountName.isNotEmpty() && cal.accountName != davx5AccountName) {
                "${cal.displayName} (${cal.accountName})"
            } else {
                cal.displayName
            }
        }.toTypedArray()

        var selectedIndex = allCalendars.indexOfFirst { it.id == currentSelectedId }
        if (selectedIndex < 0) selectedIndex = 0

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.preferences_davx5_select_collection)
            .setSingleChoiceItems(calendarNames, selectedIndex) { dialog, which ->
                val selectedCal = allCalendars[which]
                model.setDavx5Collection(selectedCal.id)

                if (selectedCal.id > 0L && davx5AccountName.isEmpty() && selectedCal.accountName.isNotEmpty()) {
                    model.setDavx5Account(selectedCal.accountName)
                }

                // Cache selection in local Davx5Contract.Collection table
                if (selectedCal.id > 0L) {
                    try {
                        val values = ContentValues().apply {
                            put(Davx5Contract.Collection._ID, selectedCal.id)
                            put(Davx5Contract.Collection.DISPLAY_NAME, selectedCal.displayName)
                            put(Davx5Contract.Collection.ACCOUNT_NAME, selectedCal.accountName)
                            put(Davx5Contract.Collection.ACCOUNT_TYPE, selectedCal.accountType)
                            put(Davx5Contract.Collection.SUPPORTS_VJOURNAL, 1)
                        }
                        context.contentResolver.insert(Davx5Contract.CONTENT_URI_COLLECTION, values)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error caching collection in Davx5Contract", e)
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getCollectionDisplayName(collectionId: Long): String? {
        val context = context ?: return null
        if (collectionId <= 0L) {
            return getString(R.string.preferences_davx5_collection_default)
        }

        // Check in CalendarContract / Davx5Helper
        val calendars = Davx5Helper.getDavx5Calendars(context)
            .filter { it.id > 0L && it.accountName != "Local" && it.accountName != "DAVx5" }
        val matchedCal = calendars.firstOrNull { it.id == collectionId }
        if (matchedCal != null) {
            return matchedCal.displayName
        }

        // Check in Davx5Contract.Collection
        val cachedName = try {
            val cursor = context.contentResolver.query(
                Davx5Contract.CONTENT_URI_COLLECTION,
                arrayOf(Davx5Contract.Collection.DISPLAY_NAME, Davx5Contract.Collection.ACCOUNT_NAME),
                "${Davx5Contract.Collection._ID} = ?",
                arrayOf(collectionId.toString()),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    val acc = it.getString(1)
                    if (acc != "Local" && acc != "DAVx5" && name != "Notes") name else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
        return cachedName ?: getString(R.string.preferences_davx5_collection_default)
    }

    private fun setupLocalLocationListener() = binding.settingStorageLocation.setOnClickListener {
        locationListener.launch(storageLocation)
    }

    private fun setupNextcloudServerListener() = binding.settingNextcloudServer.setOnClickListener {
        NextcloudServerDialog.build(nextcloudUrl).show(childFragmentManager, null)
    }

    private fun setupNextcloudAccountListener() = binding.settingNextcloudAccount.setOnClickListener {
        NextcloudAccountDialog().show(childFragmentManager, null)
    }

    private fun setupSyncServiceListener() = binding.settingSyncProvider.setOnClickListener {
        showPreferenceDialog(R.string.preferences_cloud_service, appPreferences.cloudService) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupSyncModeListener() = binding.settingSyncMode.setOnClickListener {
        showPreferenceDialog(R.string.preferences_sync_when_on, appPreferences.syncMode) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupBackgroundSyncListener() = binding.settingBackgroundSync.setOnClickListener {
        showPreferenceDialog(R.string.preferences_background_sync, appPreferences.backgroundSync) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupNewNotesSyncableListener() = binding.settingNotesSyncableByDefault.setOnClickListener {
        showPreferenceDialog(
            R.string.preferences_new_notes_synchronizable,
            appPreferences.newNotesSyncable
        ) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupTrustCertificatesListener() = binding.settingTrustSelfSignedCertificate.setOnClickListener {
        showPreferenceDialog(
            R.string.preferences_trust_self_signed_certificate,
            appPreferences.trustSelfSignedCertificate
        ) { selected ->
            model.setPreference(selected)
        }
    }

    private fun setupClearNextcloudCredentialsListener() = binding.settingNextcloudClearCredentials.setOnClickListener {
        model.clearNextcloudCredentials()
    }
}
