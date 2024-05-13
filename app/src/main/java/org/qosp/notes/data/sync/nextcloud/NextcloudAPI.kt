package org.qosp.notes.data.sync.nextcloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import org.qosp.notes.data.sync.nextcloud.model.NextcloudCapabilities
import org.qosp.notes.data.sync.core.NextcloudNote
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

const val baseURL = "index.php/apps/notes/api/v1/"

interface NextcloudAPI {
    @GET
    suspend fun getNotesAPI(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): List<NextcloudNote>

    @GET
    suspend fun getNoteAPI(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): NextcloudNote

    @POST
    suspend fun createNoteAPI(
        @Body note: NextcloudNote,
        @Url url: String,
        @Header("Authorization") auth: String,
    ): NextcloudNote

    @PUT
    suspend fun updateNoteAPI(
        @Body note: NextcloudNote,
        @Url url: String,
        @Header("If-Match") etag: String,
        @Header("Authorization") auth: String,
    ): NextcloudNote

    @DELETE
    suspend fun deleteNoteAPI(
        @Url url: String,
        @Header("Authorization") auth: String,
    )

    @Headers(
        "OCS-APIRequest: true",
        "Accept: application/json"
    )
    @GET
    suspend fun getAllCapabilitiesAPI(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): ResponseBody
}

suspend fun NextcloudAPI.getNotesCapabilities(config: NextcloudConfig): NextcloudCapabilities? {
    val endpoint = "ocs/v2.php/cloud/capabilities"
    val fullUrl = config.remoteAddress + endpoint

    val response = withContext(Dispatchers.IO) {
        getAllCapabilitiesAPI(url = fullUrl, auth = config.credentials).string()
    }

    val element = Json
        .parseToJsonElement(response).jsonObject["ocs"]?.jsonObject
        ?.get("data")?.jsonObject
        ?.get("capabilities")?.jsonObject
        ?.get("notes")
    return element?.let { Json.decodeFromJsonElement<NextcloudCapabilities>(it) }
}

suspend fun NextcloudAPI.deleteNote(note: NextcloudNote, config: NextcloudConfig) {
    deleteNoteAPI(
        url = config.remoteAddress + baseURL + "notes/${note.id}",
        auth = config.credentials,
    )
}

suspend fun NextcloudAPI.updateNote(note: NextcloudNote, etag: String, config: NextcloudConfig): NextcloudNote {
    return updateNoteAPI(
        note = note,
        url = config.remoteAddress + baseURL + "notes/${note.id}",
        etag = "\"$etag\"",
        auth = config.credentials,
    )
}

suspend fun NextcloudAPI.createNote(note: NextcloudNote, config: NextcloudConfig): NextcloudNote {
    return createNoteAPI(
        note = note,
        url = config.remoteAddress + baseURL + "notes",
        auth = config.credentials,
    )
}

suspend fun NextcloudAPI.getNote(id: Long, config: NextcloudConfig): NextcloudNote {
    return getNoteAPI(
        url = config.remoteAddress + baseURL + "notes" + "/$id",
        auth = config.credentials
    )
}

suspend fun NextcloudAPI.getNotes(config: NextcloudConfig): List<NextcloudNote> {
    return getNotesAPI(
        url = config.remoteAddress + baseURL + "notes",
        auth = config.credentials,
    )
}

suspend fun NextcloudAPI.testCredentials(config: NextcloudConfig) {
    getNotesAPI(
        url = config.remoteAddress + baseURL + "notes",
        auth = config.credentials,
    )
}
