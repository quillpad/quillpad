package org.qosp.notes

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.logger.Level
import org.koin.dsl.koinConfiguration
import org.qosp.notes.di.MarkwonModule
import org.qosp.notes.di.NextcloudModule
import org.qosp.notes.di.PreferencesModule
import org.qosp.notes.di.RepositoryModule
import org.qosp.notes.di.TestUtilModule
import org.qosp.notes.di.UIModule

class TestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
    }
}

@OptIn(KoinExperimentalAPI::class)
class TestApplication : Application(), KoinStartup {

    override fun onKoinStartup() = koinConfiguration {
        androidLogger(level = Level.DEBUG)
        androidContext(this@TestApplication)
        modules(
            TestUtilModule.module,
            RepositoryModule.repoModule,
            PreferencesModule.prefModule,
            NextcloudModule.nextcloudModule,
            UIModule.uiModule,
            MarkwonModule.markwonModule,
        )
    }
}
