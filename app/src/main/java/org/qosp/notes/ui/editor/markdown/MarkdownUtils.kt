package org.qosp.notes.ui.editor.markdown

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
        line.matches(Regex("[-+*] *\\[ ] +.*")) -> {
            line.replaceFirst("[ ]", "[x]").trimEnd() + " " // There's a strange bug which causes
            // text to be duplicated after pressing Enter
            // .trimEnd() + " " seems to be fixing it
        }

        line.matches(Regex("[-+*] *\\[[xX]] +.*")) -> {
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
