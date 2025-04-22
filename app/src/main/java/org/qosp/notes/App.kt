package org.qosp.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.StrictMode
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
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
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {
    val syncingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltWorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder()
            .setWorkerFactory(EntryPoints.get(this, HiltWorkerFactoryEntryPoint::class.java).workerFactory())
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
        val vmPolicy = StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
        if (SDK_INT >= Build.VERSION_CODES.S) {
            vmPolicy.detectUnsafeIntentLaunch()
        }
        StrictMode.setVmPolicy(vmPolicy.build())
    }

    companion object {
        const val MEDIA_FOLDER = "media"
        const val REMINDERS_CHANNEL_ID = "REMINDERS_CHANNEL"
        const val BACKUPS_CHANNEL_ID = "BACKUPS_CHANNEL"
        const val PLAYBACK_CHANNEL_ID = "PLAYBACK_CHANNEL"
    }
}
