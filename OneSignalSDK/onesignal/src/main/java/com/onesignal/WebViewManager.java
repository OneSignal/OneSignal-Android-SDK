package com.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
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

class WebViewManager extends ActivityLifecycleHandler.ActivityAvailableListener {

    private static final String TAG = WebViewManager.class.getCanonicalName();
    private static final int MARGIN_PX_SIZE = dpToPx(24);

    private static Map<String, WebViewManager> instances = new ConcurrentHashMap<>();

    private WebViewManager(@NonNull OSInAppMessage message, String base64Message) {
        this.message = message;
        this.base64Message = base64Message;
        if (!message.isPreview)
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
    private String base64Message;
    private InAppMessageView messageView;
    private int screenOrientation = -1;

    // Creates a new WebView
    static void showHTMLString(OSInAppMessage message, final String htmlStr) {
        if (!message.isPreview && instances.containsKey(message.messageId)) {
            OneSignal.Log(
                    OneSignal.LOG_LEVEL.ERROR,
                    "In-App message with id '" +
                            message.messageId +
                            "' already displayed or is already preparing to be display!");
            return;
        }

        // Web view must be created on the main thread.
        try {
            final String base64Str = Base64.encodeToString(
                    htmlStr.getBytes("UTF-8"),
                    Base64.NO_WRAP
            );

            final WebViewManager webViewManager = new WebViewManager(message, base64Str);

            OSUtils.runOnMainUIThread(new Runnable() {
                @Override
                public void run() {
                    webViewManager.setupWebView();
                }
            });
        } catch (UnsupportedEncodingException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Catch on  showHTMLString'" + e.getMessage());
            e.printStackTrace();
        }
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
            showMessageView(pageHeight, getDisplayLocation(jsonObject));
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

    @Override
    void available(@NonNull Activity activity) {
        if (OSViewUtils.isScreenRotated(activity, screenOrientation)) {
            if (messageView != null) {
                messageView.setWebView(webView);
                messageView.showView(activity);
            }
        }

        screenOrientation = activity.getResources().getConfiguration().orientation;
        messageView.checkIfShouldDismiss();
    }

    @Override
    void destroyed(WeakReference<Activity> reference) {
        if (messageView != null) {
            messageView.destroyView(reference);
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

        webView.loadData(base64Message, "text/html; charset=utf-8", "base64");
    }

    private void showMessageView(int pageHeight, Position displayLocation) {
        messageView = new InAppMessageView(webView, displayLocation, pageHeight, message.getDisplayDuration());
        messageView.setMessageController(new InAppMessageView.InAppMessageController() {
            @Override
            void onMessageWasShown() {
                OSInAppMessageController.onMessageWasShown(message);
            }

            @Override
            void onMessageWasDismissed() {
                OSInAppMessageController.getController().messageWasDismissed(message);
                ActivityLifecycleHandler.removeActivityAvailableListener(TAG + message.messageId);
                webView = null;
            }
        });
        messageView.showInAppMessageView();
        ActivityLifecycleHandler.setActivityAvailableListener(TAG + message.messageId, this);
    }

    // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
    private static void enableWebViewRemoteDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
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

    private void dismiss() {
        if (messageView != null) {
            messageView.dismiss();
            messageView = null;
        }
    }
}
