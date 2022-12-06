package org.qosp.notes.ui.editor.markdown

import android.text.Editable
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
    HIGHLIGHT("=="),
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
        line.matches(Regex("- *\\[ ] +.*")) -> {
            line.replaceFirst("[ ]", "[x]").trimEnd() + " " // There's a strange bug which causes
            // text to be duplicated after pressing Enter
            // .trimEnd() + " " seems to be fixing it
        }

        line.matches(Regex("- *\\[x] +.*")) -> {
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

private val listRegex = Regex("^((\\s*)- +).*")
private val checkRegex = Regex("^((\\s*)- *\\[([ x])] +).*")
private val numListRegex = Regex("((\\s*)([1-9]+)[.] +).*")


val ExtendedEditText.addListItemListener: TextView.OnEditorActionListener
    get() = object : TextView.OnEditorActionListener {
        override fun onEditorAction(view: TextView, actionId: Int, event: KeyEvent?): Boolean {
            if (actionId == EditorInfo.IME_ACTION_DONE ) {
                val text = text ?: return true
                text.insert(selectionStart, "\n")
                val previousLine = text.lines().getOrNull(currentLineIndex - 1) ?: return true
                when {
                    previousLine.matches(checkRegex) -> nextListLine(checkRegex, previousLine, "- [ ] ", text)
                    previousLine.matches(listRegex) -> nextListLine(listRegex, previousLine, "- ", text)
                    previousLine.matches(numListRegex) -> {
                        val suffix = numListRegex.find(previousLine)?.groupValues?.get(3)?.toInt()?.inc() ?: 1
                        nextListLine(numListRegex, previousLine, "$suffix. ", text)
                    }
                }
            }
            return true
        }

        private fun nextListLine(regex: Regex, line: String, suffix: String, text: Editable) {
            val matchedLine = regex.find(line)?.groupValues?.getOrNull(1) ?: ""
            if (matchedLine == line) {
                text.delete(currentLineStartPos - line.length - 1, currentLineStartPos)
            } else {
                val prefix = (regex.find(line)?.groupValues?.getOrNull(2) ?: "")
                text.insert(currentLineStartPos, "$prefix$suffix")
            }
        }

    }
