package com.onesignal.sdktest.ui;

import android.content.Context;
import com.google.android.material.appbar.AppBarLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;


public class FixAppBarLayoutBehavior extends AppBarLayout.Behavior {

    public FixAppBarLayoutBehavior() {
        super();
    }

    public FixAppBarLayoutBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout, AppBarLayout child, View target,
                               int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, type);
        stopNestedScrollIfNeeded(dyUnconsumed, child, target, type);
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, AppBarLayout child,
                                  View target, int dx, int dy, int[] consumed, int type) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type);
        stopNestedScrollIfNeeded(dy, child, target, type);
    }

    private void stopNestedScrollIfNeeded(int dy, AppBarLayout child, View target, int type) {
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            final int currOffset = getTopAndBottomOffset();
            if ((dy < 0 && currOffset == 0)
                    || (dy > 0 && currOffset == -child.getTotalScrollRange())) {
                ViewCompat.stopNestedScroll(target, ViewCompat.TYPE_NON_TOUCH);
            }
        }
    }
}