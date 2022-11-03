package org.qosp.notes.ui.attachments.recycler

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.ui.utils.dp

class AttachmentsPreviewGridManager(private val context: Context, private val spans: Int) : GridLayoutManager(context, spans) {

    private var itemSpans = listOf<Int>()

    init {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = itemSpans.getOrNull(position) ?: 1
        }
    }

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean {
        lp?.height = (200 / spanCount).dp(context)
        return true
    }

    fun allocateSpans(itemsCount: Int) {
        var rowSpans = 0
        itemSpans = (0 until itemsCount).map { pos ->
            val size = when {
                itemsCount == 2 && pos == 0 -> spans - rowSpans
                pos == itemsCount - 1 && (itemsCount % spans != 0 || itemsCount == spans) -> spans - rowSpans
                else -> 1
            }
            rowSpans += size
            if (rowSpans >= spans) rowSpans = 0
            size
        }
    }
}
