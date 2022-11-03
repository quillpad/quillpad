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
import coil.util.CoilUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {
    val syncingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .crossfade(true)
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(CoilUtils.createDefaultCache(applicationContext))
                    .build()
            }
            .componentRegistry {
                if (SDK_INT >= 28) add(ImageDecoderDecoder(applicationContext)) else add(GifDecoder())
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
        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return

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
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
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
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
    }

    companion object {
        const val MEDIA_FOLDER = "media"
        const val REMINDERS_CHANNEL_ID = "REMINDERS_CHANNEL"
        const val BACKUPS_CHANNEL_ID = "BACKUPS_CHANNEL"
        const val PLAYBACK_CHANNEL_ID = "PLAYBACK_CHANNEL"
    }
}
