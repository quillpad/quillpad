package org.qosp.notes.ui.tasks

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.text.clearSpans
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
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

class TaskViewHolder(
    private val context: Context,
    private val binding: LayoutTaskBinding,
    listener: TaskRecyclerListener?,
    private val inPreview: Boolean,
    private val markwon: Markwon,
) : RecyclerView.ViewHolder(binding.root) {

    private var isContentLoaded: Boolean = false
    private var isChecked: Boolean = false

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
            }
        }
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
        setContent(task.isDone, task.content)
    }
}
