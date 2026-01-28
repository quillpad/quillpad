package org.qosp.notes.ui.tasks

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.text.clearSpans
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.qosp.notes.R
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.databinding.LayoutTaskBinding
import org.qosp.notes.ui.utils.applyMask
import org.qosp.notes.ui.utils.dp
import org.qosp.notes.ui.utils.ellipsize
import org.qosp.notes.ui.utils.getDrawableCompat
import org.qosp.notes.ui.utils.hideKeyboard
import org.qosp.notes.ui.utils.requestFocusAndKeyboard
import org.qosp.notes.ui.utils.resolveAttribute

private const val INDENTATION_DP_STEP = 36 // Define step size
private const val MAX_INDENTATION_LEVELS = 1 // Define max levels

class TaskViewHolder(
    private val context: Context,
    private val binding: LayoutTaskBinding,
    listener: TaskRecyclerListener?,
    private val inPreview: Boolean,
    private val markwon: Markwon,
) : RecyclerView.ViewHolder(binding.root) {

    private var isContentLoaded: Boolean = false
    private var isChecked: Boolean = false

    // New property to store and apply visual indentation level
    var indentationLevel: Int = 0
        private set(value) {
            field = value.coerceIn(0, MAX_INDENTATION_LEVELS)
            // apply visual change
            setSpacerWidth(field*dpStep)
            //binding.indentSpacer.text = field.toString() // debugging
        }

    var spacerWidth: Float=1f
    val dpStep = INDENTATION_DP_STEP.dp(context)
    private var mListener: TaskRecyclerListener?

    init {
        with(binding) {
            val verticalPadding = if (inPreview) 4.dp() else 0.dp()
            val horizonalPading = if (inPreview) 0.dp() else 16.dp()

            root.setPadding(
                horizonalPading,
                verticalPadding,
                horizonalPading,
                verticalPadding
            )

            checkBoxPreview.isVisible = inPreview
            checkBox.isVisible = !inPreview

            editText.imeOptions = EditorInfo.IME_ACTION_NEXT
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

            textView.maxLines = if (inPreview) 2 else 4

            if (!inPreview) {
                // Style textView to match editText
                TextViewCompat.setTextAppearance(textView, R.style.TextAppearance_MaterialComponents_Body1)
                with(ConstraintSet()) {
                    clone(root)
                    connect(textView.id, ConstraintSet.START, checkBox.id, ConstraintSet.END, 0)
                    connect(textView.id, ConstraintSet.END, dragHandle.id, ConstraintSet.START, 16.dp())
                    applyTo(root)
                }
            }

            dragHandle.isVisible = !inPreview

            @SuppressLint("ClickableViewAccessibility")
            if (listener != null && !inPreview) {
                dragHandle.setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) listener.onDrag(this@TaskViewHolder)
                    true
                }

                checkBox.setOnClickListener {
                    listener.onTaskStatusChanged(bindingAdapterPosition, checkBox.isChecked)
                    editText.hideKeyboard()
                }

                checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                    setContent(isChecked, binding.editText.text.toString())
                }

                editText.doOnTextChanged { text, start, before, count ->
                    setTextViewText(text.toString(), isChecked)
                    if (isContentLoaded) {
                        listener.onTaskContentChanged(bindingAdapterPosition, binding.editText.text.toString())
                    }
                }

                editText.setOnEditorActionListener { v, actionId, event ->
                    when (actionId) {
                        EditorInfo.IME_ACTION_NEXT -> {
                            listener.onNext(bindingAdapterPosition)
                            true
                        }
                        else -> false
                    }
                }
                // Set click listener for the delete icon
                taskDeleteIcon.setOnClickListener {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        // Call the deletion method in the fragment via the listener
                        listener.onTaskDelete(bindingAdapterPosition)
                    }
                }
                // set visibility of delete icon
                editText.setOnFocusChangeListener { _, hasFocus ->
                    taskDeleteIcon.visibility = if (hasFocus) View.VISIBLE else View.GONE
                }
            }
        }
        mListener = listener
    }

    private val colorMaskDrag = context.resolveAttribute(R.attr.colorHighlightMask) ?: Color.TRANSPARENT
    var taskBackgroundColor = Color.TRANSPARENT

    var isBeingMoved = false
        set(value) {
            field = value
            val colorWithMask = taskBackgroundColor.applyMask(colorMaskDrag)
            val fromColor = if (value) taskBackgroundColor else colorWithMask
            val toColor = if (value) colorWithMask else taskBackgroundColor

            ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
                duration = 300
                addUpdateListener { binding.root.setBackgroundColor(it.animatedValue as Int) }
                doOnEnd { if (!value) binding.root.setBackgroundColor(Color.TRANSPARENT) }
                start()
            }
        }

    var isEnabled = true
        set(enabled) {
            field = enabled
            with(binding) {
                dragHandle.isVisible = enabled
                editText.isVisible = enabled && !isChecked
                textView.isVisible = !enabled || isChecked
                textView.isEnabled = !isChecked
            }
        }
    private fun setSpacerWidth(mWidth: Int) {
        val targetWidth = mWidth.coerceAtLeast(1)
        //Log.d("TaskViewHolder", "setSpacerWidth: $mWidth")
        binding.indentSpacer.updateLayoutParams {
            //width = dp.dp(context)
            width=targetWidth
        }
    }

    /**
     * Updates the visual indentation during a swipe action based on the raw swipe distance (dX).
     * This is called repeatedly in ItemTouchHelper.onChildDraw.
     * @param rawDx The horizontal displacement of the swipe.
     */
    fun setVisualIndentation(rawDx: Float) {
        if (inPreview) return
        //Log.d("TaskViewHolder", "setVisualIndentation: $rawDx")
        // Calculate the base width due to current committed indentation level
        val committedWidth = indentationLevel * dpStep

        // Calculate the temporary visual width (committed + swipe displacement)
        var newVisualWidth = committedWidth + rawDx

        // Ensure width stays within bounds (0 to max)
        if (newVisualWidth < 0) newVisualWidth = 0f

        val maxIndentWidth = MAX_INDENTATION_LEVELS * dpStep
        if (newVisualWidth > maxIndentWidth) newVisualWidth = maxIndentWidth.toFloat()
        spacerWidth= newVisualWidth

        // Apply the temporary visual width to the spacer
        setSpacerWidth(spacerWidth.toInt())
    }

    /**
     * Commits the indentation change after the swipe gesture is released.
     */
    fun commitIndentationChange() {
        val threshold = dpStep / 2 // Require half a step to change indentation
        val dx = spacerWidth - (dpStep * indentationLevel)

        var newLevel = indentationLevel

        if (dx > threshold && indentationLevel < MAX_INDENTATION_LEVELS) {
            newLevel++ // Increase indentation (Swiping Right)
        } else if (dx < -threshold && indentationLevel > 0) {
            newLevel-- // Decrease indentation (Swiping Left)
        }

        val oldLevel = indentationLevel

        // Apply the committed level. This updates the field and the visual spacer.
        indentationLevel = newLevel

        // Explicitly notify the listener only when committed and changed,
        // which triggers the model update in EditorFragment.
        if (newLevel != oldLevel) {
            mListener?.onTaskIndentationChanged(bindingAdapterPosition, newLevel)
        }
    }

    private fun setContent(isChecked: Boolean, text: String? = null) = with(binding) {
        // Needed so the textChangedListener does nothing for input not by the user
        isContentLoaded = false
        this@TaskViewHolder.isChecked = isChecked

        textView.isVisible = inPreview || isChecked || !isEnabled
        setTextViewText(text.toString(), isChecked)
        textView.isEnabled = !isChecked
        textView.ellipsize()

        editText.isVisible = !inPreview && !isChecked && isEnabled
        editText.setText(text)

        checkBox.isChecked = isChecked
        checkBoxPreview.setImageDrawable(
            context.getDrawableCompat(
                if (isChecked) R.drawable.ic_box_checked else R.drawable.ic_box
            )
        )

        isContentLoaded = true
    }

    private fun setTextViewText(text: String, isChecked: Boolean) {
        binding.textView.text.toSpannable().clearSpans()
        if (isChecked && text.isNotBlank()) {
            markwon.setMarkdown(binding.textView, "~~${text.trim()}~~")
        } else {
            binding.textView.text = text
        }
    }

    private fun Int.dp(): Int = this.dp(context)

    fun requestFocus() = binding.editText.requestFocusAndKeyboard()

    fun bind(task: NoteTask) {
        indentationLevel = task.indentationLevel
        setContent(task.isDone, task.content)
    }
}
