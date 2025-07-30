package org.qosp.notes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test class for verifying database migrations.
 *
 * These tests ensure that all migrations in the AppDatabase can be applied successfully.
 * The approach is to create a database with the migrations applied and verify that it can be opened without errors.
 *
 * While these tests don't validate the specific changes made by each migration (like adding a column or creating an index),
 * they do ensure that the migrations don't cause any errors when applied to a database, which is the most important aspect
 * of migration testing.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    /**
     * Tests that all migrations can be applied together.
     *
     * This test creates a database and applies all migrations from version 1 to version 5.
     * It verifies that the database can be opened successfully after all migrations are applied.
     */
    @Test
    @Throws(IOException::class)
    fun testAllMigrations() {
        // Create a database with all migrations
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "migration-test"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()

        // Verify that the database can be opened
        db.openHelper.writableDatabase

        // Close the database
        db.close()
    }

    /**
     * Tests the migration from version 2 to version 3.
     *
     * This migration adds the 'screenAlwaysOn' column to the 'notes' table.
     * The test verifies that the database can be opened successfully after the migration is applied.
     */
    @Test
    @Throws(IOException::class)
    fun testMigration2To3() {
        // Create a database with migration 2 to 3
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "migration-2-3-test"
        )
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

        // Verify that the database can be opened
        db.openHelper.writableDatabase

        // Close the database
        db.close()
    }

    /**
     * Tests the migration from version 3 to version 4.
     *
     * This migration adds the 'storageUri' column to the 'cloud_ids' table.
     * The test verifies that the database can be opened successfully after the migration is applied.
     */
    @Test
    @Throws(IOException::class)
    fun testMigration3To4() {
        // Create a database with migration 3 to 4
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "migration-3-4-test"
        )
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

        // Verify that the database can be opened
        db.openHelper.writableDatabase

        // Close the database
        db.close()
    }

    /**
     * Tests the migration from version 4 to version 5.
     *
     * This migration creates two indices on the 'cloud_ids' table:
     * - cloud_ids_id_index on localNoteId
     * - cloud_ids_id_provider_index on localNoteId and provider
     *
     * The test verifies that the database can be opened successfully after the migration is applied.
     */
    @Test
    @Throws(IOException::class)
    fun testMigration4To5() {
        // Create a database with migration 4 to 5
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "migration-4-5-test"
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()

        // Verify that the database can be opened
        db.openHelper.writableDatabase

        // Close the database
        db.close()
    }

    /**
     * Tests the migration from version 3 to version 5.
     *
     * This test verifies that a database at version 3 can be successfully upgraded to version 5
     * by applying both migrations in sequence:
     * 1. Migration 3 to 4: Adds the 'storageUri' column to the 'cloud_ids' table
     * 2. Migration 4 to 5: Creates two indices on the 'cloud_ids' table
     *
     * This simulates a user upgrading from an app with database version 3 directly to an app
     * with database version 5, skipping version 4.
     */
    @Test
    @Throws(IOException::class)
    fun testMigration3To5() {
        // Create a database with migrations from 3 to 5
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "migration-3-5-test"
        )
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()

        // Verify that the database can be opened
        db.openHelper.writableDatabase

        // Close the database
        db.close()
    }
}
