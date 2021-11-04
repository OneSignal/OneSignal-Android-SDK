package com.onesignal;



import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.PopupWindowCompat;
import androidx.cardview.widget.CardView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.webkit.WebView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import static com.onesignal.OSViewUtils.dpToPx;

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

    private static final String IN_APP_MESSAGE_CARD_VIEW_TAG = "IN_APP_MESSAGE_CARD_VIEW_TAG";

    private static final int ACTIVITY_BACKGROUND_COLOR_EMPTY = Color.parseColor("#00000000");
    private static final int ACTIVITY_BACKGROUND_COLOR_FULL = Color.parseColor("#BB000000");

    private static final int IN_APP_BANNER_ANIMATION_DURATION_MS = 1000;
    private static final int IN_APP_CENTER_ANIMATION_DURATION_MS = 1000;
    private static final int IN_APP_BACKGROUND_ANIMATION_DURATION_MS = 400;

    private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;
    private static final int ACTIVITY_INIT_DELAY = 200;
    private static final int DRAG_THRESHOLD_PX_SIZE = dpToPx(4);
    private PopupWindow popupWindow;

    interface InAppMessageViewListener {
        void onMessageWasShown();
        void onMessageWillDismiss();
        void onMessageWasDismissed();
    }

    private Activity currentActivity;
    private final Handler handler = new Handler();
    private int pageWidth;
    private int pageHeight;
    private int marginPxSizeLeft = dpToPx(24);
    private int marginPxSizeRight = dpToPx(24);
    private int marginPxSizeTop = dpToPx(24);
    private int marginPxSizeBottom = dpToPx(24);
    private double displayDuration;
    private boolean hasBackground;
    private boolean shouldDismissWhenActive = false;
    private boolean isDragging = false;
    private boolean disableDragDismiss = false;
    private OSInAppMessageContent messageContent;
    @NonNull private WebViewManager.Position displayLocation;
    private WebView webView;
    private RelativeLayout parentRelativeLayout;
    private DraggableRelativeLayout draggableRelativeLayout;
    private InAppMessageViewListener messageController;
    private Runnable scheduleDismissRunnable;

    InAppMessageView(@NonNull WebView webView, @NonNull OSInAppMessageContent content, boolean disableDragDismiss) {
        this.webView = webView;
        this.displayLocation = content.getDisplayLocation();
        this.pageHeight = content.getPageHeight();
        this.pageWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        this.displayDuration = content.getDisplayDuration() == null ? 0 : content.getDisplayDuration();
        this.hasBackground = !displayLocation.isBanner();
        this.disableDragDismiss = disableDragDismiss;
        this.messageContent = content;
        setMarginsFromContent(content);
    }

    /**
     * For now we only support default margin or no margin.
     * Any non-zero value will be treated as default margin
     * @param content in app message content and style
     */
    private void setMarginsFromContent(OSInAppMessageContent content) {
        this.marginPxSizeTop = content.getUseHeightMargin() ? dpToPx(24) : 0;
        this.marginPxSizeBottom = content.getUseHeightMargin() ? dpToPx(24) : 0;
        this.marginPxSizeLeft = content.getUseWidthMargin() ? dpToPx(24) : 0;
        this.marginPxSizeRight = content.getUseWidthMargin() ? dpToPx(24) : 0;
    }

    void setWebView(WebView webView) {
        this.webView = webView;
        this.webView.setBackgroundColor(Color.TRANSPARENT);
    }

    void setMessageController(InAppMessageViewListener messageController) {
        this.messageController = messageController;
    }

    @NonNull WebViewManager.Position getDisplayPosition() {
        return displayLocation;
    }

    void showView(Activity activity) {
        delayShowUntilAvailable(activity);
    }

    void checkIfShouldDismiss() {
        if (shouldDismissWhenActive) {
            shouldDismissWhenActive = false;
            finishAfterDelay(null);
        }
    }
    
    /**
     * This will fired when the device is rotated for example with a new provided height for the WebView
     * Called to shrink or grow the WebView when it receives a JS resize event with a new height.
     *
     * @param pageHeight the provided height
     */
    void updateHeight(final int pageHeight) {
        this.pageHeight = pageHeight;
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                if (webView == null) {
                    OneSignal.onesignalLog(
                            OneSignal.LOG_LEVEL.WARN,
                            "WebView height update skipped, new height will be used once it is displayed.");
                    return;
                }

                ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
                layoutParams.height = pageHeight;
                // We only need to update the WebView size since it's parent layouts are set to
                //   WRAP_CONTENT to always match the height of the WebView. (Expect for fullscreen)
                webView.setLayoutParams(layoutParams);

                // draggableRelativeLayout comes in null here sometimes, this is due to the IAM
                //  not being ready to be shown yet
                // When preparing the IAM, the correct height will be set and handle this job, so
                //  all bases are covered and the draggableRelativeLayout will never have the wrong height
                if (draggableRelativeLayout != null)
                    draggableRelativeLayout.setParams(createDraggableLayoutParams(pageHeight, displayLocation, disableDragDismiss));
            }
        });
    }

    void showInAppMessageView(Activity currentActivity) {
        /* IMPORTANT
         * The only place where currentActivity should be assigned to InAppMessageView */
        this.currentActivity = currentActivity;

        DraggableRelativeLayout.LayoutParams webViewLayoutParams = new DraggableRelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight
        );
        webViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        RelativeLayout.LayoutParams relativeLayoutParams = hasBackground ? createParentRelativeLayoutParams() : null;

        showDraggableView(
                displayLocation,
                webViewLayoutParams,
                relativeLayoutParams,
                createDraggableLayoutParams(pageHeight, displayLocation, disableDragDismiss)
        );
    }

    private int getDisplayYSize() {
        return OSViewUtils.getWindowHeight(currentActivity);
    }

    private RelativeLayout.LayoutParams createParentRelativeLayoutParams() {
        RelativeLayout.LayoutParams relativeLayoutParams = new RelativeLayout.LayoutParams(pageWidth, RelativeLayout.LayoutParams.MATCH_PARENT);
        switch (displayLocation) {
            case TOP_BANNER:
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                relativeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                break;
            case BOTTOM_BANNER:
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                relativeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                break;
            case CENTER_MODAL:
            case FULL_SCREEN:
                relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        }

        return relativeLayoutParams;
    }

    private DraggableRelativeLayout.Params createDraggableLayoutParams(int pageHeight, WebViewManager.Position displayLocation, boolean disableDragging) {
        DraggableRelativeLayout.Params draggableParams = new DraggableRelativeLayout.Params();
        draggableParams.maxXPos = marginPxSizeRight;
        draggableParams.maxYPos = marginPxSizeTop;
        draggableParams.draggingDisabled = disableDragging;
        draggableParams.messageHeight = pageHeight;
        draggableParams.height = getDisplayYSize();

        switch (displayLocation) {
            case TOP_BANNER:
                draggableParams.dragThresholdY = marginPxSizeTop - DRAG_THRESHOLD_PX_SIZE;
                break;
            case BOTTOM_BANNER:
                draggableParams.posY = getDisplayYSize() - pageHeight;
                draggableParams.dragThresholdY = marginPxSizeBottom + DRAG_THRESHOLD_PX_SIZE;
                break;
            case FULL_SCREEN:
                draggableParams.messageHeight = pageHeight = getDisplayYSize() - (marginPxSizeBottom + marginPxSizeTop);
                // fall through for FULL_SCREEN since it shares similar params to CENTER_MODAL
            case CENTER_MODAL:
                int y = (getDisplayYSize() / 2) - (pageHeight / 2);
                draggableParams.dragThresholdY = y + DRAG_THRESHOLD_PX_SIZE;
                draggableParams.maxYPos = y;
                draggableParams.posY = y;
                break;
        }

        draggableParams.dragDirection = displayLocation == WebViewManager.Position.TOP_BANNER ?
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_UP :
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_DOWN;

        return draggableParams;
    }

    private void showDraggableView(final WebViewManager.Position displayLocation,
                                   final RelativeLayout.LayoutParams relativeLayoutParams,
                                   final RelativeLayout.LayoutParams draggableRelativeLayoutParams,
                                   final DraggableRelativeLayout.Params webViewLayoutParams) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                if (webView == null)
                    return;

                webView.setLayoutParams(relativeLayoutParams);

                Context context = currentActivity.getApplicationContext();
                setUpDraggableLayout(context, draggableRelativeLayoutParams, webViewLayoutParams);
                setUpParentRelativeLayout(context);
                createPopupWindow(parentRelativeLayout);

                if (messageController != null) {
                    animateInAppMessage(displayLocation, draggableRelativeLayout, parentRelativeLayout);
                }

                startDismissTimerIfNeeded();
            }
        });
    }

    /**
     * Create a new Android PopupWindow that draws over the current Activity
     *
     * @param parentRelativeLayout root layout to attach to the pop up window
     */
    private void createPopupWindow(@NonNull RelativeLayout parentRelativeLayout) {
        popupWindow = new PopupWindow(
                parentRelativeLayout,
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : pageWidth,
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : WindowManager.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setTouchable(true);
        // NOTE: This seems like the key to getting fullscreen under notches working?!
        popupWindow.setClippingEnabled(false);

        View container = (View)popupWindow.getContentView();
        System.out.println("container: " + container);
        System.out.println("container.getClass: " + container.getClass());

        int gravity = 0;
        if (!hasBackground) {
            switch (displayLocation) {
                case TOP_BANNER:
                    gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    break;
                case BOTTOM_BANNER:
                    gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    break;
                case CENTER_MODAL:
                case FULL_SCREEN:
                    gravity = Gravity.CENTER_HORIZONTAL;
                    break;
            }
        }

        // Using panel for fullbleed IAMs and dialog for non-fullbleed. The attached dialog type
        // does not allow content to bleed under notches but panel does.
        int displayType = this.messageContent.isFullScreen() ?
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL :
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        PopupWindowCompat.setWindowLayoutType(
                popupWindow,
                displayType
        );

        popupWindow.showAtLocation(
                currentActivity.getWindow().getDecorView().getRootView(),
                gravity,
                0,
                0
        );
    }

    private void setUpParentRelativeLayout(Context context) {
        parentRelativeLayout = new RelativeLayout(context);
        parentRelativeLayout.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        parentRelativeLayout.setClipChildren(false);
        parentRelativeLayout.setClipToPadding(false);
        parentRelativeLayout.addView(draggableRelativeLayout);
    }

    private void setUpDraggableLayout(final Context context,
                                      RelativeLayout.LayoutParams relativeLayoutParams,
                                      DraggableRelativeLayout.Params draggableParams) {
        draggableRelativeLayout = new DraggableRelativeLayout(context);
        if (relativeLayoutParams != null)
            draggableRelativeLayout.setLayoutParams(relativeLayoutParams);
        draggableRelativeLayout.setParams(draggableParams);
        draggableRelativeLayout.setListener(new DraggableRelativeLayout.DraggableListener() {
            @Override
            public void onDismiss() {
                if (messageController != null) {
                    messageController.onMessageWillDismiss();
                }
                finishAfterDelay(null);
            }

            @Override
            public void onDragStart() {
                isDragging = true;
            }

            @Override
            public void onDragEnd() {
                isDragging = false;
            }
        });

        if (webView.getParent() != null)
            ((ViewGroup) webView.getParent()).removeAllViews();

        CardView cardView = createCardView(context);
        cardView.setTag(IN_APP_MESSAGE_CARD_VIEW_TAG);
        cardView.addView(webView);

        draggableRelativeLayout.setPadding(marginPxSizeLeft, marginPxSizeTop, marginPxSizeRight, marginPxSizeBottom);
        draggableRelativeLayout.setClipChildren(false);
        draggableRelativeLayout.setClipToPadding(false);
        draggableRelativeLayout.addView(cardView);
    }

    /**
     * Simple getter to know when the MessageView is in a dragging state
     */
    boolean isDragging() {
        return isDragging;
    }

    /**
     * To show drop shadow on WebView
     * Layout container for WebView is needed
     */
    private CardView createCardView(Context context) {
        CardView cardView = new CardView(context);

        int height = displayLocation == WebViewManager.Position.FULL_SCREEN ?
                ViewGroup.LayoutParams.MATCH_PARENT :
                ViewGroup.LayoutParams.WRAP_CONTENT;
        RelativeLayout.LayoutParams cardViewLayoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        );
        cardViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        cardView.setLayoutParams(cardViewLayoutParams);

        // Set the initial elevation of the CardView to 0dp if using Android 6 API 23
        //  Fixes bug when animating a elevated CardView class
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
            cardView.setCardElevation(0);
        else
            cardView.setCardElevation(dpToPx(5));

        cardView.setRadius(dpToPx(8));
        cardView.setClipChildren(false);
        cardView.setClipToPadding(false);
        cardView.setPreventCornerOverlap(false);
        cardView.setBackgroundColor(Color.TRANSPARENT);

        return cardView;
    }

    /**
     * Schedule dismiss behavior, if IAM has a dismiss after X number of seconds timer.
     */
    private void startDismissTimerIfNeeded() {
        if (displayDuration <= 0)
            return;

        if (scheduleDismissRunnable != null)
            return;

        scheduleDismissRunnable = new Runnable() {
            public void run() {
                if (messageController != null) {
                    messageController.onMessageWillDismiss();
                }
                if (currentActivity != null) {
                    dismissAndAwaitNextMessage(null);
                    scheduleDismissRunnable = null;
                } else {
                    // For cases when the app is on background and the dismiss is triggered
                    shouldDismissWhenActive = true;
                }
            }
        };
        handler.postDelayed(scheduleDismissRunnable, (long) displayDuration * 1_000);
    }

    // Do not add view until activity is ready
    private void delayShowUntilAvailable(final Activity currentActivity) {
        if (OSViewUtils.isActivityFullyReady(currentActivity) && parentRelativeLayout == null) {
            showInAppMessageView(currentActivity);
            return;
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                delayShowUntilAvailable(currentActivity);
            }
        }, ACTIVITY_INIT_DELAY);
    }

    /**
     * Trigger the {@link #draggableRelativeLayout} dismiss animation
     */
    void dismissAndAwaitNextMessage(@Nullable WebViewManager.OneSignalGenericCallback callback) {
        if (draggableRelativeLayout == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already", new Throwable());
            dereferenceViews();
            if (callback != null)
                callback.onComplete();
            return;
        }

        draggableRelativeLayout.dismiss();
        finishAfterDelay(callback);
    }

    /**
     * Finishing on a timer as continueSettling does not return false
     * when using smoothSlideViewTo on Android 4.4
     */
    private void finishAfterDelay(final WebViewManager.OneSignalGenericCallback callback) {
        OSUtils.runOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                if (hasBackground && parentRelativeLayout != null)
                    animateAndDismissLayout(parentRelativeLayout, callback);
                else {
                    cleanupViewsAfterDismiss();
                    if (callback != null)
                        callback.onComplete();
                }
            }
        }, ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS);
    }

    /**
     * IAM has been fully dismissed, remove all views and call the onMessageWasDismissed callback
     */
    private void cleanupViewsAfterDismiss() {
        removeAllViews();
        if (messageController != null)
            messageController.onMessageWasDismissed();
    }

    /**
     * Remove all views and dismiss PopupWindow
     */
    void removeAllViews() {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "InAppMessageView removing views");
        if (scheduleDismissRunnable != null) {
            // Dismissed before the dismiss delay
            handler.removeCallbacks(scheduleDismissRunnable);
            scheduleDismissRunnable = null;
        }
        if (draggableRelativeLayout != null)
            draggableRelativeLayout.removeAllViews();

        if (popupWindow != null)
            popupWindow.dismiss();
        dereferenceViews();
    }

    /**
     * Cleans all layout references so this can be cleaned up in the next GC
     */
    private void dereferenceViews() {
        // Dereference so this can be cleaned up in the next GC
        parentRelativeLayout = null;
        draggableRelativeLayout = null;
        webView = null;
    }

    private void animateInAppMessage(WebViewManager.Position displayLocation, View messageView, View backgroundView) {
        final CardView messageViewCardView = messageView.findViewWithTag(IN_APP_MESSAGE_CARD_VIEW_TAG);

        Animation.AnimationListener cardViewAnimCallback = createAnimationListener(messageViewCardView);

        // Based on the location of the in app message apply and animation to match
        switch (displayLocation) {
            case TOP_BANNER:
                animateTop(messageViewCardView, webView.getHeight(), cardViewAnimCallback);
                break;
            case BOTTOM_BANNER:
                animateBottom(messageViewCardView, webView.getHeight(), cardViewAnimCallback);
                break;
            case CENTER_MODAL:
            case FULL_SCREEN:
                animateCenter(messageView, backgroundView, cardViewAnimCallback, null);
                break;
        }
    }

    private Animation.AnimationListener createAnimationListener(final CardView messageViewCardView) {
        return new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // For Android 6 API 23 devices, waits until end of animation to set elevation of CardView class
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                    messageViewCardView.setCardElevation(dpToPx(5));
                }
                if (messageController != null) {
                    messageController.onMessageWasShown();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
    }

    private void animateTop(View messageView, int height, Animation.AnimationListener cardViewAnimCallback) {
        // Animate the message view from above the screen downward to the top
        OneSignalAnimate.animateViewByTranslation(
                messageView,
                -height - marginPxSizeTop,
                0f,
                IN_APP_BANNER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                cardViewAnimCallback)
                .start();
    }

    private void animateBottom(View messageView, int height, Animation.AnimationListener cardViewAnimCallback) {
        // Animate the message view from under the screen upward to the bottom
        OneSignalAnimate.animateViewByTranslation(
                messageView,
                height + marginPxSizeBottom,
                0f,
                IN_APP_BANNER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                cardViewAnimCallback)
                .start();
    }

    private void animateCenter(View messageView, final View backgroundView, Animation.AnimationListener cardViewAnimCallback, Animator.AnimatorListener backgroundAnimCallback) {
        // Animate the message view by scale since it settles at the center of the screen
        Animation messageAnimation = OneSignalAnimate.animateViewSmallToLarge(
                messageView,
                IN_APP_CENTER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                cardViewAnimCallback);

        // Animate background behind the message so it doesn't just show the dark transparency
        ValueAnimator backgroundAnimation = animateBackgroundColor(
                backgroundView,
                IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
                ACTIVITY_BACKGROUND_COLOR_EMPTY,
                ACTIVITY_BACKGROUND_COLOR_FULL,
                backgroundAnimCallback);

        messageAnimation.start();
        backgroundAnimation.start();
    }

    private void animateAndDismissLayout(View backgroundView, final WebViewManager.OneSignalGenericCallback callback) {
        Animator.AnimatorListener animCallback = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cleanupViewsAfterDismiss();
                if (callback != null)
                    callback.onComplete();
            }
        };

        // Animate background behind the message so it hides before being removed from the view
        animateBackgroundColor(
                backgroundView,
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

    @Override
    public String toString() {
        return "InAppMessageView{" +
                "currentActivity=" + currentActivity +
                ", pageWidth=" + pageWidth +
                ", pageHeight=" + pageHeight +
                ", displayDuration=" + displayDuration +
                ", hasBackground=" + hasBackground +
                ", shouldDismissWhenActive=" + shouldDismissWhenActive +
                ", isDragging=" + isDragging +
                ", disableDragDismiss=" + disableDragDismiss +
                ", displayLocation=" + displayLocation +
                ", webView=" + webView +
                '}';
    }
}