package org.qosp.notes.ui.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import java.text.SimpleDateFormat
import java.util.*

class NotesWidgetViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory, KoinComponent {

    private val noteRepository: NoteRepository by inject()
    private var notes: List<Note> = emptyList()

    override fun onCreate() {
        // Called when first created
    }

    override fun onDataSetChanged() {
        // Called when data is updated
        runBlocking {
            try {
                notes = noteRepository.getNonDeletedOrArchived().first()
                    .filter { it.isPinned }
                    .sortedByDescending { it.modifiedDate }.take(20) // Show up to 20 notes
                Log.d("NotesWidgetFactory", "Loaded ${notes.size} notes")
            } catch (e: Exception) {
                Log.e("NotesWidgetFactory", "Error loading notes", e)
                notes = emptyList()
            }
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        try {
            if (position >= notes.size) {
                return views
            }

            val note = notes[position]

            // Set note title
            val title = note.title.ifBlank {
                note.content.take(50).lines().firstOrNull()?.trim() ?: "Untitled"
            }
            views.setTextViewText(R.id.widget_note_title, title)

            // Set note content
            val content = when {
                note.isList -> {
                    val taskCount = note.taskList.size
                    val doneCount = note.taskList.count { it.isDone }
                    "$doneCount / $taskCount tasks"
                }

                note.content.isNotBlank() -> {
                    note.content.take(100).replace("\n", " ")
                }

                else -> ""
            }
            views.setTextViewText(R.id.widget_note_content, content)

            // Set the date
            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            val date = dateFormat.format(Date(note.modifiedDate * 1000))
            views.setTextViewText(R.id.widget_note_date, date)

            // Open note when clicked
            val fillInIntent = Intent().apply {
                putExtra("noteId", note.id)
            }
            views.setOnClickFillInIntent(R.id.widget_note_item_container, fillInIntent)

        } catch (e: Exception) {
            Log.e("NotesWidgetFactory", "Error creating view at position $position", e)
        }

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position < notes.size) notes[position].id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
