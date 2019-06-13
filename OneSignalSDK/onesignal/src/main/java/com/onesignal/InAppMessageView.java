package com.onesignal;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

import static com.onesignal.OSUtils.dpToPx;

class InAppMessageView {

    private static final int ACTIVITY_BACKGROUND_COLOR = Color.parseColor("#BB000000");
    private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;
    private static final int ACTIVITY_INIT_DELAY = 200;

    private static final int MARGIN_PX_SIZE = dpToPx(24);

    static abstract class InAppMessageController {
        void onMessageWasShown() {
        }

        void onMessageWasDismissed() {
        }
    }

    private int pageWidth;
    private int pageHeight;
    private WebViewManager.Position displayLocation;
    private WebView webView;
    private FrameLayout frameLayout;
    private DraggableRelativeLayout draggableRelativeLayout;
    private InAppMessageController messageController;

    InAppMessageView(@NonNull WebView webView, WebViewManager.Position displayLocation, int pageHeight) {
        this(webView, displayLocation, pageHeight, ConstraintLayout.LayoutParams.MATCH_PARENT);
    }

    InAppMessageView(@NonNull WebView webView, WebViewManager.Position displayLocation, int pageHeight, int pageWidth) {
        this.webView = webView;
        this.displayLocation = displayLocation;
        this.pageHeight = pageHeight;
        this.pageWidth = pageWidth;
    }

    void setWebView(WebView webView) {
        this.webView = webView;
    }

    void setMessageController(InAppMessageController messageController) {
        this.messageController = messageController;
    }

    void destroyView(WeakReference<Activity> weakReference) {
        if (weakReference.get() != null) {
            if (draggableRelativeLayout != null)
                draggableRelativeLayout.removeAllViews();
            if (frameLayout != null)
                frameLayout.removeAllViews();
        }
        frameLayout = null;
        draggableRelativeLayout = null;
        webView = null;
    }

    void showView(@NonNull Activity activity) {
        delayShowUntilAvailable(activity);
    }

    // TODO: Modal in portrait is to tall when using split screen mode
    // TODO: Edge case: Modal in portrait is to tall when using split screen mode
    void showInAppMessageView() {
        // Use pageHeight if we have it, otherwise use use full height of the Activity
        int pageWidth = this.pageWidth;
        int pageHeight = this.pageHeight;
        // If we have a height constraint; (Modal or Banner)
        //   1. Ensure we don't set a height higher than the screen height.
        //   2. Limit the width to either screen width or the height of the screen.
        //      - This is to make the modal width the same for landscape and portrait modes.
        Activity currentActivity = ActivityLifecycleHandler.curActivity;
        if (currentActivity != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            currentActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int usableHeight = metrics.heightPixels;
            int usableWidth = metrics.widthPixels;

            if (pageHeight != ConstraintLayout.LayoutParams.MATCH_PARENT) {
                pageHeight += (MARGIN_PX_SIZE * 2);
                pageHeight = Math.min(pageHeight, usableHeight -
                        ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && currentActivity.isInMultiWindowMode()) ?
                                0 : MARGIN_PX_SIZE));
                pageWidth = Math.min(getDisplayXSize(), getDisplayYSize());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && currentActivity.isInMultiWindowMode()) {
                    if (pageWidth > usableWidth) {
                        pageWidth = usableWidth;
                    }
                }
            }
        }

        RelativeLayout.LayoutParams relativeLayoutParams = new RelativeLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
        );
        relativeLayoutParams.setMargins(MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE);
        relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        boolean hasBackground = shouldHaveBackground();
        FrameLayout.LayoutParams frameLayoutParams = hasBackground ? createFrameLayout() : null;

        showDraggableView(relativeLayoutParams, frameLayoutParams,
                createDraggableLayout(pageHeight, displayLocation), createWindowLayout(pageWidth, pageHeight, hasBackground));
    }

    private int getDisplayXSize() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private int getDisplayYSize() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    private FrameLayout.LayoutParams createFrameLayout() {
        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(pageWidth, pageHeight);

        switch (displayLocation) {
            case TOP:
                frameLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                break;
            case BOTTOM:
                frameLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
            case CENTER:
            case DISPLAY:
                frameLayoutParams.gravity = Gravity.CENTER;
        }
        return frameLayoutParams;
    }

    private DraggableRelativeLayout.Params createDraggableLayout(int pageHeight, WebViewManager.Position displayLocation) {
        DraggableRelativeLayout.Params draggableParams = new DraggableRelativeLayout.Params();
        draggableParams.maxXPos = MARGIN_PX_SIZE;
        draggableParams.maxYPos = MARGIN_PX_SIZE;
        // TODO: Look into using positions from view's.
        //       Tried getLocationInWindow but it was always returning 0;
        draggableParams.height = pageHeight;
        if (pageHeight == -1) {
            draggableParams.height = pageHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        }

        switch (displayLocation) {
            case BOTTOM:
                draggableParams.posY = Resources.getSystem().getDisplayMetrics().heightPixels - pageHeight;
                break;
            case CENTER:
            case DISPLAY:
                draggableParams.posY = (Resources.getSystem().getDisplayMetrics().heightPixels / 2) - (pageHeight / 2);
        }

        draggableParams.dragDirection = displayLocation == WebViewManager.Position.TOP ?
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_UP :
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_DOWN;

        return draggableParams;
    }

    private WindowManager.LayoutParams createWindowLayout(int pageWidth, int pageHeight, boolean hasBackground) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : pageWidth,
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : pageHeight,
                // Display it on top of other application windows
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                // Don't let it grab the input focus
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        Activity currentActivity = ActivityLifecycleHandler.curActivity;
        if (currentActivity != null) {
            layoutParams.token = currentActivity.getWindow().getDecorView().getApplicationWindowToken();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            layoutParams.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        }
        if (!hasBackground) {
            switch (displayLocation) {
                case TOP:
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    break;
                case BOTTOM:
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    break;
            }
        }
        return layoutParams;
    }

    private void showDraggableView(final RelativeLayout.LayoutParams relativeLayoutParams,
                                   @Nullable final FrameLayout.LayoutParams frameParams,
                                   final DraggableRelativeLayout.Params draggableParams,
                                   final WindowManager.LayoutParams layoutParams) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                webView.setLayoutParams(relativeLayoutParams);

                // TODO: Handle curActivity NULL cases
                //   TODO:1: This seems to be null if the location prompt is shown
                //   TODO:2: Also null if consent was provided and another Activity focus event did not happen yet.
                //   TODO:3: Can also be null when just switching to the next in-app message
                // TODO: Setup ActivityAvailableListener, changing it to an observable instead.

                //Do not create view if app is not in focus
                Activity currentActivity = ActivityLifecycleHandler.curActivity;
                if (currentActivity != null) {
                    boolean hasBackground = shouldHaveBackground();
                    setUpDraggableLayout(currentActivity, frameParams, draggableParams);

                    if (hasBackground) {
                        setUpFrameLayout(currentActivity);
                        currentActivity.getWindowManager().addView(frameLayout, layoutParams);
                    } else {
                        currentActivity.getWindowManager().addView(draggableRelativeLayout, layoutParams);
                    }

                    if (messageController != null) {
                        messageController.onMessageWasShown();
                    }
                }
            }
        });
    }

    private void setUpFrameLayout(@NonNull Activity activity) {
        frameLayout = new FrameLayout(activity);
        frameLayout.addView(draggableRelativeLayout);
        frameLayout.setBackgroundColor(ACTIVITY_BACKGROUND_COLOR);
        frameLayout.setClipChildren(false);
    }

    private void setUpDraggableLayout(@NonNull Activity activity,
                                      @Nullable FrameLayout.LayoutParams frameParams,
                                      DraggableRelativeLayout.Params draggableParams) {
        draggableRelativeLayout = new DraggableRelativeLayout(activity);
        if (frameParams != null) {
            draggableRelativeLayout.setLayoutParams(frameParams);
        }
        draggableRelativeLayout.setParams(draggableParams);
        draggableRelativeLayout.setListener(new DraggableRelativeLayout.DraggableListener() {
            @Override
            void onDismiss() {
                finish();
            }
        });
        draggableRelativeLayout.addView(webView);
    }

    private boolean shouldHaveBackground() {
        boolean background = true;
        switch (displayLocation) {
            case TOP:
            case BOTTOM:
                background = false;
        }
        return background;
    }

    private void delayShowUntilAvailable(@NonNull Activity activity) {
        if (activity.getWindow().getDecorView().getApplicationWindowToken() != null) {
            showInAppMessageView();
            return;
        }
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity currentActivity = ActivityLifecycleHandler.curActivity;
                if (currentActivity != null) {
                    delayShowUntilAvailable(currentActivity);
                }
            }
        }, ACTIVITY_INIT_DELAY);
    }

    // Will be called through OSJavaScriptInterface.
    // If so let the presenter (Activity) know to start it's dismiss animation.
    void dismiss() {
        if (draggableRelativeLayout == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already");
            markAsDismissed();
            return;
        }

        draggableRelativeLayout.dismiss();
        finishAfterDelay();
    }

    // Finishing on a timer as continueSettling does not return false
    // when using smoothSlideViewTo on Android 4.4
    private void finishAfterDelay() {
        OSUtils.runOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                dismissLayout();
            }
        }, ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS);
    }

    private void finish() {
        OSUtils.runOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                dismissLayout();
            }
        }, 0);
    }

    private void dismissLayout() {
        if (draggableRelativeLayout != null) {
            draggableRelativeLayout.removeView(webView);
        }
        if (frameLayout != null && ActivityLifecycleHandler.curActivity != null) {
            ActivityLifecycleHandler.curActivity.getWindowManager().removeView(frameLayout);
        }
        markAsDismissed();
    }

    // Called from presenter when it is no longer visible. (Animation is done)
    private void markAsDismissed() {
        // Dereference so this can be cleaned up in the next GC
        frameLayout = null;
        draggableRelativeLayout = null;
        if (messageController != null) {
            messageController.onMessageWasDismissed();
        }
    }
}
