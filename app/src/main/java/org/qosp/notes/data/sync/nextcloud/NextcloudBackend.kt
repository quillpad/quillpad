package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.asSyncNote
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.nextcloud.model.asNextcloudNote
import org.qosp.notes.preferences.CloudService
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class NextcloudBackend(
    private val apiProvider: NextcloudAPIProvider,
    private val config: NextcloudConfig,
    private val timeSource: TimeSource = TimeSource.Monotonic
) : ISyncBackend {

    private val tag = javaClass.simpleName
    override val type: CloudService = CloudService.NEXTCLOUD
    private val cacheExpiration = 30.seconds

    private var cachedStatus: AvailabilityStatus? = null
    private var cacheExpTime = timeSource.markNow()
    private val mutex = Mutex()

    override suspend fun isAvailable(): AvailabilityStatus = mutex.withLock {
        if (cachedStatus != null && !cacheExpTime.hasPassedNow()) {
            return@withLock cachedStatus!!
        }
        val status = try {
            val api = apiProvider.getAPI()
            val capabilities = api.getNotesCapabilities(config)
            if (capabilities != null) {
                AvailabilityStatus.Available
            } else {
                AvailabilityStatus.Unavailable("Nextcloud Notes API not available")
            }
        } catch (e: java.net.UnknownHostException) {
            AvailabilityStatus.Unavailable("Cannot reach Nextcloud server: ${config.remoteAddress}")
        } catch (e: java.net.SocketTimeoutException) {
            AvailabilityStatus.Unavailable("Connection to Nextcloud timed out")
        } catch (e: javax.net.ssl.SSLException) {
            AvailabilityStatus.Unavailable("SSL certificate error - enable 'Trust self-signed certificate' if needed")
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> AvailabilityStatus.Unavailable("Invalid credentials for Nextcloud")
                403 -> AvailabilityStatus.Unavailable("Access denied to Nextcloud")
                503 -> AvailabilityStatus.Unavailable("Nextcloud server is temporarily unavailable")
                else -> AvailabilityStatus.Unavailable("Nextcloud error: HTTP ${e.code()}")
            }
        } catch (e: Exception) {
            AvailabilityStatus.Unavailable("Cannot connect to Nextcloud: ${e.message ?: "Unknown error"}")
        }

        cachedStatus = status
        cacheExpTime = timeSource.markNow() + cacheExpiration
        return@withLock status
    }

    override suspend fun createNote(note: Note): SyncNote {
        Log.d(tag, "createNote() called with: note = ${note.title}")
        val api = apiProvider.getAPI()
        return api.createNote(note.asNextcloudNote(0, ""), config).asSyncNote()
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        Log.d(tag, "updateNote: ${note.title}")
        val api = apiProvider.getAPI()
        val nNote = note.asNextcloudNote(mapping.remoteNoteId, "")
        val updatedNote = api.updateNote(nNote, mapping.extras ?: "", config)
        return mapping.copy(remoteNoteId = updatedNote.id, extras = updatedNote.etag)
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean = try {
        // Delete the note on the server
        Log.d(tag, "deleteNote() called with: mapping = $mapping")
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        val api = apiProvider.getAPI()
        api.deleteNote(mapping.remoteNoteId, config)
        true
    } catch (_: Exception) {
        false
    }

    override suspend fun getNote(mapping: IdMapping): SyncNote? {
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        val api = apiProvider.getAPI()
        return api.getNote(mapping.remoteNoteId, config).asSyncNote()
    }

    override suspend fun getAll(): List<SyncNote>? = try {
        Log.d(tag, "getAll() from Nextcloud")
        val api = apiProvider.getAPI()
        api.getNotes(config).map { note -> note.asSyncNote() }
    } catch (e: Exception) {
        Log.e(tag, "getAll: Error getting notes from Nextcloud", e)
        null
    }
}
