package com.onesignal;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

import static com.onesignal.OSViewUtils.dpToPx;

// Manages WebView instances by pre-loading them, displaying them, and closing them when dismissed.
//   Includes a static map for pre-loading, showing, and dismissed so these events can't be duplicated.

// Flow for Displaying WebView
// 1. showHTMLString - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. This calls showActivity which starts a new WebView
// 4. WebViewActivity will call WebViewManager.instanceFromIam(...) to get this instance and
//       add it's prepared WebView add add it to the Activity.

@TargetApi(Build.VERSION_CODES.KITKAT)
class WebViewManager extends ActivityLifecycleHandler.ActivityAvailableListener {

    private static final String TAG = WebViewManager.class.getCanonicalName();
    private static final int MARGIN_PX_SIZE = dpToPx(24);
    private static final int IN_APP_MESSAGE_INIT_DELAY = 200;

    enum Position {
        TOP_BANNER,
        BOTTOM_BANNER,
        CENTER_MODAL,
        FULL_SCREEN,
        ;

        boolean isBanner() {
            switch (this) {
                case TOP_BANNER:
                case BOTTOM_BANNER:
                    return true;
            }
            return false;
        }
    }

    @Nullable private OSWebView webView;
    @Nullable private InAppMessageView messageView;

    @Nullable protected static WebViewManager lastInstance = null;

    @NonNull private Activity activity;
    @NonNull private OSInAppMessage message;

    private boolean firstShow = true;

    interface OneSignalGenericCallback {
        void onComplete();
    }

    protected WebViewManager(@NonNull OSInAppMessage message, @NonNull Activity activity) {
        this.message = message;
        this.activity = activity;
    }

    /**
     * Creates a new WebView
     * Dismiss WebView if already showing one and the new one is a Preview
     *
     * @param message the message to show
     * @param htmlStr the html to display on the WebView
     */
    static void showHTMLString(@NonNull final OSInAppMessage message, @NonNull final String htmlStr) {
        final Activity currentActivity = ActivityLifecycleHandler.curActivity;
        /* IMPORTANT
         * This is the starting route for grabbing the current Activity and passing it to InAppMessageView */
        if (currentActivity != null) {
            // Only a preview will be dismissed, this prevents normal messages from being
            // removed when a preview is sent into the app
            if (lastInstance != null && message.isPreview) {
                // Created a callback for dismissing a message and preparing the next one
                lastInstance.dismissAndAwaitNextMessage(new OneSignalGenericCallback() {
                    @Override
                    public void onComplete() {
                        lastInstance = null;
                        initInAppMessage(currentActivity, message, htmlStr);
                    }
                });
            }
            else
                initInAppMessage(currentActivity, message, htmlStr);
            return;
        }

        /* IMPORTANT
         * Loop the setup for in app message until curActivity is not null */
        Looper.prepare();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showHTMLString(message, htmlStr);
            }
        }, IN_APP_MESSAGE_INIT_DELAY);
    }

    static void dismissCurrentInAppMessage() {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "WebViewManager IAM dismissAndAwaitNextMessage lastInstance: " + lastInstance);
        if (lastInstance != null) {
            lastInstance.dismissAndAwaitNextMessage(null);
        }
    }

    private static void initInAppMessage(@NonNull final Activity currentActivity, @NonNull OSInAppMessage message, @NonNull String htmlStr) {
        htmlStr =
                "<html>\n" +
                "<head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\"/>\n" +
                "<style>\n" +
                "    * {\n" +
                "        -webkit-touch-callout: none;\n" +
                "        -webkit-user-select: none; /* Disable selection/copy in UIWebView */\n" +
                "    }\n" +
                "    h1 {\n" +
                "        font-weight: 400;\n" +
                "    }\n" +
                "    body {\n" +
                "        font-family: -apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Oxygen-Sans,Ubuntu,Cantarell,\"Helvetica Neue\",sans-serif;\n" +
                "        padding: 16px;\n" +
                "        overflow: hidden;\n" +
                "        cursor: pointer;background-image: url(https://img.onesignal.com/i/228dacaf-62c3-42f1-a625-ff699006b01c);\n" +
                "            background-position: center;\n" +
                "            background-repeat: no-repeat;\n" +
                "            background-size: cover;background-color: #FFFFFF;\n" +
                "        \n" +
                "    }\n" +
                "\n" +
                "    .flex-container {\n" +
                "        display: flex;\n" +
                "        flex-direction: column;\n" +
                "        height: 100%;\n" +
                "    }\n" +
                "\n" +
                "    /* top level elements */\n" +
                "    .flex-container > * {\n" +
                "        margin-top: 8px;\n" +
                "        margin-bottom: 8px;\n" +
                "    }\n" +
                "\n" +
                "    /* Image only for Fullscreen and Modal */\n" +
                "    .image-container {\n" +
                "        display: flex;\n" +
                "        justify-content: center;\n" +
                "        flex-direction: column;\n" +
                "    }\n" +
                "</style>\n" +
                "<script>\n" +
                "    // Called from onClick of images, buttons, and dismiss button\n" +
                "    function actionTaken(data, clickType) {\n" +
                "        console.log(\"actionTaken(): \" + JSON.stringify(data));\n" +
                "        if (clickType)\n" +
                "            data[\"click_type\"] = clickType;\n" +
                "        postMessageToNative({ type: \"action_taken\", body: data });\n" +
                "    }\n" +
                "\n" +
                "    function postMessageToNative(msgJson) {\n" +
                "        console.log(\"postMessageToNative(): \" + JSON.stringify(msgJson));\n" +
                "        var encodedMsg = JSON.stringify(msgJson);\n" +
                "        postMessageToIos(encodedMsg);\n" +
                "        postMessageToAndroid(encodedMsg);\n" +
                "        postMessageToDashboard(encodedMsg);\n" +
                "    }\n" +
                "\n" +
                "    function postMessageToIos(encodedMsg) {\n" +
                "        // See iOS SDK Source\n" +
                "        //    userContentController:didReceiveScriptMessage:\n" +
                "        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.iosListener)\n" +
                "            window.webkit.messageHandlers.iosListener.postMessage(encodedMsg);\n" +
                "    }\n" +
                "\n" +
                "    function postMessageToAndroid(encodedMsg) {\n" +
                "        if (window.OSAndroid)\n" +
                "            window.OSAndroid.postMessage(encodedMsg);\n" +
                "    }\n" +
                "\n" +
                "    function postMessageToDashboard(encodedMsg) {\n" +
                "        if (window.parent) {\n" +
                "            window.parent.postMessage(encodedMsg, \"*\");\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // last-element needed to give the correct height for modals and banners\n" +
                "    function getPageMetaData() {\n" +
                "        var lastElement = document.getElementById(\"last-element\");\n" +
                "        if (!lastElement)\n" +
                "            return {};\n" +
                "\n" +
                "        var flexContainer = document.querySelector(\".flex-container\");\n" +
                "        if (!flexContainer) {\n" +
                "            console.error(\"Could not find flex-container class required to resize modal correctly!\");\n" +
                "            return {};\n" +
                "        }\n" +
                "\n" +
                "        // rect.x and rect.y will be undefined on ClientRect and not DOMRect, " +
                "        // but rect.top and rect.left work for both\n" +
                "        var flexContainerRect = flexContainer.getBoundingClientRect();\n" +
                "        var lastElementRect = lastElement.getBoundingClientRect();\n" +
                "        return {\n" +
                "            rect: {\n" +
                "                height: lastElementRect.top + flexContainerRect.top\n" +
                "            },\n" +
                "            flexContainerRect: toJsonObject(flexContainerRect)\n" +
                "        };\n" +
                "    }\n" +
                "\n" +
//                    "    function toJsonObject(value) {\n" +
//                    "        return JSON.parse(JSON.stringify(value));\n" +
//                    "    }\n" +
//                    "    function toJsonObject(obj) {\n" +
//                    "       var clone = {};\n" +
//                    "       for(var i in obj) {\n" +
//                    "           if(obj[i] != null && typeof(obj[i])==\"object\")\n" +
//                    "               clone[i] = cloneObject(obj[i]);\n" +
//                    "           else\n" +
//                    "               clone[i] = obj[i];\n" +
//                    "       }\n" +
//                    "       return clone;\n" +
//                    "    }\n" +
                "    function toJsonObject(rect) {\n" +
                "        return {\n" +
                "            top: rect.top,\n" +
                "            right: rect.right,\n" +
                "            bottom: rect.bottom,\n" +
                "            left: rect.left,\n" +
                "            width: rect.width,\n" +
                "            height: rect.height\n" +
//                        "       x: rect.x,\n" + // DOMRect objects have 'x' attribute, but not ClientRect
//                        "       y: rect.y\n" +  // DOMRect objects have 'y' attribute, but not ClientRect
                "        };\n" +
                "    }\n" +
                "\n" +
                "    function getDisplayLocation() {\n" +
                "        var flexContainer = document.querySelector(\".flex-container\");\n" +
                "        if (!flexContainer) {\n" +
                "            console.error(\"Could not find flex-container class required to resize modal correctly!\");\n" +
                "            return null;\n" +
                "        }\n" +
                "\n" +
                "        return flexContainer.dataset.displaylocation;\n" +
                "    }\n" +
                "\n" +
                "    function getAttributes(element) {\n" +
                "        var attributes = {};\n" +
                "        if (element.hasAttributes()) {\n" +
                "            for (var i = 0, n = element.attributes.length; i < n; i++) {\n" +
                "                var attr = element.attributes[i];\n" +
                "                attributes[attr.name] = attr.value;\n" +
                "            }\n" +
                "        }\n" +
                "        return attributes;\n" +
                "    }\n" +
                "\n" +
                "    // TODO: Remove after we have verified we are not seeing any mis touches.\n" +
                "    // Just a quick and dirty way to see where you have tapped by moving the close button to where you tapped.\n" +
                "    function debugTaps() {\n" +
                "        document.body.addEventListener('click', function(e) {\n" +
                "            console.log(\"body onlick:\" + JSON.stringify({x: e.pageX, y: e.pageY}));\n" +
                "            document.querySelector(\".close-button\").style.display = \"block\";\n" +
                "            document.querySelector(\".close-button\").style.right = 0;\n" +
                "            document.querySelector(\".close-button\").style.left = e.pageX;\n" +
                "            document.querySelector(\".close-button\").style.top = e.pageY;\n" +
                "        }, true);\n" +
                "    }\n" +
                "\n" +
                "    // Lets the SDK know the page is done loading as well as it's display type and location.\n" +
                "    window.onload = function() {\n" +
                "        postMessageToNative({\n" +
                "            type: \"rendering_complete\",\n" +
                "            pageMetaData: getPageMetaData(),\n" +
                "            displayLocation: getDisplayLocation()\n" +
                "        });\n" +
                "\n" +
                "        // Body clicks\n" +
                "        \n" +
                "            document.addEventListener(\"click\", function(e) {\n" +
                "                actionTaken({\"id\":\"ffefda63-fdb8-4912-b69b-b760b2032532\",\"url\":\"\",\"name\":\"\",\"close\":true,\"prompts\":[],\"url_target\":\"browser\"}, \"body\");\n" +
                "                e.stopPropagation();\n" +
                "            }, false);\n" +
                "        \n" +
                "\n" +
                "        // close button clicks\n" +
                "        var closeButton = document.querySelector(\".close-button\");\n" +
                "        closeButton && closeButton.addEventListener(\"click\", function(e) {\n" +
                "            actionTaken({close: true});\n" +
                "            e.stopPropagation();\n" +
                "        }, true);\n" +
                "\n" +
                "        // image and button clicks\n" +
                "        var clickable = document.getElementsByClassName(\"iam-clickable\");\n" +
                "        for (var i = 0, n = clickable.length; i < n; i++) {\n" +
                "            var el = clickable[i];\n" +
                "            var attributes = getAttributes(el);\n" +
                "            if (attributes[\"data-action-payload\"]) {\n" +
                "                // use iife to close over the right element and value\n" +
                "                (function(element, value, label) {\n" +
                "                    element.addEventListener(\"click\", function(e) {\n" +
                "                        actionTaken(value, label);\n" +
                "                        e.stopPropagation();\n" +
                "                    }, true);\n" +
                "                })(el, JSON.parse(attributes[\"data-action-payload\"]), attributes[\"data-action-label\"]);\n" +
                "            }\n" +
                "        }\n" +
                "    };\n" +
                "\n" +
                "    window.onresize = function () {\n" +
                "        postMessageToNative({\n" +
                "            type: \"resize\",\n" +
                "            pageMetaData: getPageMetaData(),\n" +
                "            displayLocation: getDisplayLocation()\n" +
                "        });\n" +
                "    }\n" +
                "</script>\n" +
                "<style>\n" +
                ".close-button {\n" +
                "    right: -8px;\n" +
                "    top: -8px;\n" +
                "    width: 48px;\n" +
                "    height: 48px;\n" +
                "    position: absolute;\n" +
                "    display: flex;\n" +
                "    justify-content: center;\n" +
                "    flex-direction: column;\n" +
                "    align-items: center;\n" +
                "}#text-a0c6f1e0-0779-4062-9741-71ac689f1f5a {\n" +
                "  color: #ffffff;\n" +
                "  font-size: 24px;\n" +
                "  margin: 0;\n" +
                "  text-align: center;\n" +
                "}\n" +
                "#button-6119642d-efda-4692-ba4a-0a22a55302fa {\n" +
                "  font-size: 24px;\n" +
                "  color: #FFF;\n" +
                "  background-color: #eb1f2c;\n" +
                "  text-align: center;\n" +
                "  width: 100%;\n" +
                "  padding: 12px;\n" +
                "  border-width: 0;\n" +
                "  border-radius: 4px;\n" +
                "}\n" +
                "#991a7eb3-f817-409a-963a-9cebe340eb96 {\n" +
                "  font-size: 18px;\n" +
                "  color: #999;\n" +
                "  margin-top: 0px;\n" +
                "  text-align: center;\n" +
                "}\n" +
                "\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class=\"close-button\">\n" +
                "    <svg width=\"10\" height=\"10\" viewBox=\"0 0 8 8\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                "        <path d=\"M7.80309 1.14768C8.06564 0.885137 8.06564 0.459453 7.80309 0.196909C7.54055 -0.0656362 7.11486 -0.0656362 6.85232 0.196909L4 3.04923L1.14768 0.196909C0.885137 -0.0656362 0.459453 -0.0656362 0.196909 0.196909C-0.0656362 0.459453 -0.0656362 0.885137 0.196909 1.14768L3.04923 4L0.196909 6.85232C-0.0656362 7.11486 -0.0656362 7.54055 0.196909 7.80309C0.459453 8.06564 0.885137 8.06564 1.14768 7.80309L4 4.95077L6.85232 7.80309C7.11486 8.06564 7.54055 8.06564 7.80309 7.80309C8.06564 7.54055 8.06564 7.11486 7.80309 6.85232L4.95077 4L7.80309 1.14768Z\" fill=\"#111111\"/>\n" +
                "    </svg>\n" +
                "</div>\n" +
                "<div class=\"flex-container\" data-displaylocation=\"center_modal\">\n" +
                "        <div class=\"title-container\">\n" +
                "    <h1 id=\"text-a0c6f1e0-0779-4062-9741-71ac689f1f5a\">Outcome Test 2</h1>\n" +
                "</div><div class=\"button-container\">\n" +
                "  <button type=\"button\" id=\"button-6119642d-efda-4692-ba4a-0a22a55302fa\" class=\"iam-button iam-clickable\" data-action-payload='{\"id\":\"332a46e8-d6a0-4007-abd8-8fb3778282a2\",\"close\":false,\"url_target\":\"browser\"}' data-action-label=\"button\">Click Me</button>\n" +
                "</div>\n" +
                "\n" +
                "\n" +
                "        <!-- Used to find the height of the content so the SDK can set the correct view port height. -->\n" +
                "        <div id=\"last-element\" />\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>\n";

        final String base64Str = Base64.encodeToString(
                htmlStr.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );

        final WebViewManager webViewManager = new WebViewManager(message, currentActivity);
        lastInstance = webViewManager;

        // Web view must be created on the main thread.
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                webViewManager.setupWebView(currentActivity, base64Str);
            }
        });
    }

    // Lets JS from the page send JSON payloads to this class
    class OSJavaScriptInterface {

        static final String JS_OBJ_NAME = "OSAndroid";
        static final String GET_PAGE_META_DATA_JS_FUNCTION = "getPageMetaData()";

        static final String EVENT_TYPE_KEY = "type";
        static final String EVENT_TYPE_RENDERING_COMPLETE = "rendering_complete";
        static final String EVENT_TYPE_ACTION_TAKEN = "action_taken";

        static final String IAM_DISPLAY_LOCATION_KEY = "displayLocation";
        static final String IAM_PAGE_META_DATA_KEY = "pageMetaData";

        @JavascriptInterface
        public void postMessage(String message) {
            try {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "OSJavaScriptInterface:postMessage: " + message);

                JSONObject jsonObject = new JSONObject(message);
                String messageType = jsonObject.getString(EVENT_TYPE_KEY);

                if (messageType.equals(EVENT_TYPE_RENDERING_COMPLETE))
                    handleRenderComplete(jsonObject);
                else if (messageType.equals(EVENT_TYPE_ACTION_TAKEN) && !messageView.isDragging()) {
                    // Added handling so that click actions won't trigger while dragging the IAM
                    handleActionTaken(jsonObject);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void handleRenderComplete(JSONObject jsonObject) {
            Position displayType = getDisplayLocation(jsonObject);
            int pageHeight = displayType == Position.FULL_SCREEN ? -1 : getPageHeightData(jsonObject);
            createNewInAppMessageView(displayType, pageHeight);
        }

        private int getPageHeightData(JSONObject jsonObject) {
            try {
                return WebViewManager.pageRectToViewHeight(activity, jsonObject.getJSONObject(IAM_PAGE_META_DATA_KEY));
            } catch (JSONException e) {
                return -1;
            }
        }

        private @NonNull Position getDisplayLocation(JSONObject jsonObject) {
            Position displayLocation = Position.FULL_SCREEN;
            try {
                if (jsonObject.has(IAM_DISPLAY_LOCATION_KEY) && !jsonObject.get(IAM_DISPLAY_LOCATION_KEY).equals(""))
                    displayLocation = Position.valueOf(jsonObject.optString(IAM_DISPLAY_LOCATION_KEY, "FULL_SCREEN").toUpperCase());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return displayLocation;
        }

        private void handleActionTaken(JSONObject jsonObject) throws JSONException {
            JSONObject body = jsonObject.getJSONObject("body");
            String id = body.optString("id", null);
            if (message.isPreview) {
                OSInAppMessageController.getController().onMessageActionOccurredOnPreview(message, body);
            } else if (id != null) {
                OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, body);
            }

            boolean close = body.getBoolean("close");
            if (close)
                dismissAndAwaitNextMessage(null);
        }
    }

    private static int pageRectToViewHeight(final @NonNull Activity activity, @NonNull JSONObject jsonObject) {
        try {
            int pageHeight = jsonObject.getJSONObject("rect").getInt("height");
            int pxHeight = OSViewUtils.dpToPx(pageHeight);
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "getPageHeightData:pxHeight: " + pxHeight);

            int maxPxHeight = getWebViewMaxSizeY(activity);
            if (pxHeight > maxPxHeight) {
                pxHeight = maxPxHeight;
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "getPageHeightData:pxHeight is over screen max: " + maxPxHeight);
            }

            return pxHeight;
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "pageRectToViewHeight could not get page height", e);
            return -1;
        }
    }

    // Every time an Activity is shown we update the height of the WebView since the available
    //   screen size may have changed. (Expect for Fullscreen)
    private void calculateHeightAndShowWebViewAfterNewActivity() {
        // Don't need a CSS / HTML height update for fullscreen
        if (messageView.getDisplayPosition() == Position.FULL_SCREEN) {
            showMessageView(null);
            return;
        }

       // Using post to ensure that the status bar inset is already added to the view
       OSViewUtils.decorViewReady(activity, new Runnable() {
          @Override
          public void run() {
             // At time point the webView isn't attached to a view
             // Set the WebView to the max screen size then run JS to evaluate the height.
             setWebViewToMaxSize(activity);
             webView.evaluateJavascript(OSJavaScriptInterface.GET_PAGE_META_DATA_JS_FUNCTION, new ValueCallback<String>() {
                 @Override
                 public void onReceiveValue(final String value) {
                    try {
                       int pagePxHeight = pageRectToViewHeight(activity, new JSONObject(value));
                       showMessageView(pagePxHeight);
                    } catch (JSONException e) {
                       e.printStackTrace();
                    }
                 }
             });
          }
       });
    }

    @Override
    void available(final @NonNull Activity activity) {
        this.activity = activity;
        if (firstShow)
            showMessageView(null);
        else
            calculateHeightAndShowWebViewAfterNewActivity();
    }

    @Override
    void stopped(WeakReference<Activity> reference) {
        if (messageView != null)
            messageView.removeAllViews();
    }

    private void showMessageView(@Nullable Integer newHeight) {
        if (messageView == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "No messageView found to update a with a new height.");
            return;
        }

        messageView.setWebView(webView);
        if (newHeight != null)
            messageView.updateHeight(newHeight);
        messageView.showView(activity);
        messageView.checkIfShouldDismiss();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView(@NonNull final Activity currentActivity, final @NonNull String base64Message) {
       enableWebViewRemoteDebugging();

       webView = new OSWebView(currentActivity);

       webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
       webView.setVerticalScrollBarEnabled(false);
       webView.setHorizontalScrollBarEnabled(false);
       webView.clearCache(true);
       webView.clearHistory();
       webView.getSettings().setJavaScriptEnabled(true);
       webView.getSettings().setDomStorageEnabled(true);
       webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

       webView.setWebChromeClient(new WebChromeClient() {
           public boolean onConsoleMessage(ConsoleMessage cm) {
               Log.d(TAG, cm.message() + " -- From line "
                       + cm.lineNumber() + " of "
                       + cm.sourceId() );
               return true;
           }
       });

       webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep local assets in this WebView.
                return !url.startsWith("file");
            }
        });

       // Setup receiver for page events / data from JS
       webView.addJavascriptInterface(new OSJavaScriptInterface(), OSJavaScriptInterface.JS_OBJ_NAME);

       blurryRenderingWebViewForKitKatWorkAround(webView);

       OSViewUtils.decorViewReady(currentActivity, new Runnable() {
          @Override
          public void run() {
             setWebViewToMaxSize(currentActivity);
             webView.loadData(base64Message,"text/html; charset=utf-8","base64");
          }
       });
    }

    private void blurryRenderingWebViewForKitKatWorkAround(@NonNull WebView webView) {
        // Android 4.4 has a rendering bug that cause the whole WebView to by extremely blurry
        // This is due to a bug with hardware rending so ensure it is disabled.
        // Tested on other version of Android and it is specific to only Android 4.4
        //    On both the emulator and real devices.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT)
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    // This sets the WebView view port sizes to the max screen sizes so the initialize
    //   max content height can be calculated.
    // A render complete or resize event will fire from JS to tell Java it's height and will then display
    //  it via this SDK's InAppMessageView class. If smaller than the screen it will correctly
    //  set it's height to match.
    private void setWebViewToMaxSize(Activity activity) {
        webView.layout(0,0, getWebViewMaxSizeX(activity), getWebViewMaxSizeY(activity));
    }

    private void createNewInAppMessageView(@NonNull Position displayLocation, int pageHeight) {
        messageView = new InAppMessageView(webView, displayLocation, pageHeight, message.getDisplayDuration());
        messageView.setMessageController(new InAppMessageView.InAppMessageViewListener() {
            @Override
            public void onMessageWasShown() {
                firstShow = false;
                OSInAppMessageController.getController().onMessageWasShown(message);
            }

            @Override
            public void onMessageWasDismissed() {
                OSInAppMessageController.getController().messageWasDismissed(message);
                ActivityLifecycleHandler.removeActivityAvailableListener(TAG + message.messageId);
            }
        });

        // Fires event if available, which will call messageView.showInAppMessageView() for us.
        ActivityLifecycleHandler.setActivityAvailableListener(TAG + message.messageId, this);
    }

    // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
    private static void enableWebViewRemoteDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private static int getWebViewMaxSizeX(Activity activity) {
        return OSViewUtils.getWindowWidth(activity) - (MARGIN_PX_SIZE * 2);
    }

    private static int getWebViewMaxSizeY(Activity activity) {
       return OSViewUtils.getWindowHeight(activity) - (MARGIN_PX_SIZE * 2);
    }

    /**
     * Trigger the {@link #messageView} dismiss animation flow
     */
    protected void dismissAndAwaitNextMessage(@Nullable final OneSignalGenericCallback callback) {
        if (messageView == null) {
            if (callback != null)
                callback.onComplete();
            return;
        }

        messageView.dismissAndAwaitNextMessage(new OneSignalGenericCallback() {
            @Override
            public void onComplete() {
                messageView = null;
                if (callback != null)
                    callback.onComplete();
            }
        });
    }
}
