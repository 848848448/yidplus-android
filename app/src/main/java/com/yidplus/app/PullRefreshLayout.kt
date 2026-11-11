package com.yidplus.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Pull to refresh that asks the page first. The site scrolls inside its own panels, so the
 * WebView itself is almost always "at the top"; the page tells us (on every touchstart)
 * whether the panel under the finger is really at its top.
 */
class PullRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwipeRefreshLayout(context, attrs) {

    @Volatile var pageAllowsPull: Boolean = false
    var webScrollY: () -> Int = { 0 }
    private var downPhase = false

    init {
        setOnChildScrollUpCallback { _, _ -> if (downPhase) false else (!pageAllowsPull || webScrollY() > 0) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            pageAllowsPull = false
            downPhase = true
            val r = super.onInterceptTouchEvent(ev)
            downPhase = false
            return r
        }
        return super.onInterceptTouchEvent(ev)
    }
}
