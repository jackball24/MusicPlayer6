package org.akanework.gramophone.ui.components

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

/**
 * CustomGridLayoutManager:
 *   A grid layout manager for making the grid view
 * intact.
 *
 * @author AkaneTan
 */
class CustomGridLayoutManager(
    context: Context,
    spanCount: Int,
) : GridLayoutManager(context, spanCount) {
    init {
        spanSizeLookup =
            object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (position == 0) {
                        spanCount
                    } else {
                        1
                    }
            }
    }
}
