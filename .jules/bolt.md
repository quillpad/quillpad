## 2025-05-22 - [Note list performance optimizations]
**Learning:** Found several common Android performance bottlenecks:
1. Redundant background on inner layouts causing overdraw in note list items.
2. Manual ellipsization implementation using OnGlobalLayoutListener which adds overhead on every bind and triggers extra layout passes.
3. Lack of shared RecycledViewPool for nested RecyclerViews (tasks/attachments), causing unnecessary view inflation during scroll.
4. Frequent removeAllViews/addView calls in onBindViewHolder for tags.

**Action:**
1. Removed redundant background from LinearLayout inside NoteCardView.
2. Replaced manual ellipsize() with native android:ellipsize="end".
3. Implemented shared RecycledViewPool for tasks and attachments in NoteRecyclerAdapter.
4. Optimized updateTags to reuse existing views in ChipGroup.

## 2025-05-22 - [Native ellipsize="end" anti-pattern]
**Learning:** Replacing a custom manual ellipsization with native android:ellipsize="end" on multi-line TextViews in this codebase caused strange behavior where ellipsis appeared at the start of multiple lines. This might be due to a conflict with Markwon, animateLayoutChanges, or textIsSelectable.
**Action:** Reverted to manual ellipsization using OnGlobalLayoutListener for multi-line content to ensure visual correctness while maintaining other list optimizations.

## 2025-05-22 - [Note list rendering and DiffUtil optimizations]
**Learning:** `DiffUtil.ItemCallback.getChangePayload` using `sequenceOf` and `Pair` objects creates excessive garbage collection pressure when processing large lists of notes. Additionally, `NoteViewHolder` was performing expensive Markwon parsing and adapter submissions even for views that were hidden or in compact preview.
**Action:**
1. Optimized `getChangePayload` to use a manual `MutableList` with explicit property checks.
2. Refactored `NoteViewHolder.setContent` to conditionally skip expensive logic (Markwon, tasks) based on visibility and compact preview state.
3. Cleaned up redundant mappings in `onBindViewHolder` payload handling.

## 2025-05-22 - [Note list binding and nested adapter optimizations]
**Learning:** Even with DiffUtil, processing payloads in `onBindViewHolder` using functional operators like `filterIsInstance` and `flatten` creates significant allocation overhead. Furthermore, nested adapters (tasks) were running `DiffUtil` calculations when view holders were rebound to entirely different notes, which is wasteful.
**Action:**
1. Replaced `filterIsInstance().flatten()` in `onBindViewHolder` with a manual nested loop and a `Set` for zero-allocation (of intermediate lists) payload processing.
2. Added `useDiff` parameter to `TasksAdapter.submitList` and disabled diffing during initial `bind()` to skip irrelevant comparisons between different notes.
3. Granularized `NoteViewHolder` updates by splitting `setContent` into `setTextContent` and `setTasks`, allowing for targeted UI refreshes.
4. Aligned `setupAttachments` with other content by respecting `isCompactPreview`, avoiding unnecessary attachment processing in compact mode.
