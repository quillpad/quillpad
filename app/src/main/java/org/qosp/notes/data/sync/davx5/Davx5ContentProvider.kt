package org.qosp.notes.data.sync.davx5

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri

class Davx5ContentProvider : ContentProvider() {

    private lateinit var dbHelper: Davx5DatabaseHelper

    companion object {
        private const val CODE_COLLECTION = 100
        private const val CODE_COLLECTION_ID = 101
        private const val CODE_ICALOBJECT = 200
        private const val CODE_ICALOBJECT_ID = 201
        private const val CODE_CATEGORY = 300
        private const val CODE_CATEGORY_ID = 301
        private const val CODE_ATTACHMENT = 400
        private const val CODE_ATTACHMENT_ID = 401
        private const val CODE_ALARM = 500
        private const val CODE_ALARM_ID = 501

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(Davx5Contract.AUTHORITY, Davx5Contract.PATH_COLLECTION, CODE_COLLECTION)
            addURI(Davx5Contract.AUTHORITY, "${Davx5Contract.PATH_COLLECTION}/#", CODE_COLLECTION_ID)
            addURI(Davx5Contract.AUTHORITY, Davx5Contract.PATH_ICALOBJECT, CODE_ICALOBJECT)
            addURI(Davx5Contract.AUTHORITY, "${Davx5Contract.PATH_ICALOBJECT}/#", CODE_ICALOBJECT_ID)
            addURI(Davx5Contract.AUTHORITY, Davx5Contract.PATH_CATEGORY, CODE_CATEGORY)
            addURI(Davx5Contract.AUTHORITY, "${Davx5Contract.PATH_CATEGORY}/#", CODE_CATEGORY_ID)
            addURI(Davx5Contract.AUTHORITY, Davx5Contract.PATH_ATTACHMENT, CODE_ATTACHMENT)
            addURI(Davx5Contract.AUTHORITY, "${Davx5Contract.PATH_ATTACHMENT}/#", CODE_ATTACHMENT_ID)
            addURI(Davx5Contract.AUTHORITY, Davx5Contract.PATH_ALARM, CODE_ALARM)
            addURI(Davx5Contract.AUTHORITY, "${Davx5Contract.PATH_ALARM}/#", CODE_ALARM_ID)
        }
    }

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        dbHelper = Davx5DatabaseHelper(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = dbHelper.readableDatabase
        val cursor: Cursor? = when (uriMatcher.match(uri)) {
            CODE_COLLECTION -> db.query(
                Davx5Contract.Collection.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )

            CODE_COLLECTION_ID -> {
                val id = parseValidId(uri) ?: return null
                val where = appendIdSelection(selection, Davx5Contract.Collection._ID)
                val args = appendIdSelectionArgs(selectionArgs, id)
                db.query(Davx5Contract.Collection.TABLE_NAME, projection, where, args, null, null, sortOrder)
            }

            CODE_ICALOBJECT -> db.query(
                Davx5Contract.ICalObject.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )

            CODE_ICALOBJECT_ID -> {
                val id = parseValidId(uri) ?: return null
                val where = appendIdSelection(selection, Davx5Contract.ICalObject._ID)
                val args = appendIdSelectionArgs(selectionArgs, id)
                db.query(Davx5Contract.ICalObject.TABLE_NAME, projection, where, args, null, null, sortOrder)
            }

            CODE_CATEGORY -> db.query(
                Davx5Contract.Category.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )

            CODE_ATTACHMENT -> db.query(
                Davx5Contract.Attachment.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )

            CODE_ALARM -> db.query(
                Davx5Contract.Alarm.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
            )

            else -> null
        }
        cursor?.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_COLLECTION -> "vnd.android.cursor.dir/vnd.io.github.quillpad.collection"
            CODE_COLLECTION_ID -> "vnd.android.cursor.item/vnd.io.github.quillpad.collection"
            CODE_ICALOBJECT -> "vnd.android.cursor.dir/vnd.io.github.quillpad.icalobject"
            CODE_ICALOBJECT_ID -> "vnd.android.cursor.item/vnd.io.github.quillpad.icalobject"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (values == null || values.size() == 0) return null
        val db = dbHelper.writableDatabase
        val match = uriMatcher.match(uri)
        val (tableName, baseUri) = when (match) {
            CODE_COLLECTION -> Davx5Contract.Collection.TABLE_NAME to Davx5Contract.CONTENT_URI_COLLECTION
            CODE_ICALOBJECT -> Davx5Contract.ICalObject.TABLE_NAME to Davx5Contract.CONTENT_URI_ICALOBJECT
            CODE_CATEGORY -> Davx5Contract.Category.TABLE_NAME to Uri.withAppendedPath(Davx5Contract.BASE_CONTENT_URI, Davx5Contract.PATH_CATEGORY)
            CODE_ATTACHMENT -> Davx5Contract.Attachment.TABLE_NAME to Uri.withAppendedPath(Davx5Contract.BASE_CONTENT_URI, Davx5Contract.PATH_ATTACHMENT)
            CODE_ALARM -> Davx5Contract.Alarm.TABLE_NAME to Uri.withAppendedPath(Davx5Contract.BASE_CONTENT_URI, Davx5Contract.PATH_ALARM)
            else -> return null
        }

        val conflictAlgorithm = if (match == CODE_COLLECTION) {
            SQLiteDatabase.CONFLICT_REPLACE
        } else {
            SQLiteDatabase.CONFLICT_ABORT
        }
        val rowId = try {
            db.insertWithOnConflict(tableName, null, values, conflictAlgorithm)
        } catch (_: Exception) {
            db.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        if (rowId != -1L) {
            val insertedUri = ContentUris.withAppendedId(baseUri, rowId)
            context?.contentResolver?.notifyChange(uri, null)
            return insertedUri
        }
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val db = dbHelper.writableDatabase
        val rowsUpdated = when (uriMatcher.match(uri)) {
            CODE_COLLECTION -> db.update(Davx5Contract.Collection.TABLE_NAME, values, selection, selectionArgs)
            CODE_COLLECTION_ID -> {
                val id = parseValidId(uri) ?: return 0
                val where = appendIdSelection(selection, Davx5Contract.Collection._ID)
                val args = appendIdSelectionArgs(selectionArgs, id)
                db.update(Davx5Contract.Collection.TABLE_NAME, values, where, args)
            }

            CODE_ICALOBJECT -> db.update(Davx5Contract.ICalObject.TABLE_NAME, values, selection, selectionArgs)
            CODE_ICALOBJECT_ID -> {
                val id = parseValidId(uri) ?: return 0
                val where = appendIdSelection(selection, Davx5Contract.ICalObject._ID)
                val args = appendIdSelectionArgs(selectionArgs, id)
                db.update(Davx5Contract.ICalObject.TABLE_NAME, values, where, args)
            }

            CODE_CATEGORY -> db.update(Davx5Contract.Category.TABLE_NAME, values, selection, selectionArgs)
            CODE_ATTACHMENT -> db.update(Davx5Contract.Attachment.TABLE_NAME, values, selection, selectionArgs)
            CODE_ALARM -> db.update(Davx5Contract.Alarm.TABLE_NAME, values, selection, selectionArgs)
            else -> 0
        }
        if (rowsUpdated > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsUpdated
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = dbHelper.writableDatabase
        val rowsDeleted = when (uriMatcher.match(uri)) {
            CODE_COLLECTION -> db.delete(Davx5Contract.Collection.TABLE_NAME, selection, selectionArgs)
            CODE_COLLECTION_ID -> {
                val id = parseValidId(uri) ?: return 0
                val where = appendIdSelection(selection, Davx5Contract.Collection._ID)
                val args = appendIdSelectionArgs(selectionArgs, id)
                db.delete(Davx5Contract.Collection.TABLE_NAME, where, args)
            }

            CODE_ICALOBJECT -> db.delete(Davx5Contract.ICalObject.TABLE_NAME, selection, selectionArgs)
            CODE_ICALOBJECT_ID -> {
                val id = parseValidId(uri) ?: return 0
                val where = appendIdSelection(selection, Davx5Contract.ICalObject._ID)
                val args = appendIdSelectionArgs(selectionArgs, id)
                db.delete(Davx5Contract.ICalObject.TABLE_NAME, where, args)
            }

            CODE_CATEGORY -> db.delete(Davx5Contract.Category.TABLE_NAME, selection, selectionArgs)
            CODE_ATTACHMENT -> db.delete(Davx5Contract.Attachment.TABLE_NAME, selection, selectionArgs)
            CODE_ALARM -> db.delete(Davx5Contract.Alarm.TABLE_NAME, selection, selectionArgs)
            else -> 0
        }
        if (rowsDeleted > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsDeleted
    }

    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val results = super.applyBatch(operations)
            db.setTransactionSuccessful()
            return results
        } finally {
            db.endTransaction()
        }
    }

    private fun parseValidId(uri: Uri): String? {
        return uri.lastPathSegment?.toLongOrNull()?.takeIf { it >= 0L }?.toString()
    }

    private fun appendIdSelection(selection: String?, idColumn: String): String {
        return if (selection.isNullOrEmpty()) {
            "$idColumn = ?"
        } else {
            "($selection) AND $idColumn = ?"
        }
    }

    private fun appendIdSelectionArgs(args: Array<out String>?, id: String): Array<String> {
        return if (args == null || args.isEmpty()) {
            arrayOf(id)
        } else {
            args.toMutableList().apply { add(id) }.toTypedArray()
        }
    }
}
