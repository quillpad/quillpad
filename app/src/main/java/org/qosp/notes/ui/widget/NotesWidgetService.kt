package org.qosp.notes.ui.widget

import android.content.Intent
import android.widget.RemoteViewsService

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NotesWidgetViewsFactory(this.applicationContext)
    }
}
