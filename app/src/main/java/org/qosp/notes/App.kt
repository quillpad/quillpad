package org.qosp.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.StrictMode
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration
import org.koin.ksp.generated.module
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker
import org.qosp.notes.di.DatabaseModule
import org.qosp.notes.di.KoinWorkerFactory
import org.qosp.notes.di.MarkwonModule
import org.qosp.notes.di.NextcloudModule
import org.qosp.notes.di.PreferencesModule
import org.qosp.notes.di.RepositoryModule
import org.qosp.notes.di.UtilModule
import org.qosp.notes.ui.UIModule
import java.util.concurrent.TimeUnit

@OptIn(KoinExperimentalAPI::class)
class App : Application(), ImageLoaderFactory, Configuration.Provider, KoinStartup {
    val syncingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workerFactory = KoinWorkerFactory()

    override val workManagerConfiguration: Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(applicationContext).maxSizePercent(0.05).build()
            }
            .diskCache(
                DiskCache.Builder().directory(applicationContext.cacheDir.resolve("img_cache"))
                    .maxSizePercent(0.02).build()
            )
            .components {
                if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        super.onCreate()

        createNotificationChannels()
        enqueueWorkers()
    }

    override fun onKoinStartup() = koinConfiguration {
        androidLogger()
        androidContext(this@App)
        modules(
            listOf(
                DatabaseModule().module,
                RepositoryModule().module,
                PreferencesModule().module,
                UtilModule().module,
                NextcloudModule().module,
                UIModule().module,
                MarkwonModule().module,
            )
        )
    }

    private fun createNotificationChannels() {
        if (SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return

        listOf(
            NotificationChannel(
                REMINDERS_CHANNEL_ID,
                getString(R.string.notifications_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH
            ),
            NotificationChannel(
                BACKUPS_CHANNEL_ID,
                getString(R.string.notifications_channel_backups),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                getString(R.string.notifications_channel_playback),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        ).forEach { notificationManager.createNotificationChannel(it) }
    }

    private fun enqueueWorkers() {
        val workManager = WorkManager.getInstance(this)

        val periodicRequests = listOf(
            "BIN_CLEAN" to PeriodicWorkRequestBuilder<BinCleaningWorker>(5, TimeUnit.HOURS)
                .build(),
            "SYNC" to PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )

        periodicRequests.forEach { (name, request) ->
            workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()

        if (SDK_INT >= 31) {
            vmPolicyBuilder.detectUnsafeIntentLaunch()
        }

        StrictMode.setVmPolicy(vmPolicyBuilder.build())
    }

    companion object {
        const val MEDIA_FOLDER = "media"
        const val REMINDERS_CHANNEL_ID = "REMINDERS_CHANNEL"
        const val BACKUPS_CHANNEL_ID = "BACKUPS_CHANNEL"
        const val PLAYBACK_CHANNEL_ID = "PLAYBACK_CHANNEL"
    }
}
