package org.qosp.notes.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.net.toUri
import org.qosp.notes.R
import org.qosp.notes.ui.MainActivity

class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
    }

    override fun onDisabled(context: Context) {
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_notes_list)

            val serviceIntent = Intent(context, NotesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = toUri(Intent.URI_INTENT_SCHEME).toUri()
            }
            views.setRemoteAdapter(R.id.widget_notes_list, serviceIntent)

            val clickIntent = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val clickPendingIntent = PendingIntent.getActivity(context, 0, clickIntent, flags)
            views.setPendingIntentTemplate(R.id.widget_notes_list, clickPendingIntent)

            // Show an informative empty state when there are no pinned notes
            views.setEmptyView(R.id.widget_notes_list, R.id.widget_empty_view)
            // Allow tapping the empty state to open the app
            views.setOnClickPendingIntent(R.id.widget_empty_view, clickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_notes_list)
        }
    }
}
