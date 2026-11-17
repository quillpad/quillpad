package org.qosp.notes.data.sync.davx5

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.preferences.CloudService
import java.util.TimeZone
import java.util.UUID

class Davx5Backend(
    private val context: Context,
    private val config: Davx5Config,
) : ISyncBackend {

    private val tag = "Davx5Backend"
    override val type: CloudService = CloudService.DAVX5

    companion object {
        private const val MASS_DELETE_CIRCUIT_BREAKER_THRESHOLD = 10
    }

    override suspend fun isAvailable(): AvailabilityStatus {
        return AvailabilityStatus.Available
    }

    private fun isRemoteCalendarSelected(): Boolean {
        if (config.collectionId <= 0L) return false
        if (!Davx5Helper.hasReadCalendarPermission(context)) return false
        return Davx5Helper.getDavx5Calendars(context, config.accountName.ifEmpty { null }).any {
            it.id == config.collectionId &&
                it.accountName != "Local" &&
                it.accountName != "DAVx5" &&
                (config.accountName.isEmpty() || it.accountName == config.accountName)
        }
    }

    private fun insertIntoCalendarContract(
        uid: String,
        title: String,
        description: String,
        createdMs: Long,
        modifiedMs: Long
    ): Long? {
        if (!Davx5Helper.hasWriteCalendarPermission(context)) return null
        val pointMs = when {
            modifiedMs > 0L -> modifiedMs
            createdMs > 0L -> createdMs
            else -> System.currentTimeMillis()
        }
        val tz = TimeZone.getDefault().id ?: "UTC"

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, config.collectionId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, pointMs)
            put(CalendarContract.Events.DTEND, pointMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            put(CalendarContract.Events.UID_2445, uid)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            }
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(tag, "Insert with UID_2445 failed, retrying without UID_2445", e)
            try {
                values.remove(CalendarContract.Events.UID_2445)
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            } catch (e2: Exception) {
                Log.e(tag, "Failed to insert note into CalendarContract.Events", e2)
                null
            }
        }
    }

    override suspend fun createNote(note: Note): SyncNote = withContext(Dispatchers.IO) {
        val uid = UUID.randomUUID().toString()
        val storableContent = note.toStorableContent()
        val createdMs = note.creationDate * 1000L
        val modifiedMs = note.modifiedDate * 1000L

        val calEventId = if (isRemoteCalendarSelected()) {
            insertIntoCalendarContract(uid, note.title, storableContent, createdMs, modifiedMs)
        } else {
            null
        }

        val values = ContentValues().apply {
            put(Davx5Contract.ICalObject.COLLECTION_ID, config.collectionId)
            put(Davx5Contract.ICalObject.UID, uid)
            put(Davx5Contract.ICalObject.SUMMARY, note.title)
            put(Davx5Contract.ICalObject.DESCRIPTION, storableContent)
            put(Davx5Contract.ICalObject.CREATED, createdMs)
            put(Davx5Contract.ICalObject.LAST_MODIFIED, modifiedMs)
            put(Davx5Contract.ICalObject.DIRTY, 1)
            put(Davx5Contract.ICalObject.DELETED, 0)
            put(Davx5Contract.ICalObject.COMPONENT, "VJOURNAL")
            if (calEventId != null) {
                put(Davx5Contract.ICalObject.FILENAME, "cal_${config.collectionId}_$calEventId")
            }
        }

        val uri = context.contentResolver.insert(Davx5Contract.CONTENT_URI_ICALOBJECT, values)
        val rowId = calEventId ?: uri?.lastPathSegment?.toLongOrNull() ?: 0L

        Davx5Helper.requestSync(context, config.accountName.ifEmpty { null }, config.collectionId)

        SyncNote(
            id = rowId,
            idStr = uid,
            content = storableContent,
            title = note.title,
            lastModified = note.modifiedDate,
        )
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping = withContext(Dispatchers.IO) {
        val uid = mapping.storageUri
        if (uid.isNullOrEmpty()) {
            Log.w(tag, "Cannot update note without storageUri/uid: ${note.title}")
            return@withContext mapping
        }

        val storableContent = note.toStorableContent()
        val createdMs = note.creationDate * 1000L
        val modifiedMs = note.modifiedDate * 1000L
        var finalRemoteId = mapping.remoteNoteId

        if (isRemoteCalendarSelected() && Davx5Helper.hasWriteCalendarPermission(context)) {
            val pointMs = when {
                modifiedMs > 0L -> modifiedMs
                createdMs > 0L -> createdMs
                else -> System.currentTimeMillis()
            }
            val eventValues = ContentValues().apply {
                put(CalendarContract.Events.TITLE, note.title)
                put(CalendarContract.Events.DESCRIPTION, storableContent)
                put(CalendarContract.Events.DTSTART, pointMs)
                put(CalendarContract.Events.DTEND, pointMs)
            }
            val remoteId = mapping.remoteNoteId ?: -1L
            val updatedByUid = try {
                context.contentResolver.update(
                    CalendarContract.Events.CONTENT_URI,
                    eventValues,
                    "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.UID_2445} = ?",
                    arrayOf(config.collectionId.toString(), uid)
                )
            } catch (_: Exception) {
                0
            }
            val updatedRows = if (updatedByUid > 0 || remoteId <= 0L) {
                updatedByUid
            } else {
                val (fallbackWhere, fallbackArgs) = buildFallbackEventSelection(remoteId)
                try {
                    context.contentResolver.update(
                        CalendarContract.Events.CONTENT_URI,
                        eventValues,
                        fallbackWhere,
                        fallbackArgs
                    )
                } catch (_: Exception) {
                    0
                }
            }

            if (updatedRows == 0) {
                val newEventId = insertIntoCalendarContract(uid, note.title, storableContent, createdMs, modifiedMs)
                if (newEventId != null) {
                    finalRemoteId = newEventId
                }
            }
        }

        val values = ContentValues().apply {
            put(Davx5Contract.ICalObject.COLLECTION_ID, config.collectionId)
            put(Davx5Contract.ICalObject.SUMMARY, note.title)
            put(Davx5Contract.ICalObject.DESCRIPTION, storableContent)
            put(Davx5Contract.ICalObject.LAST_MODIFIED, modifiedMs)
            put(Davx5Contract.ICalObject.DIRTY, 1)
            put(Davx5Contract.ICalObject.DELETED, 0)
            if (finalRemoteId != null && isRemoteCalendarSelected()) {
                put(Davx5Contract.ICalObject.FILENAME, "cal_${config.collectionId}_$finalRemoteId")
            }
        }

        val localUpdated = context.contentResolver.update(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            values,
            "${Davx5Contract.ICalObject.UID} = ?",
            arrayOf(uid)
        )
        if (localUpdated == 0) {
            values.put(Davx5Contract.ICalObject.UID, uid)
            values.put(Davx5Contract.ICalObject.CREATED, createdMs)
            values.put(Davx5Contract.ICalObject.COMPONENT, "VJOURNAL")
            context.contentResolver.insert(Davx5Contract.CONTENT_URI_ICALOBJECT, values)
        }

        Davx5Helper.requestSync(context, config.accountName.ifEmpty { null }, config.collectionId)
        mapping.copy(remoteNoteId = finalRemoteId)
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean = withContext(Dispatchers.IO) {
        val uid = mapping.storageUri ?: return@withContext true

        if (isRemoteCalendarSelected() && Davx5Helper.hasWriteCalendarPermission(context)) {
            val remoteId = mapping.remoteNoteId ?: -1L
            val deletedByUid = try {
                context.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.UID_2445} = ?",
                    arrayOf(config.collectionId.toString(), uid)
                )
            } catch (_: Exception) {
                0
            }
            if (deletedByUid == 0 && remoteId > 0L) {
                val (fallbackWhere, fallbackArgs) = buildFallbackEventSelection(remoteId)
                try {
                    context.contentResolver.delete(
                        CalendarContract.Events.CONTENT_URI,
                        fallbackWhere,
                        fallbackArgs
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to delete from CalendarContract.Events", e)
                }
            }
        }

        val values = ContentValues().apply {
            put(Davx5Contract.ICalObject.DIRTY, 1)
            put(Davx5Contract.ICalObject.DELETED, 1)
        }

        context.contentResolver.update(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            values,
            "${Davx5Contract.ICalObject.UID} = ?",
            arrayOf(uid)
        )

        Davx5Helper.requestSync(context, config.accountName.ifEmpty { null }, config.collectionId)
        true
    }

    private fun buildFallbackEventSelection(remoteId: Long): Pair<String, Array<String>> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events._ID} = ? AND (${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? OR ${CalendarContract.Events.UID_2445} IS NULL)" to
                arrayOf(config.collectionId.toString(), remoteId.toString(), context.packageName)
        } else {
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.UID_2445} IS NULL" to
                arrayOf(config.collectionId.toString(), remoteId.toString())
        }
    }

    override suspend fun getNote(mapping: IdMapping): SyncNote? = withContext(Dispatchers.IO) {
        val uid = mapping.storageUri ?: return@withContext null

        if (isRemoteCalendarSelected()) {
            val remoteId = mapping.remoteNoteId ?: -1L
            try {
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND
                )
                val cursor = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0 AND (${CalendarContract.Events.UID_2445} = ? OR ${CalendarContract.Events._ID} = ?)",
                    arrayOf(config.collectionId.toString(), uid, remoteId.toString()),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                        val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: ""
                        val desc = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: ""
                        val dtEnd = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                        val dtStart = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                        val mod = when {
                            dtEnd > 0L -> dtEnd / 1000L
                            dtStart > 0L -> dtStart / 1000L
                            else -> System.currentTimeMillis() / 1000L
                        }
                        return@withContext SyncNote(id = id, idStr = uid, content = desc, title = title, lastModified = mod)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error querying CalendarContract.Events in getNote", e)
            }
        }

        val cursor = context.contentResolver.query(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            null,
            "${Davx5Contract.ICalObject.UID} = ? AND ${Davx5Contract.ICalObject.DELETED} = 0",
            arrayOf(uid),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(Davx5Contract.ICalObject._ID))
                val summary = it.getString(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.SUMMARY)) ?: ""
                val desc = it.getString(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.DESCRIPTION)) ?: ""
                val mod = it.getLong(it.getColumnIndexOrThrow(Davx5Contract.ICalObject.LAST_MODIFIED)) / 1000L
                SyncNote(id = id, idStr = uid, content = desc, title = summary, lastModified = mod)
            } else {
                null
            }
        }
    }

    private data class CachedICalRow(
        val id: Long,
        val uid: String,
        val summary: String,
        val description: String,
        val createdMs: Long,
        val lastModifiedMs: Long,
        val filename: String?
    )

    override suspend fun getAll(): List<SyncNote>? = withContext(Dispatchers.IO) {
        val cachedRows = mutableListOf<CachedICalRow>()
        val localCursor = context.contentResolver.query(
            Davx5Contract.CONTENT_URI_ICALOBJECT,
            null,
            "${Davx5Contract.ICalObject.DELETED} = 0 AND (${Davx5Contract.ICalObject.COLLECTION_ID} = ? OR ${Davx5Contract.ICalObject.COLLECTION_ID} = 0)",
            arrayOf(config.collectionId.toString()),
            null
        )
        localCursor?.use {
            val idCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject._ID)
            val uidCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject.UID)
            val summaryCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject.SUMMARY)
            val descCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject.DESCRIPTION)
            val createdCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject.CREATED)
            val modCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject.LAST_MODIFIED)
            val fileCol = it.getColumnIndexOrThrow(Davx5Contract.ICalObject.FILENAME)

            while (it.moveToNext()) {
                val uid = it.getString(uidCol) ?: continue
                cachedRows.add(
                    CachedICalRow(
                        id = it.getLong(idCol),
                        uid = uid,
                        summary = it.getString(summaryCol) ?: "",
                        description = it.getString(descCol) ?: "",
                        createdMs = it.getLong(createdCol),
                        lastModifiedMs = it.getLong(modCol),
                        filename = it.getString(fileCol)
                    )
                )
            }
        }

        if (!isRemoteCalendarSelected()) {
            return@withContext cachedRows.map { row ->
                SyncNote(
                    id = row.id,
                    idStr = row.uid,
                    content = row.description,
                    title = row.summary,
                    lastModified = row.lastModifiedMs / 1000L
                )
            }
        }

        // Sync with the selected CalDAV calendar in CalendarContract.Events
        val prefix = "cal_${config.collectionId}_"
        val cachedByUid = cachedRows.associateBy { it.uid }
        val cachedByMarker = cachedRows.filter { it.filename != null }.associateBy { it.filename!! }
        val matchedUids = mutableSetOf<String>()
        val resultNotes = mutableListOf<SyncNote>()

        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.UID_2445,
                CalendarContract.Events._SYNC_ID
            )
            val calCursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(config.collectionId.toString()),
                null
            )

            calCursor?.use {
                val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleCol = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descCol = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val dtStartCol = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val dtEndCol = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val uid2445Col = it.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
                val syncIdCol = it.getColumnIndexOrThrow(CalendarContract.Events._SYNC_ID)

                while (it.moveToNext()) {
                    val eventId = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: ""
                    val desc = it.getString(descCol) ?: ""
                    val dtStart = it.getLong(dtStartCol)
                    val dtEnd = it.getLong(dtEndCol)
                    val marker = "$prefix$eventId"

                    val rawUid = it.getString(uid2445Col)?.takeIf { u -> u.isNotBlank() }
                        ?: it.getString(syncIdCol)?.takeIf { s -> s.isNotBlank() }

                    val cached = (if (rawUid != null) cachedByUid[rawUid] else null)
                        ?: cachedByMarker[marker]

                    val uid = cached?.uid ?: rawUid ?: "davx5-cal-${config.collectionId}-$eventId"
                    matchedUids.add(uid)

                    val modMs: Long
                    if (cached == null) {
                        modMs = when {
                            dtEnd > 0L -> dtEnd
                            dtStart > 0L -> dtStart
                            else -> System.currentTimeMillis()
                        }
                        val insertValues = ContentValues().apply {
                            put(Davx5Contract.ICalObject.COLLECTION_ID, config.collectionId)
                            put(Davx5Contract.ICalObject.UID, uid)
                            put(Davx5Contract.ICalObject.SUMMARY, title)
                            put(Davx5Contract.ICalObject.DESCRIPTION, desc)
                            put(Davx5Contract.ICalObject.CREATED, if (dtStart > 0L) dtStart else modMs)
                            put(Davx5Contract.ICalObject.LAST_MODIFIED, modMs)
                            put(Davx5Contract.ICalObject.DIRTY, 0)
                            put(Davx5Contract.ICalObject.DELETED, 0)
                            put(Davx5Contract.ICalObject.COMPONENT, "VJOURNAL")
                            put(Davx5Contract.ICalObject.FILENAME, marker)
                        }
                        context.contentResolver.insert(Davx5Contract.CONTENT_URI_ICALOBJECT, insertValues)
                    } else if (cached.summary != title || cached.description != desc) {
                        // Modified remotely on CalDAV server via DAVx5
                        modMs = maxOf(System.currentTimeMillis(), cached.lastModifiedMs + 2000L)
                        val updateValues = ContentValues().apply {
                            put(Davx5Contract.ICalObject.COLLECTION_ID, config.collectionId)
                            put(Davx5Contract.ICalObject.SUMMARY, title)
                            put(Davx5Contract.ICalObject.DESCRIPTION, desc)
                            put(Davx5Contract.ICalObject.LAST_MODIFIED, modMs)
                            put(Davx5Contract.ICalObject.DIRTY, 0)
                            put(Davx5Contract.ICalObject.FILENAME, marker)
                        }
                        context.contentResolver.update(
                            Davx5Contract.CONTENT_URI_ICALOBJECT,
                            updateValues,
                            "${Davx5Contract.ICalObject.UID} = ?",
                            arrayOf(uid)
                        )
                    } else {
                        modMs = cached.lastModifiedMs
                    }

                    resultNotes.add(
                        SyncNote(
                            id = eventId,
                            idStr = uid,
                            content = desc,
                            title = title,
                            lastModified = modMs / 1000L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed querying CalendarContract.Events in getAll", e)
        }

        val previouslySyncedCount = cachedRows.count { it.filename != null && it.filename.startsWith(prefix) }
        if (previouslySyncedCount >= MASS_DELETE_CIRCUIT_BREAKER_THRESHOLD && matchedUids.isEmpty()) {
            Log.w(
                tag,
                "Aborted DAVx5 sync: $previouslySyncedCount previously synced notes exist locally, but CalendarContract returned 0 events."
            )
            return@withContext null
        }

        // Check cached rows not yet present in this CalDAV calendar
        for (row in cachedRows) {
            if (row.uid in matchedUids) continue

            if (row.filename == null || !row.filename.startsWith(prefix)) {
                // Note was created before this CalDAV calendar was selected, push it to CalendarContract now
                val newEventId = insertIntoCalendarContract(
                    uid = row.uid,
                    title = row.summary,
                    description = row.description,
                    createdMs = row.createdMs,
                    modifiedMs = row.lastModifiedMs
                )
                if (newEventId != null) {
                    val updateValues = ContentValues().apply {
                        put(Davx5Contract.ICalObject.COLLECTION_ID, config.collectionId)
                        put(Davx5Contract.ICalObject.FILENAME, "$prefix$newEventId")
                    }
                    context.contentResolver.update(
                        Davx5Contract.CONTENT_URI_ICALOBJECT,
                        updateValues,
                        "${Davx5Contract.ICalObject.UID} = ?",
                        arrayOf(row.uid)
                    )
                }
                resultNotes.add(
                    SyncNote(
                        id = newEventId ?: row.id,
                        idStr = row.uid,
                        content = row.description,
                        title = row.summary,
                        lastModified = row.lastModifiedMs / 1000L
                    )
                )
            } else {
                // Note was previously synced to this calendar and deleted remotely via DAVx5
                val deleteValues = ContentValues().apply {
                    put(Davx5Contract.ICalObject.DELETED, 1)
                    put(Davx5Contract.ICalObject.DIRTY, 0)
                }
                context.contentResolver.update(
                    Davx5Contract.CONTENT_URI_ICALOBJECT,
                    deleteValues,
                    "${Davx5Contract.ICalObject.UID} = ?",
                    arrayOf(row.uid)
                )
            }
        }

        Davx5Helper.requestSync(context, config.accountName.ifEmpty { null }, config.collectionId)
        resultNotes
    }
}
