package org.qosp.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.StrictMode
import android.util.Log
import androidx.core.content.ContextCompat
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
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker
import org.qosp.notes.di.MarkwonModule
import org.qosp.notes.di.NextcloudModule
import org.qosp.notes.di.PreferencesModule
import org.qosp.notes.di.RepositoryModule
import org.qosp.notes.di.SyncModule
import org.qosp.notes.di.UIModule
import org.qosp.notes.di.UtilModule
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {

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

        startKoin {
            androidLogger()
            androidContext(this@App)
            workManagerFactory()
            modules(
                listOf(
                    MarkwonModule.markwonModule,
                    NextcloudModule.nextcloudModule,
                    PreferencesModule.prefModule,
                    RepositoryModule.repoModule,
                    UIModule.uiModule,
                    UtilModule.utilModule,
                    SyncModule.syncModule,
                )
            )
        }

        createNotificationChannels()
        enqueueWorkers()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Log.d("App", "attachBaseContext: Initializing ACRA")
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            reportContent = listOf(
                ReportField.REPORT_ID,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.PRODUCT,
                ReportField.BRAND,
                ReportField.PHONE_MODEL,
                ReportField.BUILD_CONFIG,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.USER_COMMENT,
                ReportField.USER_APP_START_DATE,
                ReportField.USER_CRASH_DATE,
                ReportField.LOGCAT,
            )
            logcatFilterByPid = true
            applicationLogFileLines = 500
            mailSender {
                mailTo = getString(R.string.error_report_email)
                reportAsFile = true
                reportFileName = "error_report.json"
            }
        }
        ACRA.errorReporter.setEnabled(false)
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
