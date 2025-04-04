package org.qosp.notes

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.data.AppDatabase
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest : KoinComponent {
    private val testDb = "migration-test"

    // Array of all migrations.
    private val ALL_MIGRATIONS = arrayOf(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
    )

    // Inject MigrationTestHelper from Koin
    private val helper: MigrationTestHelper by inject()

    @get:Rule
    val helperRule: MigrationTestHelper
        get() = helper

    @Test
    @Throws(IOException::class)
    fun migrateAlreadyIn3() {
        // Create the earliest version of the database.
        helper.createDatabase(testDb, 3).apply {
            close()
        }

        // Open latest version of the database. Room validates the schema
        // once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            testDb
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            openHelper.writableDatabase.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2to3() {
        // Create the earliest version of the database.
        helper.createDatabase(testDb, 2).apply {
            close()
        }

        // Open latest version of the database. Room validates the schema
        // once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            testDb
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            openHelper.writableDatabase.close()
        }
    }
}
