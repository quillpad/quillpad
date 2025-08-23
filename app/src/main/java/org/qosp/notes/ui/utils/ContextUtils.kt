package org.qosp.notes.ui.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.qosp.notes.R
import org.qosp.notes.data.model.NoteColor

fun Context.resolveAttribute(resId: Int): Int? {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(resId, typedValue, true)) typedValue.data else null
}

fun Context.getDrawableCompat(@DrawableRes resId: Int) = ContextCompat.getDrawable(this, resId)

fun Context.getDimensionAttribute(attr: Int): Int? {
    return resolveAttribute(attr)?.let { TypedValue.complexToDimensionPixelSize(it, resources.displayMetrics) }
}

fun NoteColor.resId(context: Context): Int? {
    val resId = when (this) {
        NoteColor.Red -> R.attr.colorNoteRed
        NoteColor.Orange -> R.attr.colorNoteOrange
        NoteColor.Yellow -> R.attr.colorNoteYellow
        NoteColor.Green -> R.attr.colorNoteGreen
        NoteColor.Teal -> R.attr.colorNoteTeal
        NoteColor.Cyan -> R.attr.colorNoteCyan
        NoteColor.Blue -> R.attr.colorNoteBlue
        NoteColor.Purple -> R.attr.colorNotePurple
        NoteColor.Pink -> R.attr.colorNotePink
        NoteColor.Brown -> R.attr.colorNoteBrown
        NoteColor.Gray -> R.attr.colorNoteGray
        else -> R.attr.colorNoteDefault
    }

    return context.resolveAttribute(resId)
}
