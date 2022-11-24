package org.qosp.notes.ui.utils

import android.content.Context
import android.text.Layout
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.postDelayed
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import org.qosp.notes.ui.utils.views.ExtendedEditText

fun Int.dp(context: Context): Int {
    return (context.resources.displayMetrics.density * this).toInt()
}

fun View.requestFocusAndKeyboard() {
    postDelayed(100) {
        if (this is ExtendedEditText) {
            requestFocusAndMoveCaret()
        } else {
            requestFocus()
        }

        if (hasWindowFocus()) return@postDelayed showKeyboard()

        viewTreeObserver.addOnWindowFocusChangeListener(
            object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (hasFocus) {
                        this@requestFocusAndKeyboard.showKeyboard()
                        viewTreeObserver.removeOnWindowFocusChangeListener(this)
                    }
                }
            }
        )
    }
}

fun View.showKeyboard() {
    val inputMethodManager = context.getSystemService<InputMethodManager>()
    inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.hideKeyboard() {
    val inputMethodManager = context.getSystemService<InputMethodManager>()
    inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.liftAppBarOnScroll(
    appBar: AppBarLayout,
    elevation: Float
) {
    appBar.postDelayed(300) {
        appBar.elevation = if (canScrollVertically(-1)) elevation else 0F
    }
    when (this) {
        is RecyclerView -> addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                appBar.elevation = if (canScrollVertically(-1)) elevation else 0F
            }
        })
        is ScrollView, is NestedScrollView -> {
            val listener = ViewTreeObserver.OnScrollChangedListener {
                appBar.elevation = if (canScrollVertically(-1)) elevation else 0F
            }

            viewTreeObserver.addOnScrollChangedListener(listener)

            appBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View?) { }

                override fun onViewDetachedFromWindow(v: View?) {
                    viewTreeObserver.removeOnScrollChangedListener(listener)
                }
            })
        }
    }
}

fun TextView.ellipsize() {
    viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                val maxLines: Int = maxLines
                if (layout != null) {
                    val layout: Layout = layout
                    if (layout.lineCount > maxLines) {
                        val end: Int = layout.getLineEnd(maxLines - 1)
                        setText(text.subSequence(0, end - 3), TextView.BufferType.SPANNABLE)
                        append("...")
                    }
                }
            }
        }
    )
}

inline fun DrawerLayout.closeAndThen(crossinline block: () -> Unit) {
    addDrawerListener(object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) { }

        override fun onDrawerOpened(drawerView: View) { }

        override fun onDrawerClosed(drawerView: View) {
            removeDrawerListener(this)
            block()
        }

        override fun onDrawerStateChanged(newState: Int) { }
    })

    close()
}
