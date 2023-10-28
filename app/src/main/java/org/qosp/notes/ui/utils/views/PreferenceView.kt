package org.qosp.notes.ui.utils.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import org.qosp.notes.R

class PreferenceView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    fun setIcon(@DrawableRes id: Int) {
        imageView.setImageResource(id)
    }

    private val imageView: AppCompatImageView
    private val textView: AppCompatTextView
    private val subTextView: AppCompatTextView

    var subText: String = ""
        set(value) {
            subTextView.text = value
            subTextView.isVisible = value.isNotBlank()
            field = value
        }
    var subTextId: Int = 0
        set(id) {
            if (id > 0) {
                subTextView.text = context.getText(id)
                subTextView.isVisible = context.getText(id).isNotBlank()
            }
            field = id
        }

    init {
        inflate(context, R.layout.layout_about_item, this)
        imageView = findViewById(R.id.about_item_image_view)
        textView = findViewById(R.id.about_item_text_view)
        subTextView = findViewById(R.id.about_item_sub_text_view)

        context.theme.obtainStyledAttributes(attrs, R.styleable.PreferenceView, 0, 0).apply {
            try {
                imageView.setImageResource(getResourceId(R.styleable.PreferenceView_iconSrc, 0))
                subTextView.text = getString(R.styleable.PreferenceView_subText).also {
                    if (it != null) subTextView.isVisible = true
                }
                textView.text = getString(R.styleable.PreferenceView_text)
            } finally {
                recycle()
            }
        }
    }
}
