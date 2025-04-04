package org.qosp.notes

import android.app.Application
import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.qosp.notes.data.AppDatabase

class TestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
    }
}

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        stopKoin() // Stop any existing Koin instance

        val testModule = module {
            single {
                MigrationTestHelper(
                    InstrumentationRegistry.getInstrumentation(),
                    AppDatabase::class.java.canonicalName,
                    androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
                )
            }
        }

        startKoin {
            androidContext(this@TestApplication)
            modules(testModule)
        }
    }
}
