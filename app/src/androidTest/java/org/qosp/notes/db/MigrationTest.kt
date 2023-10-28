package org.qosp.notes.db;

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.impl.WorkDatabaseMigrations.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.data.AppDatabase
import java.io.IOException

@RunWith(AndroidJUnit4ClassRunner::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. insert some data using SQL queries.
            // You cannot use DAO classes because they expect the latest schema.
            execSQL(
                """
                insert into cloud_ids(mappingId, localNoteId, remoteNoteId, provider, isDeletedLocally, isBeingUpdated)
                values ( 1, 33, 44, "nextcloud", 0, 0 );
            """.trimIndent()
            )

            //     val mappingId: Long = 0L,
            //    val localNoteId: Long,
            //    val remoteNoteId: Long?,
            //    val provider: CloudService?,
            //    val extras: String?,
            //    val isDeletedLocally: Boolean,
            //    val isBeingUpdated: Boolean = false,
            // Prepare for the next version.
            close()
        }


        // Re-open the database with version 2 and provide
        // MIGRATION_1_2 as the migration process.
//        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // MigrationTestHelper automatically verifies the schema changes,
        // but you need to validate that the data was migrated properly.

    }
}
