package org.akanework.gramophone.logic.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior

class MyBottomSheetBehavior<T : View>(context: Context, attrs: AttributeSet) :
    BottomSheetBehavior<T>(context, attrs) {

    companion object {
        fun <T : View> from(v: T): MyBottomSheetBehavior<T> {
            return BottomSheetBehavior.from<T>(v) as MyBottomSheetBehavior<T>
        }
    }

    init {
        state = STATE_HIDDEN
        maxWidth = ViewGroup.LayoutParams.MATCH_PARENT
        isGestureInsetBottomIgnored = true
    }

    @SuppressLint("RestrictedApi")
    override fun isHideableWhenDragging(): Boolean {
        return false
    }

    @SuppressLint("RestrictedApi")
    override fun handleBackInvoked() {
        if (state != STATE_HIDDEN) {
            setHideableInternal(false)
        }
        super.handleBackInvoked()
        setHideableInternal(true)
    }
}


