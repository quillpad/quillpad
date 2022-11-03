package org.qosp.notes.ui.attachments.recycler

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.ui.utils.dp

class AttachmentsGridManager(private val context: Context) : LinearLayoutManager(context, HORIZONTAL, false) {

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean {
        if (itemCount == 0) return false
        val minWidth = width * 0.45
        lp?.width = (width.toDouble() / itemCount).coerceAtLeast(minWidth).toInt()
        lp?.height = (200).dp(context)
        return true
    }
}
