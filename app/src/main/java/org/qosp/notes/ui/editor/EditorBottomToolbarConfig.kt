package org.qosp.notes.ui.editor

import org.qosp.notes.R

enum class EditorBottomToolbarItem(
    val key: String,
    val itemId: Int,
    val titleRes: Int,
    val iconRes: Int,
) {
    BOLD("bold", R.id.action_insert_bold, R.string.action_insert_bold_text, R.drawable.ic_bold),
    ITALICS("italics", R.id.action_insert_italics, R.string.action_insert_italics, R.drawable.ic_italics),
    STRIKETHROUGH(
        "strikethrough",
        R.id.action_insert_strikethrough,
        R.string.action_insert_strikethrough,
        R.drawable.ic_strikethrough,
    ),
    HEADING("heading", R.id.action_insert_heading, R.string.action_insert_heading, R.drawable.ic_header),
    QUOTE("quote", R.id.action_insert_quote, R.string.action_insert_quote, R.drawable.ic_quote),
    HIGHLIGHT("highlight", R.id.action_insert_highlight, R.string.action_insert_highlight, R.drawable.ic_highlight),
    CODE("code", R.id.action_insert_code, R.string.action_insert_code, R.drawable.ic_code),
    LINK("link", R.id.action_insert_link, R.string.action_insert_link, R.drawable.ic_link),
    TABLE("table", R.id.action_insert_table, R.string.action_insert_table, R.drawable.ic_table),
    IMAGE("image", R.id.action_insert_image, R.string.action_insert_image, R.drawable.ic_photo),
    CHECK_LINE(
        "check_line",
        R.id.action_toggle_check_line,
        R.string.action_toggle_check_line,
        R.drawable.ic_box_checked_outline,
    ),
    SCROLL_TOP("scroll_top", R.id.action_scroll_to_top, R.string.action_scroll_to_top, R.drawable.ic_up),
    SCROLL_BOTTOM("scroll_bottom", R.id.action_scroll_to_bottom, R.string.action_scroll_to_bottom, R.drawable.ic_down),
    UNDO("undo", R.id.action_undo, R.string.action_undo, R.drawable.ic_undo),
    REDO("redo", R.id.action_redo, R.string.action_redo, R.drawable.ic_redo),
}

data class EditorBottomToolbarItemState(
    val item: EditorBottomToolbarItem,
    val visible: Boolean,
)

object EditorBottomToolbarConfig {
    val defaultItems: List<EditorBottomToolbarItemState>
        get() = EditorBottomToolbarItem.entries.map { EditorBottomToolbarItemState(it, visible = true) }

    fun parse(value: String): List<EditorBottomToolbarItemState> {
        if (value.isBlank()) return defaultItems

        val itemsByKey = EditorBottomToolbarItem.entries.associateBy { it.key }
        val parsed = value.split(ITEM_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(VALUE_SEPARATOR, limit = 2)
                val item = itemsByKey[parts.getOrNull(0)] ?: return@mapNotNull null
                EditorBottomToolbarItemState(
                    item = item,
                    visible = parts.getOrNull(1) != HIDDEN_VALUE,
                )
            }

        val parsedItems = parsed.map { it.item }.toSet()
        val missingItems = EditorBottomToolbarItem.entries
            .filterNot { it in parsedItems }
            .map { EditorBottomToolbarItemState(it, visible = true) }

        return parsed + missingItems
    }

    fun serialize(items: List<EditorBottomToolbarItemState>): String {
        return items.joinToString(ITEM_SEPARATOR) { state ->
            "${state.item.key}$VALUE_SEPARATOR${if (state.visible) VISIBLE_VALUE else HIDDEN_VALUE}"
        }
    }

    private const val ITEM_SEPARATOR = ","
    private const val VALUE_SEPARATOR = ":"
    private const val VISIBLE_VALUE = "1"
    private const val HIDDEN_VALUE = "0"
}
