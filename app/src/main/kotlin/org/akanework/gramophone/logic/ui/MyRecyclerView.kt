package org.akanework.gramophone.logic.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import me.zhanghai.android.fastscroll.DefaultAnimationHelper
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.FixOnItemTouchListenerRecyclerView
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R

// Please don't try to understand it :) this abstracts away all the ugly details you rather not know
class MyRecyclerView(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int)
	: FixOnItemTouchListenerRecyclerView(context, attributeSet, defStyleAttr),
	AppBarLayout.OnOffsetChangedListener {
	constructor(context: Context, attributeSet: AttributeSet?)
			: this(context, attributeSet, 0)
	constructor(context: Context) : this(context, null)

	private var appBarLayout: AppBarLayout? = null
	private var ah: MyAnimationHelper? = null
	private var scrollInProgress = false
	private var scrollIsNatural = false

	fun setAppBar(appBarLayout: AppBarLayout) {
		this.appBarLayout = appBarLayout
		appBarLayout.addOnOffsetChangedListener(this)
	}

	private fun setAppBarExpanded(expanded: Boolean) {
		val behavior = (appBarLayout?.layoutParams as CoordinatorLayout.LayoutParams?)?.behavior
		val isExpanded = behavior is AppBarLayout.Behavior && behavior.topAndBottomOffset == 0
		if (isExpanded != expanded) {
			appBarLayout?.setExpanded(expanded, true)
		}
	}

	fun startSmoothScrollCompat(scroller: SmoothScroller) {
		scrollInProgress = true
		val isZero = (layoutManager as? LinearLayoutManager)
			?.findFirstVisibleItemPosition() == 0
		if (isZero && scroller.targetPosition > 0)
			setAppBarExpanded(false)
		layoutManager?.startSmoothScroll(scroller)
	}

	@Suppress("UNCHECKED_CAST")
	fun scrollToPositionWithOffsetCompat(position: Int, offset: Int) {
		val apb = (appBarLayout?.layoutParams as CoordinatorLayout.LayoutParams?)?.behavior
		val isExpanded = apb is AppBarLayout.Behavior && apb.topAndBottomOffset == 0
		if (appBarLayout != null && (position > 0 || offset > 0) && isExpanded) {
			// this is setAppBarExpanded(false) but it works without layout pass
			val behavior: CoordinatorLayout.Behavior<AppBarLayout>? =
				(appBarLayout!!.layoutParams as CoordinatorLayout.LayoutParams).behavior
						as CoordinatorLayout.Behavior<AppBarLayout>?
			val a = IntArray(2)
			behavior!!.onNestedPreScroll(
				appBarLayout!!.parent!! as CoordinatorLayout, appBarLayout!!,
				appBarLayout!!.parent!! as CoordinatorLayout, 0, Int.MAX_VALUE,
				a, 0
			)
			// in theory, we need to scroll recyclerview back by dy=-a[1] but it does not make
			// sense because the CollapsingToolbar is pushing away our scroll bar to bottom
			// and when the scroll bar goes back up after collapse, the scale is different anyway
			// and we have an ugly jump in the list. I appreciate any idea for improvements.
		}
		(layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(position, offset)
	}

	override fun onScrollStateChanged(state: Int) {
		super.onScrollStateChanged(state)
		if (state == SCROLL_STATE_DRAGGING) {
			scrollInProgress = true
			scrollIsNatural = true
		} else if (!scrollIsNatural && state == SCROLL_STATE_SETTLING) {
			scrollInProgress = true
		} else if (state == SCROLL_STATE_IDLE) {
			if (scrollInProgress && !scrollIsNatural) {
				val pos =
					(layoutManager as? LinearLayoutManager)?.findFirstCompletelyVisibleItemPosition()
				setAppBarExpanded(pos == 0)
			}
			scrollInProgress = false
			scrollIsNatural = false
		}
	}

	fun fastScroll(popupTextProvider: PopupTextProvider?, ihh: ItemHeightHelper?): FastScroller {
		return FastScrollerBuilder(this)
			.setViewHelper(RecyclerViewHelper(this, popupTextProvider, ihh))
			.also { builder -> builder.setAnimationHelper(MyAnimationHelper().also { ah = it }) }
			.useMd2Style()
			.setTrackDrawable(
				AppCompatResources.getDrawable(
					context,
					R.drawable.ic_transparent
				)!!
			)
			.build()
	}

	override fun onOffsetChanged(unused: AppBarLayout?, offset: Int) {
		if (offset == 0) ah?.hideScrollbar()
	}

	abstract class Adapter<VH : ViewHolder> : RecyclerView.Adapter<VH>() {
		final override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
			super.onAttachedToRecyclerView(recyclerView)
			onAttachedToRecyclerView(recyclerView as MyRecyclerView)
		}

		final override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
			super.onDetachedFromRecyclerView(recyclerView)
			onDetachedFromRecyclerView(recyclerView as MyRecyclerView)
		}

		open fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {}
		open fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {}
	}

	private inner class MyAnimationHelper : DefaultAnimationHelper(this) {
		var trackViewCache: View? = null
		var thumbViewCache: View? = null

		override fun showScrollbar(trackView: View, thumbView: View) {
			super.showScrollbar(trackView, thumbView)
			this.trackViewCache = trackView
			this.thumbViewCache = thumbView
		}

		override fun hideScrollbar(trackView: View, thumbView: View) {
			super.hideScrollbar(trackView, thumbView)
			this.trackViewCache = null
			this.thumbViewCache = null
		}

		fun hideScrollbar() {
			if (trackViewCache != null)
				hideScrollbar(trackViewCache!!, thumbViewCache!!)
		}
	}
}

abstract class DefaultItemHeightHelper : ItemHeightHelper {
	companion object {
		fun concatItemHeightHelper(one: ItemHeightHelper, oneCount: () -> Int, two: ItemHeightHelper): ItemHeightHelper {
			return object : DefaultItemHeightHelper() {
				override fun getItemHeightFromZeroTo(to: Int): Int {
					val oc = oneCount()
					val oh = one.getItemHeightFromZeroTo(to.coerceAtMost(oc))
					val th = if (to >= oc) two.getItemHeightFromZeroTo(to - oc) else 0
					return (oh + th)
				}
			}
		}
	}
}