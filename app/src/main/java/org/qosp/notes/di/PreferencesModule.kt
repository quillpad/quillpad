package org.qosp.notes.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tfcporciuncula.flow.FlowSharedPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.qosp.notes.preferences.PreferenceRepository
import java.io.File
import java.security.KeyStore

val Context.dataStore by preferencesDataStore("preferences")

object PreferencesModule {

    val prefModule = module {
        single { androidContext().dataStore }
        single { encryptedSharedPreferences(androidContext()) }
        singleOf(::PreferenceRepository)
    }

    fun encryptedSharedPreferences(context: Context): FlowSharedPreferences {
        val filename = "encrypted_prefs"

        fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                filename,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        @SuppressLint("ApplySharedPref")
        fun deleteSharedPreferencesFileAndMasterKey(context: Context) {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                val appStorageDir = context.filesDir?.parent ?: return
                val prefsFile = File("$appStorageDir/shared_prefs/$filename.xml")

                context.getSharedPreferences(filename, Context.MODE_PRIVATE).edit(commit = true) { clear() }

                keyStore.load(null)
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                prefsFile.delete()
            } catch (_: Throwable) {
            }
        }

        return FlowSharedPreferences(
            try {
                createEncryptedSharedPreferences(context)
            } catch (e: Throwable) {
                deleteSharedPreferencesFileAndMasterKey(context)
                createEncryptedSharedPreferences(context)
            }
        )
    }
}
