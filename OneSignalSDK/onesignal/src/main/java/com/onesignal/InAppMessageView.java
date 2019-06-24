package com.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.util.Pair;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

import static com.onesignal.OSUtils.dpToPx;

/**
 * Layout Documentation
 * ### Modals & Banners ###
 *  - WebView
 *      - width  = MATCH_PARENT
 *      - height = PX height provided via a JS event for the content
 *  - Parent Layouts
 *      - width  = MATCH_PARENT
 *      - height = WRAP_CONTENT - Since the WebView is providing the height.
 * ### Fullscreen ###
 *  - WebView
 *      - width  = MATCH_PARENT
 *      - height = MATCH_PARENT
 *  - Parent Layouts
 *      - width  = MATCH_PARENT
 *      - height = MATCH_PARENT
 */
class InAppMessageView {

    private static final int ACTIVITY_BACKGROUND_COLOR = Color.parseColor("#BB000000");
    private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;
    private static final int ACTIVITY_INIT_DELAY = 200;
    private static final int MARGIN_PX_SIZE = dpToPx(24);
    private static final int SMALL_MARGIN_PX_SIZE = dpToPx(6);

    static abstract class InAppMessageController {
        void onMessageWasShown() {
        }

        void onMessageWasDismissed() {
        }
    }

    private final Handler handler = new Handler();
    private int pageWidth;
    private int pageHeight;
    private double dismissDuration;
    private boolean hasBackground;
    private boolean shouldDismissWhenActive = false;
    private WebViewManager.Position displayLocation;
    private WebView webView;
    private LinearLayout shadowLinearLayout;
    private FrameLayout backgroundFrameLayout;
    private DraggableRelativeLayout draggableRelativeLayout;
    private InAppMessageController messageController;
    private Runnable dismissSchedule;

    InAppMessageView(@NonNull WebView webView, WebViewManager.Position displayLocation, int pageHeight, double dismissDuration) {
        this(webView, displayLocation, pageHeight, ConstraintLayout.LayoutParams.MATCH_PARENT, dismissDuration);
    }

    private InAppMessageView(@NonNull WebView webView, WebViewManager.Position displayLocation, int pageHeight, int pageWidth, double dismissDuration) {
        this.webView = webView;
        this.displayLocation = displayLocation;
        this.pageHeight = pageHeight;
        this.pageWidth = pageWidth;
        this.dismissDuration = dismissDuration;
        this.hasBackground = isBanner();
    }

    void setWebView(WebView webView) {
        this.webView = webView;
    }

    void setMessageController(InAppMessageController messageController) {
        this.messageController = messageController;
    }

    void destroyView(WeakReference<Activity> weakReference) {
        if (weakReference.get() != null) {
            if (shadowLinearLayout != null)
                shadowLinearLayout.removeAllViews();
            if (draggableRelativeLayout != null)
                draggableRelativeLayout.removeAllViews();
            if (backgroundFrameLayout != null)
                backgroundFrameLayout.removeAllViews();
        }
        shadowLinearLayout = null;
        backgroundFrameLayout = null;
        draggableRelativeLayout = null;
        webView = null;
    }

    void showView(@NonNull Activity activity) {
        delayShowUntilAvailable(activity);
    }

    void checkIfShouldDismiss() {
        if (shouldDismissWhenActive) {
            finishAfterDelay();
            shouldDismissWhenActive = false;
        }
    }

    /**
     * This will fired when the device is rotated for example with a new provided height for the WebView
     * Called to shrink or grow the WebView when it receives a JS resize event with a new height.
     *
     * @param pageHeight the provided height
     */
    void updateHeight(final int pageHeight) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
                layoutParams.height = pageHeight;
                // We only need to update the WebView size since it's parent layouts are set to
                //   WRAP_CONTENT to always match the height of the WebView. (Expect for fullscreen)
                webView.setLayoutParams(layoutParams);
            }
        });
    }

    void showInAppMessageView() {
        Pair<Integer, Integer> pair = getWidthHeight();
        int pageWidth = pair.first;
        int pageHeight = pair.second;

        RelativeLayout.LayoutParams webViewLayoutParams = new RelativeLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                pageHeight
        );
        webViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        FrameLayout.LayoutParams frameLayoutParams = hasBackground ? createBackgroundLayout() : null;

        showDraggableView(
                webViewLayoutParams,
                frameLayoutParams,
                createDraggableLayout(pageHeight, displayLocation),
                createWindowLayout(pageWidth)
        );
    }

    /**
     * If we have a height constraint; (Modal or Banner)
     * 1. Ensure we don't set a height higher than the screen height.
     * 2. Limit the width to either screen width or the height of the screen.
     * - This is to make the modal width the same for landscape and portrait modes.
     * 3. Use Available width for cases when the height of the view is bigger than the usable height
     * 4. Limit usable width and height for split mode
     */
    private Pair<Integer, Integer> getWidthHeight() {
        int pageWidth = this.pageWidth;
        int pageHeight = this.pageHeight;

        Activity currentActivity = ActivityLifecycleHandler.curActivity;
        if (currentActivity != null) {
            if (pageHeight != ConstraintLayout.LayoutParams.MATCH_PARENT) {
                DisplayMetrics metrics = new DisplayMetrics();
                currentActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int usableHeight = metrics.heightPixels;
                int usableWidth = metrics.widthPixels;

                pageHeight += (MARGIN_PX_SIZE * 2);
                pageWidth = Math.min(getDisplayXSize(), getDisplayYSize());

                if (pageHeight > usableHeight ||
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                currentActivity.isInMultiWindowMode() && pageWidth > usableWidth) {
                    pageWidth = usableWidth;
                }

                pageHeight = Math.min(pageHeight, usableHeight -
                        ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && currentActivity.isInMultiWindowMode()) ?
                                0 : MARGIN_PX_SIZE));
            }
        }
        return new Pair<>(pageWidth, pageHeight);
    }

    private int getDisplayXSize() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private int getDisplayYSize() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    private FrameLayout.LayoutParams createBackgroundLayout() {
        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(pageWidth, FrameLayout.LayoutParams.WRAP_CONTENT);

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
        draggableParams.maxXPos = hasBackground ? MARGIN_PX_SIZE : MARGIN_PX_SIZE - SMALL_MARGIN_PX_SIZE;
        draggableParams.maxYPos = hasBackground ? MARGIN_PX_SIZE : MARGIN_PX_SIZE - SMALL_MARGIN_PX_SIZE;
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

    private WindowManager.LayoutParams createWindowLayout(int pageWidth) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : pageWidth,
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : WindowManager.LayoutParams.WRAP_CONTENT,
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

    private void showDraggableView(final RelativeLayout.LayoutParams webViewLayoutParams,
                                   @Nullable final FrameLayout.LayoutParams backgroundLayoutParams,
                                   final DraggableRelativeLayout.Params draggableLayoutParams,
                                   final WindowManager.LayoutParams layoutParams) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                //Do not create view if app is not in focus
                Activity currentActivity = ActivityLifecycleHandler.curActivity;
                if (currentActivity != null) {
                    webView.setLayoutParams(webViewLayoutParams);
                    draggableRelativeLayout = createDraggableLayout(currentActivity, backgroundLayoutParams, draggableLayoutParams);

                    if (hasBackground) {
                        backgroundFrameLayout = createBackgroundLayout(currentActivity);
                        currentActivity.getWindowManager().addView(backgroundFrameLayout, layoutParams);
                    } else {
                        currentActivity.getWindowManager().addView(draggableRelativeLayout, layoutParams);
                    }

                    if (messageController != null) {
                        messageController.onMessageWasShown();
                    }
                    initDismissIfNeeded();
                }
            }
        });
    }

    /**
     * Creates the background layout
     * <p>
     * {@link #draggableRelativeLayout} must be setup before this method is call
     *
     * @param context the FrameLayout context
     * @return the background frame layout
     */
    private FrameLayout createBackgroundLayout(@NonNull Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(ACTIVITY_BACKGROUND_COLOR);
        frameLayout.addView(draggableRelativeLayout);
        frameLayout.setClipChildren(false);
        return frameLayout;
    }

    /**
     * Creates the draggable layout with the corresponding child to be set
     *
     * @param context the DraggableRelativeLayout context
     * @return the draggable layout
     */
    private DraggableRelativeLayout createDraggableLayout(@NonNull Context context,
                                                          @Nullable FrameLayout.LayoutParams frameParams,
                                                          DraggableRelativeLayout.Params draggableParams) {
        DraggableRelativeLayout draggableRelativeLayout = new DraggableRelativeLayout(context);
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

        //If webView has parent remove it before adding a new parent
        if (webView.getParent() != null) {
            ((ViewGroup) webView.getParent()).removeAllViews();
        }

        View childView = webView;
        int padding = MARGIN_PX_SIZE;

        if (!hasBackground) {
            LinearLayout linearLayout = createLinearLayout(context);
            linearLayout.addView(webView);
            padding = padding - SMALL_MARGIN_PX_SIZE;
            childView = shadowLinearLayout = linearLayout;
        }

        draggableRelativeLayout.setPadding(padding, padding, padding, padding);
        draggableRelativeLayout.setClipToPadding(false);
        draggableRelativeLayout.addView(childView);
        return draggableRelativeLayout;
    }

    /**
     * To show drop shadow on WebView
     * Layout container for WebView is needed
     *
     * @param context the LinearLayout context
     */
    private LinearLayout createLinearLayout(@NonNull Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        linearLayout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        linearLayout.setLayoutParams(params);
        linearLayout.setPadding(SMALL_MARGIN_PX_SIZE, SMALL_MARGIN_PX_SIZE, SMALL_MARGIN_PX_SIZE, SMALL_MARGIN_PX_SIZE);
        return linearLayout;
    }

    /**
     * Schedule dismiss behavior
     */
    private void initDismissIfNeeded() {
        if (dismissDuration > 0 && dismissSchedule == null) {
            dismissSchedule = new Runnable() {
                public void run() {
                    if (ActivityLifecycleHandler.curActivity != null) {
                        dismiss();
                        dismissSchedule = null;
                    } else {
                        //for cases when the app is on background and the dismiss is triggered
                        shouldDismissWhenActive = true;
                    }
                }
            };

            handler.postDelayed(dismissSchedule, (long) dismissDuration * 1_000);
        }
    }

    /**
     * Do not add view until activity is ready
     * To check if activity is ready, token must not be null
     *
     * @param currentActivity the activity where to show the view
     */
    private void delayShowUntilAvailable(@NonNull Activity currentActivity) {
        if (currentActivity.getWindow().getDecorView().getApplicationWindowToken() != null) {
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

    /**
     * Trigger the {@link #draggableRelativeLayout} dismiss animation
     */
    void dismiss() {
        if (draggableRelativeLayout == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already");
            markAsDismissed();
            return;
        }

        draggableRelativeLayout.dismiss();
        finishAfterDelay();
    }

    /**
     * Finishing on a timer as continueSettling does not return false
     * when using smoothSlideViewTo on Android 4.4
     */
    private void finishAfterDelay() {
        OSUtils.runOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                removeViews();
            }
        }, ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS);
    }

    private void finish() {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                removeViews();
            }
        });
    }

    /**
     * Remove references from the views
     */
    private void removeViews() {
        if (dismissSchedule != null) {
            //dismissed before the dismiss delay
            handler.removeCallbacks(dismissSchedule);
            dismissSchedule = null;
        }
        if (shadowLinearLayout != null) {
            shadowLinearLayout.removeAllViews();
        }
        if (draggableRelativeLayout != null) {
            if (backgroundFrameLayout == null && ActivityLifecycleHandler.curActivity != null) {
                ActivityLifecycleHandler.curActivity.getWindowManager().removeView(draggableRelativeLayout);
            }
            draggableRelativeLayout.removeAllViews();
        }
        if (backgroundFrameLayout != null) {
            if (ActivityLifecycleHandler.curActivity != null) {
                ActivityLifecycleHandler.curActivity.getWindowManager().removeView(backgroundFrameLayout);
            }
            backgroundFrameLayout.removeAllViews();
        }
        if (messageController != null) {
            messageController.onMessageWasDismissed();
        }
        markAsDismissed();
    }

    /**
     * Cleans all layout references so this can be cleaned up in the next GC
     */
    private void markAsDismissed() {
        shadowLinearLayout = null;
        backgroundFrameLayout = null;
        draggableRelativeLayout = null;
        webView = null;
    }

    /**
     * TOP and BOTTOM display location are for banner cases
     */
    private boolean isBanner() {
        switch (displayLocation) {
            case TOP:
            case BOTTOM:
                return false;
        }
        return true;
    }
}
