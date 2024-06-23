package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * [MarqueeTextView] is a kind of [AppCompatTextView]
 * keeps the focus of the textView all the time so marquee
 * can be displayed properly without needing to set focus
 * manually.
 *
 * Noteworthy, when the mainThread is doing something, marquee
 * will reload and cause a fake "jitter". Use this wisely, don't
 * make it everywhere.
 */
class MarqueeTextView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(
    context,
    attrs,
    defStyleAttr,
) {
    init {
        isSingleLine = true
        ellipsize =
            android
                .text
                .TextUtils
                .TruncateAt
                .MARQUEE
        marqueeRepeatLimit = -1
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun isFocused(): Boolean = true

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        if (focused) {
            super.onFocusChanged(true, direction, previouslyFocusedRect)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (hasWindowFocus) {
            super.onWindowFocusChanged(true)
        }
    }
}
