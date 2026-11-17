package org.qosp.notes.data.sync.davx5

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Davx5DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ${Davx5Contract.Collection.TABLE_NAME} (
                ${Davx5Contract.Collection._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Davx5Contract.Collection.ACCOUNT_NAME} TEXT,
                ${Davx5Contract.Collection.ACCOUNT_TYPE} TEXT,
                ${Davx5Contract.Collection.URL} TEXT,
                ${Davx5Contract.Collection.DISPLAY_NAME} TEXT,
                ${Davx5Contract.Collection.DESCRIPTION} TEXT,
                ${Davx5Contract.Collection.COLOR} INTEGER,
                ${Davx5Contract.Collection.SUPPORTS_VEVENT} INTEGER DEFAULT 0,
                ${Davx5Contract.Collection.SUPPORTS_VTODO} INTEGER DEFAULT 0,
                ${Davx5Contract.Collection.SUPPORTS_VJOURNAL} INTEGER DEFAULT 1,
                ${Davx5Contract.Collection.SYNC_VERSION} INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${Davx5Contract.ICalObject.TABLE_NAME} (
                ${Davx5Contract.ICalObject._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Davx5Contract.ICalObject.COLLECTION_ID} INTEGER,
                ${Davx5Contract.ICalObject.UID} TEXT UNIQUE,
                ${Davx5Contract.ICalObject.SUMMARY} TEXT,
                ${Davx5Contract.ICalObject.DESCRIPTION} TEXT,
                ${Davx5Contract.ICalObject.CREATED} INTEGER,
                ${Davx5Contract.ICalObject.LAST_MODIFIED} INTEGER,
                ${Davx5Contract.ICalObject.DTSTART} INTEGER,
                ${Davx5Contract.ICalObject.STATUS} TEXT,
                ${Davx5Contract.ICalObject.CLASSIFICATION} TEXT,
                ${Davx5Contract.ICalObject.COLOR} INTEGER,
                ${Davx5Contract.ICalObject.DIRTY} INTEGER DEFAULT 0,
                ${Davx5Contract.ICalObject.DELETED} INTEGER DEFAULT 0,
                ${Davx5Contract.ICalObject.FILENAME} TEXT,
                ${Davx5Contract.ICalObject.ETAG} TEXT,
                ${Davx5Contract.ICalObject.SCHEDULE_TAG} TEXT,
                ${Davx5Contract.ICalObject.FLAGS} INTEGER DEFAULT 0,
                ${Davx5Contract.ICalObject.COMPONENT} TEXT DEFAULT 'VJOURNAL'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${Davx5Contract.Category.TABLE_NAME} (
                ${Davx5Contract.Category._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Davx5Contract.Category.ICAL_OBJECT_ID} INTEGER,
                ${Davx5Contract.Category.TEXT} TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${Davx5Contract.Attachment.TABLE_NAME} (
                ${Davx5Contract.Attachment._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Davx5Contract.Attachment.ICAL_OBJECT_ID} INTEGER,
                ${Davx5Contract.Attachment.URI} TEXT,
                ${Davx5Contract.Attachment.FILENAME} TEXT,
                ${Davx5Contract.Attachment.FMTTYPE} TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${Davx5Contract.Alarm.TABLE_NAME} (
                ${Davx5Contract.Alarm._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Davx5Contract.Alarm.ICAL_OBJECT_ID} INTEGER,
                ${Davx5Contract.Alarm.ACTION} TEXT,
                ${Davx5Contract.Alarm.TRIGGER_TIME} INTEGER
            )
            """.trimIndent()
        )

        // Seed a default Local collection for VJOURNAL notes
        db.execSQL(
            """
            INSERT OR IGNORE INTO ${Davx5Contract.Collection.TABLE_NAME} (
                ${Davx5Contract.Collection._ID},
                ${Davx5Contract.Collection.ACCOUNT_NAME},
                ${Davx5Contract.Collection.ACCOUNT_TYPE},
                ${Davx5Contract.Collection.URL},
                ${Davx5Contract.Collection.DISPLAY_NAME},
                ${Davx5Contract.Collection.SUPPORTS_VJOURNAL}
            ) VALUES (0, 'Local', '${Davx5Contract.DAVX5_ACCOUNT_TYPE}', 'davx5://local', 'Local', 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO ${Davx5Contract.Collection.TABLE_NAME} (
                ${Davx5Contract.Collection._ID},
                ${Davx5Contract.Collection.ACCOUNT_NAME},
                ${Davx5Contract.Collection.ACCOUNT_TYPE},
                ${Davx5Contract.Collection.URL},
                ${Davx5Contract.Collection.DISPLAY_NAME},
                ${Davx5Contract.Collection.SUPPORTS_VJOURNAL}
            ) VALUES (1, 'Local', '${Davx5Contract.DAVX5_ACCOUNT_TYPE}', 'davx5://default/local', 'Local', 1)
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "UPDATE ${Davx5Contract.Collection.TABLE_NAME} SET ${Davx5Contract.Collection.DISPLAY_NAME} = 'Local', ${Davx5Contract.Collection.ACCOUNT_NAME} = 'Local' WHERE ${Davx5Contract.Collection._ID} IN (0, 1) AND ${Davx5Contract.Collection.DISPLAY_NAME} = 'Notes'"
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO ${Davx5Contract.Collection.TABLE_NAME} (
                    ${Davx5Contract.Collection._ID},
                    ${Davx5Contract.Collection.ACCOUNT_NAME},
                    ${Davx5Contract.Collection.ACCOUNT_TYPE},
                    ${Davx5Contract.Collection.URL},
                    ${Davx5Contract.Collection.DISPLAY_NAME},
                    ${Davx5Contract.Collection.SUPPORTS_VJOURNAL}
                ) VALUES (0, 'Local', '${Davx5Contract.DAVX5_ACCOUNT_TYPE}', 'davx5://local', 'Local', 1)
                """.trimIndent()
            )
        }
    }

    companion object {
        const val DB_NAME = "davx5_notes_sync.db"
        const val DB_VERSION = 2
    }
}
