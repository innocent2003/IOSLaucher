package com.example.oslaucher.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Hosts the app grid pages stacked on top of each other.
 *
 * Unlike ViewFlipper, hidden pages are kept INVISIBLE instead of GONE, so every page stays
 * measured and laid out. Switching or swiping between pages then only toggles visibility
 * (a redraw), instead of forcing a full measure/layout pass and GridView re-binding all of
 * its cells on the first frame of the swipe, which is what made page changes stutter.
 */
class AppPagesLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var displayedChild: Int = 0
        set(value) {
            field = if (childCount == 0) 0 else value.coerceIn(0, childCount - 1)
            applyChildVisibility()
        }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        child.visibility = if (indexOfChild(child) == displayedChild) VISIBLE else INVISIBLE
    }

    override fun removeAllViews() {
        super.removeAllViews()
        displayedChild = 0
    }

    private fun applyChildVisibility() {
        for (index in 0 until childCount) {
            val target = if (index == displayedChild) VISIBLE else INVISIBLE
            val child = getChildAt(index)
            if (child.visibility != target) child.visibility = target
        }
    }
}
