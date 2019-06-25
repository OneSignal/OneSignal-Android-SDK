package com.onesignal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.util.Pair;
import android.support.v7.widget.CardView;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.webkit.WebView;
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

    private static final int ACTIVITY_BACKGROUND_COLOR_EMPTY = Color.parseColor("#00000000");
    private static final int ACTIVITY_BACKGROUND_COLOR_FULL = Color.parseColor("#BB000000");

    private static final int IN_APP_BANNER_ANIMATION_DURATION_MS = 1000;
    private static final int IN_APP_CENTER_ANIMATION_DURATION_MS = 1000;
    private static final int IN_APP_BACKGROUND_ANIMATION_DURATION_MS = 400;

    private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;
    private static final int ACTIVITY_INIT_DELAY = 200;
    private static final int MARGIN_PX_SIZE = dpToPx(24);

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
    private LinearLayout parentLinearLayout;
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
            if (draggableRelativeLayout != null)
                draggableRelativeLayout.removeAllViews();
            if (parentLinearLayout != null)
                parentLinearLayout.removeAllViews();
        }
        parentLinearLayout = null;
        draggableRelativeLayout = null;
        webView = null;
    }

    void showView(Activity activity) {
        delayShowUntilAvailable(activity);
    }

    void checkIfShouldDismiss() {
        if (shouldDismissWhenActive) {
            finishAfterDelay();
            shouldDismissWhenActive = false;
        }
    }

    private Activity getCurrentActivity() {
        return ActivityLifecycleHandler.curActivity;
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
        int pageWidth;
        int pageHeight;

        if (pair.first != null)
            pageWidth = pair.first;
        else
            return;

        if (pair.second != null)
            pageHeight = pair.second;
        else
            return;

        DraggableRelativeLayout.LayoutParams webViewLayoutParams = new DraggableRelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight
        );
        webViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        LinearLayout.LayoutParams linearLayoutParams = hasBackground ? createParentLinearLayoutParams() : null;

        showDraggableView(
                displayLocation,
                webViewLayoutParams,
                linearLayoutParams,
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

        Activity currentActivity = getCurrentActivity();
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

    private LinearLayout.LayoutParams createParentLinearLayoutParams() {
        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(pageWidth, LinearLayout.LayoutParams.MATCH_PARENT);

        switch (displayLocation) {
            case TOP:
                linearLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                break;
            case BOTTOM:
                linearLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
            case CENTER:
            case DISPLAY:
                linearLayoutParams.gravity = Gravity.CENTER;
        }

        return linearLayoutParams;
    }

    private DraggableRelativeLayout.Params createDraggableLayout(int pageHeight, WebViewManager.Position displayLocation) {
        DraggableRelativeLayout.Params draggableParams = new DraggableRelativeLayout.Params();
        draggableParams.maxXPos = MARGIN_PX_SIZE;
        draggableParams.maxYPos = MARGIN_PX_SIZE;

        draggableParams.messageHeight = pageHeight;
        draggableParams.height = getDisplayYSize();

        if (pageHeight == -1)
            draggableParams.messageHeight = pageHeight = getDisplayYSize() - (MARGIN_PX_SIZE * 2);

        switch (displayLocation) {
            case BOTTOM:
                draggableParams.posY = getDisplayYSize() - pageHeight;
                break;
            case CENTER:
            case DISPLAY:
                // Page height at -1 is a fullscreen message
                // When center modal, set the maxYPos as the top of the message height
                if (pageHeight != -1)
                    draggableParams.maxYPos = (getDisplayYSize() / 2) - (pageHeight / 2);

                draggableParams.posY = (getDisplayYSize() / 2) - (pageHeight / 2);
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
        Activity currentActivity = getCurrentActivity();
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
//                case CENTER:
//                case DISPLAY:
//                    layoutParams.gravity = Gravity.CENTER;
//                    break;
            }
        }
        return layoutParams;
    }

    private void showDraggableView(final WebViewManager.Position displayLocation,
                                   final RelativeLayout.LayoutParams relativeLayoutParams,
                                   final LinearLayout.LayoutParams linearLayoutParams,
                                   final DraggableRelativeLayout.Params webViewLayoutParams,
                                   final WindowManager.LayoutParams parentLinearLayoutParams) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                //Do not create view if app is not in focus
                Activity currentActivity = getCurrentActivity();
                if (currentActivity != null) {
                    webView.setLayoutParams(relativeLayoutParams);

                    setUpDraggableLayout(linearLayoutParams, webViewLayoutParams);
                    setUpParentLinearLayout();

                    currentActivity.getWindowManager().addView(parentLinearLayout, parentLinearLayoutParams);

                    if (messageController != null) {
                        animateInAppMessage(displayLocation, draggableRelativeLayout, parentLinearLayout);
                        messageController.onMessageWasShown();
                    }
                    initDismissIfNeeded();
                }
            }
        });
    }

    private void setUpParentLinearLayout() {
        parentLinearLayout = new LinearLayout(getCurrentActivity());
        parentLinearLayout.setBackgroundColor(ACTIVITY_BACKGROUND_COLOR_EMPTY);
        parentLinearLayout.setClipChildren(false);
        parentLinearLayout.setClipToPadding(false);
        parentLinearLayout.addView(draggableRelativeLayout);
    }

    private void setUpDraggableLayout(LinearLayout.LayoutParams linearLayoutParams,
                                      DraggableRelativeLayout.Params draggableParams) {
        draggableRelativeLayout = new DraggableRelativeLayout(getCurrentActivity());
        if (linearLayoutParams != null) {
            draggableRelativeLayout.setLayoutParams(linearLayoutParams);
        }
        draggableRelativeLayout.setParams(draggableParams);
        draggableRelativeLayout.setListener(new DraggableRelativeLayout.DraggableListener() {
            @Override
            void onDismiss() {
                finishAfterDelay();
            }
        });

        //If webView has parent remove it before adding a new parent
        if (webView.getParent() != null) {
            ((ViewGroup) webView.getParent()).removeAllViews();
        }

        CardView cardView = createCardView();
        cardView.addView(webView);

        draggableRelativeLayout.setPadding(MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE);
        draggableRelativeLayout.setClipChildren(false);
        draggableRelativeLayout.setClipToPadding(false);
        draggableRelativeLayout.addView(cardView);
    }

    /**
     * To show drop shadow on WebView
     * Layout container for WebView is needed
     */
    private CardView createCardView() {
        CardView cardView = new CardView(getCurrentActivity());

        RelativeLayout.LayoutParams cardViewLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, pageHeight);
        cardViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        cardView.setLayoutParams(cardViewLayoutParams);

        cardView.setRadius(dpToPx(8));
        cardView.setCardElevation(dpToPx(5));

        cardView.setClipChildren(false);
        cardView.setClipToPadding(false);
        cardView.setPreventCornerOverlap(false);
        return cardView;
    }

    /**
     * Schedule dismiss behavior
     */
    private void initDismissIfNeeded() {
        if (dismissDuration > 0 && dismissSchedule == null) {
            dismissSchedule = new Runnable() {
                public void run() {
                    if (getCurrentActivity() != null) {
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
    private void delayShowUntilAvailable(final Activity currentActivity) {
        if (currentActivity.getWindow().getDecorView().getApplicationWindowToken() != null) {
            showInAppMessageView();
            return;
        }
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                delayShowUntilAvailable(currentActivity);
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
                if (hasBackground) {
                    animateAndDismissLayout();
                } else {
                    removeViews();
                }
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
        if (draggableRelativeLayout != null) {
            draggableRelativeLayout.removeAllViews();
        }
        if (parentLinearLayout != null && getCurrentActivity() != null) {
            parentLinearLayout.setVisibility(View.INVISIBLE);
            getCurrentActivity().getWindowManager().removeView(parentLinearLayout);
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
        // Dereference so this can be cleaned up in the next GC
        parentLinearLayout = null;
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

    private void animateInAppMessage(WebViewManager.Position displayLocation, View messageView, View backgroundView) {
        // Based on the location of the in app message apply and animation to match
        switch (displayLocation) {
            case TOP:
                View topBannerMessageViewChild = ((ViewGroup) messageView).getChildAt(0);
                animateTop(topBannerMessageViewChild, webView.getHeight());
                break;
            case BOTTOM:
                View bottomBannerMessageViewChild = ((ViewGroup) messageView).getChildAt(0);
                animateBottom(bottomBannerMessageViewChild, webView.getHeight());
                break;
            case CENTER:
            case DISPLAY:
                animateCenter(messageView, backgroundView);
                break;
        }
    }

    private void animateTop(View messageView, int height) {
        // Animate the message view from above the screen downward to the top
        OneSignalAnimate.animateViewByTranslation(
                messageView,
                -height - MARGIN_PX_SIZE,
                0f,
                IN_APP_BANNER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                null)
                .start();
    }

    private void animateBottom(View messageView, int height) {
        // Animate the message view from under the screen upward to the bottom
        OneSignalAnimate.animateViewByTranslation(
                messageView,
                height + MARGIN_PX_SIZE,
                0f,
                IN_APP_BANNER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                null)
                .start();
    }

    private void animateCenter(View messageView, final View backgroundView) {
        // Animate the message view by scale since it settles at the center of the screen
        Animation messageAnimation = OneSignalAnimate.animateViewSmallToLarge(
                messageView,
                IN_APP_CENTER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                null);

        // Animate background behind the message so it doesn't just show the dark transparency
        ValueAnimator backgroundAnimation = animateBackgroundColor(
                backgroundView,
                IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
                ACTIVITY_BACKGROUND_COLOR_EMPTY,
                ACTIVITY_BACKGROUND_COLOR_FULL,
                null);

        messageAnimation.start();
        backgroundAnimation.start();
    }

    private void animateAndDismissLayout() {
        Animator.AnimatorListener animCallback = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeViews();
            }
        };

        // Animate background behind the message so it hides before being removed from the view
        animateBackgroundColor(
                parentLinearLayout,
                IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
                ACTIVITY_BACKGROUND_COLOR_FULL,
                ACTIVITY_BACKGROUND_COLOR_EMPTY,
                animCallback)
                .start();
    }

    private ValueAnimator animateBackgroundColor(View backgroundView, int duration, int startColor, int endColor, Animator.AnimatorListener animCallback) {
        return OneSignalAnimate.animateViewColor(
                backgroundView,
                duration,
                startColor,
                endColor,
                animCallback);
    }
}
