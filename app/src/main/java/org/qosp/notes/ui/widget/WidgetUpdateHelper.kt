package org.qosp.notes.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

object WidgetUpdateHelper {
    fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NotesWidgetProvider::class.java)
            )

            if (appWidgetIds.isNotEmpty()) {
                appWidgetIds.forEach { appWidgetId ->
                    NotesWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
                }
                Log.d("WidgetUpdateHelper", "Updated ${appWidgetIds.size} widget(s)")
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateHelper", "Error updating widgets", e)
        }
    }
}
