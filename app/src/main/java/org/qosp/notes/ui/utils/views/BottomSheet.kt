package org.qosp.notes.ui.utils.views

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.parcelize.Parcelize
import org.qosp.notes.R
import org.qosp.notes.ui.utils.resolveAttribute

class BottomSheet : BottomSheetDialogFragment() {
    @Suppress("UNCHECKED_CAST")
    private val actions: Set<Action>? by lazy {
        arguments?.get(MENU_ACTIONS) as? Set<Action>
    }
    private val header: String? by lazy { arguments?.getString(MENU_HEADER) }
    private val showPlaceHolderText by lazy { arguments?.getBoolean(SHOW_PLACEHOLDER) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return NestedScrollView(requireContext()).apply {
            addView(
                LinearLayout(context).apply {
                    context.resolveAttribute(R.attr.colorDrawerBackground)?.let { background = ColorDrawable(it) }
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, 0, 16)

                    addView(
                        LinearLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                            orientation = LinearLayout.HORIZONTAL

                            addView(
                                AppCompatTextView(ContextThemeWrapper(context, R.style.BottomSheetHeader)).apply {
                                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                                        weight = 1F
                                    }

                                    text = when {
                                        header.isNullOrBlank() && showPlaceHolderText == true -> context.getString(R.string.indicator_untitled)
                                        else -> header
                                    }
                                }
                            )
                        }
                    )

                    actions?.forEach { action ->
                        addView(
                            AppCompatTextView(ContextThemeWrapper(context, R.style.BottomSheetAction)).apply {
                                text = action.title ?: action.titleResId?.let { getString(it) } ?: ""
                                setOnClickListener {
                                    action.onClick(this@BottomSheet)
                                    if (action.dismissAfterClick) dismiss()
                                }
                                action.iconResId?.let { iconResId ->
                                    setCompoundDrawablesRelativeWithIntrinsicBounds(iconResId, 0, 0, 0)
                                }
                            }
                        )
                    }
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet =
                d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    @Parcelize
    class Action(
        val titleResId: Int?,
        val title: String?,
        val iconResId: Int?,
        val dismissAfterClick: Boolean = true,
        val onClick: BottomSheet.() -> Unit
    ) : Parcelable

    class Builder {
        val items = mutableSetOf<Action>()

        fun action(
            @StringRes titleResId: Int,
            @DrawableRes iconResId: Int?,
            dismissAfterClick: Boolean = true,
            condition: Boolean = true,
            onClick: BottomSheet.() -> Unit
        ) {
            if (condition) items.add(Action(titleResId, null, iconResId, dismissAfterClick, onClick))
        }

        fun action(
            title: String,
            @DrawableRes iconResId: Int?,
            dismissAfterClick: Boolean = true,
            condition: Boolean = true,
            onClick: BottomSheet.() -> Unit
        ) {
            if (condition) items.add(Action(null, title, iconResId, dismissAfterClick, onClick))
        }
    }

    companion object {
        const val MENU_HEADER = "SHEET_HEADER"
        const val MENU_ACTIONS = "SHEET_ACTIONS"
        const val SHOW_PLACEHOLDER = "SHOW_PLACEHOLDER"

        fun show(
            header: String?,
            fragmentManager: FragmentManager,
            showPlaceHolderText: Boolean = true,
            itemBuilder: (Builder.() -> Unit)?
        ) {
            val builder = Builder()
            itemBuilder?.invoke(builder)
            BottomSheet().apply {
                arguments = bundleOf(
                    MENU_HEADER to header,
                    MENU_ACTIONS to builder.items,
                    SHOW_PLACEHOLDER to showPlaceHolderText
                )
                show(fragmentManager, null)
            }
        }
    }
}
