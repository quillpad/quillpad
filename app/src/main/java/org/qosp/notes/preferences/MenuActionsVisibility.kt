package org.qosp.notes.preferences

import me.msoul.datastore.defaultOf

enum class ShowScreenAlwaysOn(val value: Boolean = true) {
    SHOW, HIDE(false)
}

enum class ShowDuplicate(val value: Boolean = true) {
    SHOW, HIDE(false)
}

enum class ShowExport(val value: Boolean = true) {
    SHOW, HIDE(false)
}

enum class ShowMarkdown(val value: Boolean = true) {
    SHOW, HIDE(false)
}

enum class ShowHideNote(val value: Boolean = true) {
    SHOW, HIDE(false)
}

enum class ShowMoveToNotebook(val value: Boolean = true) {
    SHOW, HIDE(false)
}