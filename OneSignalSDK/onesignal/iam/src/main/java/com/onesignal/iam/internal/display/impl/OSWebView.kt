package com.onesignal.iam.internal.display.impl

import android.content.Context
import android.webkit.WebView

// Custom WebView to lock scrolling
internal class OSWebView(context: Context?) : WebView(context!!) {
    // The method overrides below; overScrollBy, scrollTo, and computeScroll prevent page scrolling
    public override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        return false
    }

    override fun scrollTo(x: Int, y: Int) {
        // Do nothing
    }

    override fun computeScroll() {
        // Do nothing
    }
}
