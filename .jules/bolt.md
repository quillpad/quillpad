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
