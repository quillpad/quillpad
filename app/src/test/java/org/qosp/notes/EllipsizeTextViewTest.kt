package org.qosp.notes

import android.text.Layout
import android.view.ViewGroup
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.ui.utils.ellipsize
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Regression tests for quillpad/quillpad#568: ellipsize() crashed with a
 * StringIndexOutOfBoundsException from its global layout listener.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EllipsizeTextViewTest {

    private fun textViewWithLayout(content: String, lineCount: Int, lineEnd: Int): TextView {
        val layout = mockk<Layout>()
        every { layout.lineCount } returns lineCount
        every { layout.getLineEnd(any()) } returns lineEnd

        return TextView(RuntimeEnvironment.getApplication()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            maxLines = 2
            text = content
            ReflectionHelpers.setField(this, "mLayout", layout)
        }
    }

    private fun TextView.runEllipsize() {
        ellipsize()
        viewTreeObserver.dispatchOnGlobalLayout()
    }

    @Test
    fun `truncates and appends ellipsis when text exceeds max lines`() {
        val tv = textViewWithLayout("a".repeat(127), lineCount = 5, lineEnd = 50)
        tv.runEllipsize()
        assertEquals("a".repeat(47) + "...", tv.text.toString())
    }

    @Test
    fun `does not crash when line end is shorter than the ellipsis`() {
        val tv = textViewWithLayout("ab", lineCount = 5, lineEnd = 1)
        tv.runEllipsize()
        assertEquals("...", tv.text.toString())
    }

    @Test
    fun `does not crash when line end is negative`() {
        val tv = textViewWithLayout("a".repeat(127), lineCount = 5, lineEnd = -1)
        tv.runEllipsize()
        assertEquals("...", tv.text.toString())
    }

    @Test
    fun `does not crash when layout is stale and longer than the text`() {
        val tv = textViewWithLayout("short", lineCount = 5, lineEnd = 500)
        tv.runEllipsize()
        assertEquals("short...", tv.text.toString())
    }

    @Test
    fun `does not crash with empty text`() {
        val tv = textViewWithLayout("", lineCount = 5, lineEnd = 10)
        tv.runEllipsize()
        assertEquals("...", tv.text.toString())
    }

    @Test
    fun `leaves text untouched when it fits`() {
        val tv = textViewWithLayout("hello", lineCount = 1, lineEnd = 5)
        tv.runEllipsize()
        assertEquals("hello", tv.text.toString())
    }
}
