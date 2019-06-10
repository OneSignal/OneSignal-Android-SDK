package com.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.onesignal.OSUtils.dpToPx;

// Manages WebView instances by pre-loading them, displaying them, and closing them when dismissed.
//   Includes a static map for pre-loading, showing, and dismissed so these events can't be duplicated.

// Flow for Displaying WebView
// 1. showHTMLString - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. This calls showActivity which starts a new WebView
// 4. WebViewActivity will call WebViewManager.instanceFromIam(...) to get this instance and
//       add it's prepared WebView add add it to the Activity.

class WebViewManager {

    private static final int ACTIVITY_BACKGROUND_COLOR = Color.parseColor("#BB000000");
    private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;
    private static final int MARGIN_PX_SIZE = dpToPx(24);

    private static Map<String, WebViewManager> instances = new ConcurrentHashMap<>();

    private WebViewManager(@NonNull OSInAppMessage message) {
        this.message = message;
        instances.put(message.messageId, this);
    }

    enum Position {
        TOP,
        BOTTOM,
        CENTER,
        DISPLAY,
        ;
    }

    private WebView webView;
    private OSInAppMessage message;
    private FrameLayout frameLayout;
    private DraggableRelativeLayout draggableRelativeLayout;

    @Nullable
    static WebViewManager instanceFromIam(@Nullable String iamId) {
        return instances.get(iamId);
    }

    // Lets JS from the page send JSON payloads to this class
    private class OSJavaScriptInterface {
        private static final String JS_OBJ_NAME = "OSAndroid";

        @JavascriptInterface
        public void postMessage(String message) {
            try {
                JSONObject jsonObject = new JSONObject(message);
                String messageType = jsonObject.getString("type");

                if (messageType.equals("rendering_complete")) {
                    handleRenderComplete(jsonObject);
                } else if (messageType.equals("action_taken")) {
                    handleActionTaken(jsonObject);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void handleRenderComplete(JSONObject jsonObject) {
            int pageHeight = getPageHeightData(jsonObject);
            if (pageHeight != -1) {
                pageHeight = OSUtils.dpToPx(pageHeight);
            }
            addLayoutAndView(pageHeight, getDisplayLocation(jsonObject));
        }

        private int getPageHeightData(JSONObject jsonObject) {
            try {
                return jsonObject.getJSONObject("pageMetaData").getJSONObject("rect").getInt("height");
            } catch (JSONException e) {
                return -1;
            }
        }

        private Position getDisplayLocation(JSONObject jsonObject) {
            return Position.valueOf(jsonObject.optString("displayLocation", "display").toUpperCase());
        }

        private void handleActionTaken(JSONObject jsonObject) throws JSONException {
            JSONObject body = jsonObject.getJSONObject("body");
            String id = body.optString("id", null);
            if (id != null) {
                OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, body);
            }

            boolean close = body.getBoolean("close");
            if (close) {
                dismiss();
            }
        }
    }

    // TODO: Test with chrome://crash
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        enableWebViewRemoteDebugging();

        webView = new OSWebView(OneSignal.appContext);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);

        webView.getSettings().setJavaScriptEnabled(true);

        // Setup receiver for page events / data from JS
        webView.addJavascriptInterface(new OSJavaScriptInterface(), OSJavaScriptInterface.JS_OBJ_NAME);

        // Setting size before adding to Activity to prevent a resize event.
        // Also setting up sizes so JS can give us the correct content height for modals and banners
        //   Min on width and max on height is used to give us a consistent height from JS.
        //   The Activity will shrink the height if needed when rotating.
        int xSize = getWebViewXSize();
        int ySize = getWebViewYSize();
        webView.setLeft(0);
        webView.setRight(Math.min(ySize, xSize));
        // TODO: When shrinking image based on WebView port height setBottom may need to be tweaked
        // NOTE: If setTop and / or setBottom are NOT set on Android 5.0 (Chrome 72) it calcs width as 0 somehow...
        webView.setTop(0);
        webView.setBottom(Math.max(ySize, xSize));

        // TODO: Look into using setInitialScale if WebView does not fit
        //       Default size is dp * 100
        // webView.setInitialScale(350);
    }

    // Creates a new WebView
    static void showHTMLString(OSInAppMessage message, final String htmlStr) {
        if (instances.containsKey(message.messageId)) {
            OneSignal.Log(
                    OneSignal.LOG_LEVEL.ERROR,
                    "In-App message with id '" +
                            message.messageId +
                            "' already displayed or is already preparing to be display!");
            return;
        }

        final WebViewManager webViewManager = new WebViewManager(message);

        // Web view must be created on the main thread.
        try {
            final String base64Str = Base64.encodeToString(
                    htmlStr.getBytes("UTF-8"),
                    Base64.DEFAULT
            );

            OSUtils.runOnMainUIThread(new Runnable() {
                @Override
                public void run() {
                    webViewManager.setupWebView();
                    webViewManager.webView.loadData(base64Str, "text/html; charset=utf-8", "base64");
                }
            });
        } catch (UnsupportedEncodingException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Catch on  showHTMLString'" + e.getMessage());
            e.printStackTrace();
        }
    }

    // TODO: Modal in portrait is to tall when using split screen mode
    // TODO: Edge case: Modal in portrait is to tall when using split screen mode
    private void addLayoutAndView(int pageHeight, Position displayLocation) {
        // Use pageHeight if we have it, otherwise use use full height of the Activity
        int pageWidth = ConstraintLayout.LayoutParams.MATCH_PARENT;
        // If we have a height constraint; (Modal or Banner)
        //   1. Ensure we don't set a height higher than the screen height.
        //   2. Limit the width to either screen width or the height of the screen.
        //      - This is to make the modal width the same for landscape and portrait modes.
        if (pageHeight != ConstraintLayout.LayoutParams.MATCH_PARENT) {
            pageHeight += (MARGIN_PX_SIZE * 2);
            pageHeight = Math.min(pageHeight, getWebViewYSize() + (MARGIN_PX_SIZE * 2));
            pageWidth = Math.min(getWebViewXSize() + (MARGIN_PX_SIZE * 2), getWebViewYSize() + (MARGIN_PX_SIZE * 3));
        }

        final RelativeLayout.LayoutParams relativeLayoutParams = new RelativeLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
        );
        relativeLayoutParams.setMargins(MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE);
        relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

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

        showDraggableView(relativeLayoutParams, frameLayoutParams,
                createDraggableLayout(pageHeight, displayLocation), createWindowLayout());
    }

    private DraggableRelativeLayout.Params createDraggableLayout(int pageHeight, Position displayLocation) {
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

        draggableParams.dragDirection = displayLocation == Position.TOP ?
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_UP :
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_DOWN;

        return draggableParams;
    }

    private WindowManager.LayoutParams createWindowLayout() {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                // Display it on top of other application windows
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                // Don't let it grab the input focus
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        return layoutParams;
    }

    private void showDraggableView(final RelativeLayout.LayoutParams relativeLayoutParams,
                                   final FrameLayout.LayoutParams frameParams,
                                   final DraggableRelativeLayout.Params draggableParams,
                                   final WindowManager.LayoutParams layoutParams) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                webView.setLayoutParams(relativeLayoutParams);
                draggableRelativeLayout = new DraggableRelativeLayout(OneSignal.appContext);
                draggableRelativeLayout.setLayoutParams(frameParams);
                draggableRelativeLayout.setParams(draggableParams);
                draggableRelativeLayout.setListener(new DraggableRelativeLayout.DraggableListener() {
                    @Override
                    void onDismiss() {
                        finish();
                    }
                });
                draggableRelativeLayout.addView(webView);

                // TODO: Handle curActivity NULL cases
                //   TODO:1: This seems to be null if the location prompt is shown
                //   TODO:2: Also null if consent was provided and another Activity focus event did not happen yet.
                //   TODO:3: Can also be null when just switching to the next in-app message
                // TODO: Setup ActivityAvailableListener, changing it to an observable instead.
                Activity currentActivity = ActivityLifecycleHandler.curActivity;
                if (currentActivity != null) {
                    frameLayout = new FrameLayout(currentActivity);
                    frameLayout.addView(draggableRelativeLayout);
                    frameLayout.setBackgroundColor(ACTIVITY_BACKGROUND_COLOR);
                    frameLayout.setClipChildren(false);
                    currentActivity.getWindowManager().addView(frameLayout, layoutParams);
                    OSInAppMessageController.onMessageWasShown(message);
                }
            }
        });
    }

    // Another possible way to get the size. Should include the status bar...
    // getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
    private int getWebViewXSize() {
        return Resources.getSystem().getDisplayMetrics().widthPixels - (MARGIN_PX_SIZE * 2);
    }

    private int getWebViewYSize() {
        // 24dp is a best estimate of the status bar.
        // Getting the size correct will prevent a redraw of the WebView
        return Resources.getSystem().getDisplayMetrics().heightPixels - (MARGIN_PX_SIZE * 2) - dpToPx(24);
    }

    // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
    private static void enableWebViewRemoteDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    // Will be called through OSJavaScriptInterface.
    // If so let the presenter (Activity) know to start it's dismiss animation.
    private void dismiss() {
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
        if (frameLayout != null) {
            ActivityLifecycleHandler.curActivity.getWindowManager().removeView(frameLayout);
        }
        markAsDismissed();
    }

    // Called from presenter when it is no longer visible. (Animation is done)
    private void markAsDismissed() {
        // Dereference so this can be cleaned up in the next GC
        webView = null;
        frameLayout = null;
        draggableRelativeLayout = null;
        OSInAppMessageController.getController().messageWasDismissed(message);
    }
}
