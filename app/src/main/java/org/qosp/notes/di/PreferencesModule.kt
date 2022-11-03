package org.qosp.notes.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tfcporciuncula.flow.FlowSharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.qosp.notes.preferences.PreferenceRepository
import java.io.File
import java.security.KeyStore
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore("preferences")

@Module
@InstallIn(SingletonComponent::class)
@OptIn(ExperimentalCoroutinesApi::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun providePreferenceRepository(
        dataStore: DataStore<Preferences>,
        sharedPreferences: FlowSharedPreferences,
    ): PreferenceRepository {
        return PreferenceRepository(dataStore, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context) = context.dataStore

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): FlowSharedPreferences {
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

                context.getSharedPreferences(filename, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()

                keyStore.load(null)
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                prefsFile.delete()
            } catch (e: Throwable) {}
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
