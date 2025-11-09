# Add Drag-to-Sort Functionality with Custom Sort Order and Move Mode

## Overview
This PR adds the ability to reorder notes via drag-and-drop with a new "Custom" sort option and a "Move mode" toggle for intuitive reordering. The custom order is persisted locally and synced to cloud services.

## Features

### ✅ Custom Sort Option
- Added "Custom" to the sort menu alongside existing options (Title, Date Created, Date Modified)
- Custom sort order is remembered when switching between sort methods
- Only available when "Custom" sort is selected

### ✅ Move Mode Toggle
- **New menu option:** "Move mode" checkbox
- When enabled: **touch + drag** any note to reorder (no long-press needed!)
- When disabled: normal behavior (context menu, note opening)
- Automatically switches to Custom sort when enabled
- Clear visual feedback via snackbar

### ✅ Drag-and-Drop Reordering
- **Touch + drag** immediately starts reordering in move mode
- Works in both **List mode** (vertical drag only) and **Grid mode** (all directions)
- Supports 2-column grid layout with horizontal reordering
- Visual feedback during drag
- No gesture conflicts - dedicated mode for dragging

### ✅ Pull-to-Refresh Handling
- Pull-to-refresh is automatically disabled in move mode
- Prevents conflicts between drag gesture and pull-down-to-sync
- Re-enabled when move mode is disabled

### ✅ Persistence & Sync
- Custom order saved to local SQLite database
- **Database migration** (v5 → v6) adds `customSortOrder` field
- Syncs to **Nextcloud** via hidden HTML comment in note content: `<!-- customSortOrder:123 -->`
- Included in JSON backups via `@Serializable`
- Bidirectional sync - order syncs both ways

## Technical Implementation

### Database Changes
- Added `customSortOrder: Int` field to `Note` and `NoteEntity`
- Created migration `MIGRATION_5_6` to add column with default value 0
- Updated DAO to support custom sorting with `ORDER BY customSortOrder ASC`

### UI Changes
- Added "Move mode" menu item (checkable)
- Implemented `ItemTouchHelper` with adaptive movement flags:
  - **List mode**: UP/DOWN only
  - **Grid mode**: UP/DOWN/LEFT/RIGHT
- Touch listener in `NoteViewHolder`:
  - In move mode: ACTION_DOWN starts drag immediately
  - In normal mode: standard touch behavior
- Move mode state tracked in `ActivityViewModel`
- Visual feedback via snackbar messages

### Sync Implementation
- Embedded `customSortOrder` as HTML comment in note content for Nextcloud sync
- Parser extracts order value when syncing from cloud
- Metadata is invisible in rendered Markdown/plain text
- No Nextcloud API changes required

## User Experience Flow

### Enabling Move Mode

1. **Enable Move Mode**
   - Open menu → Check "Move mode"
   - If not on Custom sort: automatically switches to Custom
   - Snackbar shows: "Move mode enabled - touch and drag notes to reorder"
   - Pull-to-refresh disabled

2. **Reorder Notes**
   - **Touch any note** → drag starts immediately
   - Note follows finger movement
   - Other notes shift to make room
   - Works in list (vertical) and grid (all directions)
   
3. **Release to Drop**
   - Custom sort order saved to database
   - Changes sync to cloud automatically

4. **Disable Move Mode**
   - Uncheck "Move mode" in menu
   - Normal behavior restored (context menu, note opening)
   - Pull-to-refresh re-enabled

### Normal Mode vs Move Mode

| Mode | Touch Note | Long-Press Note | Pull-Down |
|------|-----------|----------------|-----------|
| **Normal** | Opens note | Context menu | Refresh |
| **Move Mode** | **Starts drag** | Blocked | Disabled |

## Files Changed

### Core Data Layer
- `app/src/main/java/org/qosp/notes/data/model/Note.kt` - Added customSortOrder field
- `app/src/main/java/org/qosp/notes/data/dao/NoteDao.kt` - Updated getOrderByMethod()
- `app/src/main/java/org/qosp/notes/data/AppDatabase.kt` - Database v6 + migration
- `app/src/main/java/org/qosp/notes/di/DatabaseModule.kt` - Registered migration

### Repository Layer
- `app/src/main/java/org/qosp/notes/data/repo/NoteRepository.kt` - Added updateCustomSortOrder()
- `app/src/main/java/org/qosp/notes/data/repo/NoteRepositoryImpl.kt` - Implemented update logic

### Sync Layer
- `app/src/main/java/org/qosp/notes/data/sync/Converters.kt` - Extract/parse customSortOrder from content
- `app/src/main/java/org/qosp/notes/data/sync/nextcloud/model/NextcloudConverters.kt` - Embed customSortOrder in content

### UI Layer
- `app/src/main/java/org/qosp/notes/ui/common/AbstractNotesFragment.kt` - ItemTouchHelper implementation
- `app/src/main/java/org/qosp/notes/ui/common/recycler/NoteRecyclerAdapter.kt` - Drag callbacks
- `app/src/main/java/org/qosp/notes/ui/common/recycler/NoteViewHolder.kt` - Touch detection
- `app/src/main/java/org/qosp/notes/ui/main/MainFragment.kt` - Menu handler for custom sort
- `app/src/main/java/org/qosp/notes/ui/ActivityViewModel.kt` - updateCustomSortOrder()

### Preferences
- `app/src/main/java/org/qosp/notes/preferences/PreferenceEnums.kt` - Added CUSTOM to SortMethod

### Resources
- `app/src/main/res/menu/main_top.xml` - Added "Custom" menu item
- `app/src/main/res/values/strings.xml` - Added custom sort string

### Schema
- `app/schemas/org.qosp.notes.data.AppDatabase/6.json` - Database schema v6

## Testing

### Manual Testing Checklist
- [ ] Select "Custom" from sort menu
- [ ] Long-press note and drag to reorder
- [ ] Verify context menu closes on movement
- [ ] Verify pull-to-refresh doesn't trigger during drag
- [ ] Switch to grid layout and test horizontal drag
- [ ] Switch to another sort method, then back to Custom - order preserved
- [ ] Sync with Nextcloud - order syncs correctly
- [ ] Test on multiple devices - order syncs between them

## Backward Compatibility

- ✅ Database migration handles existing installations
- ✅ Default `customSortOrder = 0` for existing notes
- ✅ Metadata comment ignored by older clients
- ✅ No breaking changes to sync protocol

## Key Design Decisions

### Why Move Mode Instead of Drag Handle?

**Initial Approach (Abandoned):**
- Drag handle icon (≡) on each note
- Complex touch handling to distinguish drag from click
- Touch target issues and gesture conflicts

**Final Approach (Move Mode):**
- ✅ Simple toggle - one control vs per-note handles
- ✅ No touch target issues - entire note is draggable
- ✅ Clear mode indication - user knows when drag is available
- ✅ Works reliably with ItemTouchHelper
- ✅ Clean separation: drag mode vs normal mode

## Statistics

- **15 files changed**
- **~500 insertions**
- **~150 deletions**  
- **15 commits** (including iterations and refinements)

## Related Issues

Closes #[issue number if applicable]
