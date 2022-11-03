package org.qosp.notes.ui.editor.markdown

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import org.qosp.notes.ui.utils.views.ExtendedEditText

enum class MarkdownSpan(val value: String) {
    BOLD("**"),
    ITALICS("_"),
    STRIKETHROUGH("~~"),
    CODE("`"),
    QUOTE(">"),
    HEADING("#"),
}

fun ExtendedEditText.insertMarkdown(markdownSpan: MarkdownSpan) {
    val start = selectionStart
    val end = selectionEnd

    if (start < 0) return

    var line = text?.lines()?.get(currentLineIndex) ?: return
    val lineStart = currentLineStartPos
    val oldLength = line.length
    val s = markdownSpan.value

    when (markdownSpan) {
        MarkdownSpan.HEADING -> {
            line = when {
                line.matches(Regex("""($s)\1{5,} [^$s]*""")) -> {
                    line.replaceFirst(Regex("""($s)\1{5,} """), "")
                }
                line.matches(Regex("$s+ [^$s]*")) -> "$s$line"
                else -> "$s $line"
            }

            text?.replace(lineStart, lineStart + oldLength, line)
            setSelection(lineStart + line.length)
        }
        MarkdownSpan.QUOTE -> {
            line = when {
                line.matches(Regex("$s .*")) -> "$s$line"
                else -> "$s $line"
            }

            text?.replace(lineStart, lineStart + oldLength, line)
            setSelection(lineStart + line.length)
        }
        else -> {
            text?.insert(start, s)
            text?.insert(end + s.length, s)
            setSelection(start + s.length)
        }
    }
}

fun ExtendedEditText.toggleCheckmarkCurrentLine() {
    var line = text?.lines()?.get(currentLineIndex) ?: return
    val lineStart = currentLineStartPos
    val oldLength = line.length

    line = when {
        line.matches(Regex("-[ ]*\\[ \\][ ]+.*")) -> {
            line.replaceFirst("[ ]", "[x]").trimEnd() + " " // There's a strange bug which causes
            // text to be duplicated after pressing Enter
            // .trimEnd() + " " seems to be fixing it
        }
        line.matches(Regex("-[ ]*\\[x\\][ ]+.*")) -> {
            line.replaceFirst("[x]", "[ ]").trimEnd() + " "
        }
        else -> "- [ ] $line"
    }

    text?.replace(lineStart, lineStart + oldLength, line)
    setSelection(lineStart + line.length)
}

fun hyperlinkMarkdown(url: String, content: String): String {
    return "[$content]($url)"
}

fun imageMarkdown(url: String, description: String): String {
    return "![alt text]($url \"$description\")"
}

fun tableMarkdown(rows: Int, columns: Int): String {
    var markdown = ""

    for (r in 0..rows) {
        val space = if (r != 1) "    " else "----"
        for (c in 0 until columns) {
            markdown += "|$space"
        }
        markdown += "|\n"
    }
    return markdown
}

val ExtendedEditText.addListItemListener: TextView.OnEditorActionListener
    get() = TextView.OnEditorActionListener { v: TextView, actionId: Int, event: KeyEvent ->
        if (actionId == EditorInfo.TYPE_NULL && event.action == KeyEvent.ACTION_DOWN) {
            val text = text ?: return@OnEditorActionListener true
            text.insert(selectionStart, "\n")

            val previousLine = text.lines().getOrNull(currentLineIndex - 1) ?: return@OnEditorActionListener true

            when {
                previousLine.matches(Regex("-[ ]*\\[( |x)\\][ ]+.*")) -> text.insert(currentLineStartPos, "- [ ] ")
                previousLine.matches(Regex("-[ ]+.*")) -> text.insert(currentLineStartPos, "- ")
                previousLine.matches(Regex("[1-9]+[0-9]*[.][ ]+.*")) -> {
                    val inc = Regex("[1-9]+[0-9]*").findAll(previousLine).first().value.toInt().inc()
                    text.insert(currentLineStartPos, "$inc. ")
                }
            }
        }

        true
    }
