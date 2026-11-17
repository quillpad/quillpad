package org.qosp.notes.data.sync.davx5

import android.net.Uri

object Davx5Contract {
    const val AUTHORITY = "io.github.quillpad.sync.davx5"
    val BASE_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

    const val PATH_COLLECTION = "collection"
    const val PATH_ICALOBJECT = "icalobject"
    const val PATH_CATEGORY = "category"
    const val PATH_ATTACHMENT = "attachment"
    const val PATH_ALARM = "alarm"

    val CONTENT_URI_COLLECTION: Uri = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_COLLECTION)
    val CONTENT_URI_ICALOBJECT: Uri = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ICALOBJECT)

    const val CALLER_IS_SYNCADAPTER = "caller_is_syncadapter"
    const val DAVX5_ACCOUNT_TYPE = "bitfire.at.davdroid"
    const val DAVX5_ACCOUNT_TYPE_ALT = "at.bitfire.davdroid"
    const val DAVX5_PACKAGE = "at.bitfire.davdroid"
    const val DAVX5_PACKAGE_OSE = "at.bitfire.davdroid.ose"
    const val DAVX5_PACKAGE_ORG = "org.davx5"

    object Collection {
        const val TABLE_NAME = "collection"
        const val _ID = "_id"
        const val ACCOUNT_NAME = "accountname"
        const val ACCOUNT_TYPE = "accounttype"
        const val URL = "url"
        const val DISPLAY_NAME = "displayname"
        const val DESCRIPTION = "description"
        const val COLOR = "color"
        const val SUPPORTS_VEVENT = "supportsVEVENT"
        const val SUPPORTS_VTODO = "supportsVTODO"
        const val SUPPORTS_VJOURNAL = "supportsVJOURNAL"
        const val SYNC_VERSION = "syncversion"
    }

    object ICalObject {
        const val TABLE_NAME = "icalobject"
        const val _ID = "_id"
        const val COLLECTION_ID = "collection_id"
        const val UID = "uid"
        const val SUMMARY = "summary"
        const val DESCRIPTION = "description"
        const val CREATED = "created"
        const val LAST_MODIFIED = "lastmodified"
        const val DTSTART = "dtstart"
        const val STATUS = "status"
        const val CLASSIFICATION = "classification"
        const val COLOR = "color"
        const val DIRTY = "dirty"
        const val DELETED = "deleted"
        const val FILENAME = "filename"
        const val ETAG = "etag"
        const val SCHEDULE_TAG = "scheduletag"
        const val FLAGS = "flags"
        const val COMPONENT = "component"
    }

    object Category {
        const val TABLE_NAME = "category"
        const val _ID = "_id"
        const val ICAL_OBJECT_ID = "icalObjectId"
        const val TEXT = "text"
    }

    object Attachment {
        const val TABLE_NAME = "attachment"
        const val _ID = "_id"
        const val ICAL_OBJECT_ID = "icalObjectId"
        const val URI = "uri"
        const val FILENAME = "filename"
        const val FMTTYPE = "fmttype"
    }

    object Alarm {
        const val TABLE_NAME = "alarm"
        const val _ID = "_id"
        const val ICAL_OBJECT_ID = "icalObjectId"
        const val ACTION = "action"
        const val TRIGGER_TIME = "triggerTime"
    }
}
