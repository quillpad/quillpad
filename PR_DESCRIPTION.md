# Add Drag-to-Sort Functionality with Custom Sort Order

## Overview
This PR adds the ability to reorder notes via drag-and-drop with a new "Custom" sort option. The custom order is persisted locally and synced to cloud services.

## Features

### ✅ Custom Sort Option
- Added "Custom" to the sort menu alongside existing options (Title, Date Created, Date Modified)
- Custom sort order is remembered when switching between sort methods
- Only available when "Custom" sort is selected

### ✅ Drag-and-Drop Reordering
- **Long-press + move** to enter drag mode
- Works in both **List mode** (vertical drag only) and **Grid mode** (all directions)
- Supports 2-column grid layout with horizontal reordering
- Visual feedback during drag

### ✅ Context Menu Integration
- Long-press opens context menu as before
- If you continue holding and **start moving**, the context menu automatically dismisses and drag mode activates
- Smooth transition from menu to drag

### ✅ Pull-to-Refresh Handling
- Pull-to-refresh is automatically disabled while dragging
- Prevents conflicts between drag gesture and pull-down-to-sync
- Re-enabled when drag ends

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
- Implemented `ItemTouchHelper` with adaptive movement flags:
  - **List mode**: UP/DOWN only
  - **Grid mode**: UP/DOWN/LEFT/RIGHT
- Custom touch detection in `NoteViewHolder`:
  - Tracks touch position on ACTION_DOWN
  - Detects movement threshold (10px) on ACTION_MOVE
  - Triggers drag when threshold exceeded during long-press
- Added callbacks for context menu dismissal and drag start

### Sync Implementation
- Embedded `customSortOrder` as HTML comment in note content for Nextcloud sync
- Parser extracts order value when syncing from cloud
- Metadata is invisible in rendered Markdown/plain text
- No Nextcloud API changes required

## User Experience Flow

### Long Hold → Continue Holding → Drag

1. **Long Press Detected**
   - Context menu (BottomSheet) opens
   - `isLongPressing = true` flag is set

2. **User Continues Holding and Starts Moving**
   - When movement exceeds 10 pixels:
     - ✅ Blocks SwipeRefreshLayout from intercepting touch
     - ✅ Cancels long-press callback
     - ✅ Dismisses context menu via FragmentManager
     - ✅ Starts drag mode

3. **ItemTouchHelper Takes Over**
   - ✅ Disables pull-to-refresh (`swipeRefreshLayout.isEnabled = false`)
   - ✅ Allows dragging in appropriate directions

4. **User Drags the Note**
   - Note follows finger movement
   - Visual feedback shows drag state
   - Other notes shift to make room

5. **User Releases (Drag Ends)**
   - ✅ Re-enables parent touch interception
   - ✅ Re-enables pull-to-refresh
   - ✅ Saves custom sort order to database
   - ✅ Syncs changes to cloud

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

## Statistics

- **17 files changed**
- **628 insertions**
- **19 deletions**
- **2 commits**

## Related Issues

Closes #[issue number if applicable]
