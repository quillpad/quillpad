package org.qosp.notes.ui.sync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.qosp.notes.R
import org.qosp.notes.data.sync.fs.toFriendlyString
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

    private val locationListener = registerForActivityResult(StorageLocationContract) { uri ->
        uri?.let {
            // Get the previous location before setting the new one
            val previousLocation = storageLocation?.toString() ?: ""
            val newLocation = it.toString()

            // Set the new location
            model.setEncryptedString(PreferenceRepository.STORAGE_LOCATION, newLocation)
            Log.i(TAG, "Storing location: $it")

            // Remove IdMappings if needed
            model.removeFileStorageIdMappingsIfNeeded(newLocation, previousLocation)

            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context?.contentResolver?.takePersistableUriPermission(it, takeFlags)
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

        setupLocalLocationListener()
    }

    private fun View.show(visible: Boolean) = if (visible) visibility = View.VISIBLE else visibility = View.GONE

    private fun setupPreferenceObservers() {
        model.appPreferences.collect(viewLifecycleOwner) { prefs ->
            appPreferences = prefs

            // Update visibility of layouts based on cloud service
            binding.layoutGenericSettings.show(prefs.cloudService == CloudService.NEXTCLOUD)
            binding.layoutNextcloudSettings.show(prefs.cloudService == CloudService.NEXTCLOUD)
            binding.layoutStorageSettings.show(prefs.cloudService == CloudService.FILE_STORAGE)
            binding.settingSyncMode.show(prefs.cloudService == CloudService.NEXTCLOUD)
            binding.settingBackgroundSync.show(prefs.cloudService == CloudService.NEXTCLOUD)
            binding.settingNotesSyncableByDefault.show(prefs.cloudService == CloudService.NEXTCLOUD)
            binding.settingTrustSelfSignedCertificate.show(prefs.cloudService == CloudService.NEXTCLOUD)

            binding.settingSyncProvider.subText = getString(prefs.cloudService.nameResource)
            binding.settingSyncMode.subText = getString(prefs.syncMode.nameResource)
            binding.settingBackgroundSync.subText = getString(prefs.backgroundSync.nameResource)
            binding.settingNotesSyncableByDefault.subText = getString(prefs.newNotesSyncable.nameResource)
            binding.settingTrustSelfSignedCertificate.subText = getString(prefs.trustSelfSignedCertificate.nameResource)
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

        model.getEncryptedString(PreferenceRepository.STORAGE_LOCATION).collect(viewLifecycleOwner) { u ->
            val uri = u.toUri()
            storageLocation = uri
            val appName = if (u.isNotBlank()) context?.let { uri.toFriendlyString(it) } else null
            binding.settingStorageLocation.subText = appName ?: getString(R.string.preferences_file_storage_select)
        }
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
