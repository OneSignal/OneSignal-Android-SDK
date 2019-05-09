package com.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Manages WebView instances by pre-loading them, displaying them, and closing them when dismissed.
//   Includes a static map for pre-loading, showing, and dismissed so these events can't be duplicated.

// Flow for Displaying WebView
// 1. showHTMLString - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. This calls showActivity which starts a new WebView
// 4. WebViewActivity will call WebViewManager.instanceFromIam(...) to get this instance and
//       add it's prepared WebView add add it to the Activity.

class WebViewManager {
   private static Map<String, WebViewManager> instances = new ConcurrentHashMap<>();

   private WebViewManager(@NonNull OSInAppMessage message) {
      this.message = message;
      instances.put(message.messageId, this);
   }

   private OSInAppMessage message;

   private WebView webView;
   @Nullable
   WebView getWebView() {
      return webView;
   }

   @Nullable
   static WebViewManager instanceFromIam(@Nullable String iamId) {
      return instances.get(iamId);
   }

   private WebViewActivity hostingActivity;

   // Lets JS from the page send JSON payloads to this class
   private class OSJavaScriptInterface {
      private static final String JS_OBJ_NAME = "OSAndroid";

      @JavascriptInterface
      public void postMessage(String message) {
         try {
            JSONObject jsonObject = new JSONObject(message);
            String messageType = jsonObject.getString("type");

            if (messageType.equals("rendering_complete"))
               handleRenderComplete(jsonObject);
            else if (messageType.equals("action_taken"))
               handleActionTaken(jsonObject);
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }

      private void handleRenderComplete(JSONObject jsonObject) {
         int pageHeight = getPageHeightData(jsonObject);
         if (pageHeight != -1)
            pageHeight = OSUtils.dpToPx(pageHeight);

         showActivity(pageHeight, getDisplayLocation(jsonObject));
      }

      private int getPageHeightData(JSONObject jsonObject) {
         try {
            return jsonObject.getJSONObject("pageMetaData").getJSONObject("rect").getInt("height");
         } catch (JSONException e) {
            return -1;
         }
      }

      private String getDisplayLocation(JSONObject jsonObject) {
         return jsonObject.optString("displayLocation", "");
      }

      private void handleActionTaken(JSONObject jsonObject) throws JSONException {
         JSONObject body = jsonObject.getJSONObject("body");
         String id = body.optString("id", null);
         if (id != null)
            OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, body);

         boolean close = body.getBoolean("close");
         if (close)
            dismiss();
      }
   }

   // TODO: Test with chrome://crash
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
      webView.setLeft(0);
      webView.setRight(Math.min(WebViewActivity.getWebViewYSize(), WebViewActivity.getWebViewXSize()));
      // TODO: When shrinking image based on WebView port height setBottom may need to be tweaked
      // NOTE: If setTop and / or setBottom are NOT set on Android 5.0 (Chrome 72) it calcs width as 0 somehow...
      webView.setTop(0);
      webView.setBottom(Math.max(WebViewActivity.getWebViewYSize(), WebViewActivity.getWebViewXSize()));

      // TODO: Look into using setInitialScale if WebView does not fit
      //       Default size is dp * 100
      // webView.setInitialScale(350);
   }

   // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
   private static void enableWebViewRemoteDebugging() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
          OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG))
         WebView.setWebContentsDebuggingEnabled(true);
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
         e.printStackTrace();
      }
   }

   private void showActivity(int pageHeight, String displayLocation) {
// TODO: Add a view to the app's window to prevent Activity pauses for top / bottom banners
//      OSUtils.runOnMainUIThread(new Runnable() {
//         @Override
//         public void run() {
//            WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION, 0);
//            ActivityLifecycleHandler.curActivity.getWindowManager().addView(webView, new WindowManager.LayoutParams(
//               WindowManager.LayoutParams.TYPE_APPLICATION,
//               0));
//         }});


      // TODO: Handle curActivity NULL cases
      //   TODO:1: This seems to be null if the location prompt is shown
      //   TODO:2: Also null if consent was provided and another Activity focus event did not happen yet.
      //   TODO:3: Can also be null when just switching to the next in-app message
      // TODO: Setup ActivityAvailableListener, changing it to an observable instead.
      Activity activity = ActivityLifecycleHandler.curActivity;

      Intent intent = new Intent(activity, WebViewActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

      intent.putExtra(WebViewActivity.INTENT_KEY_PAGE_HEIGHT, pageHeight);
      intent.putExtra(WebViewActivity.INTENT_KEY_DISPLAY_LOCATION, displayLocation);
      intent.putExtra(WebViewActivity.INTENT_KEY_IAM_ID, message.messageId);

      activity.startActivity(intent);
   }

   void presenterShown(WebViewActivity activity) {
      hostingActivity = activity;
      OSInAppMessageController.onMessageWasShown(message);
   }

   // Will be called through OSJavaScriptInterface.
   // If so let the presenter (Activity) know to start it's dismiss animation.
   private void dismiss() {
      if (hostingActivity == null) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already");
         markAsDismissed();
         return;
      }

      hostingActivity.dismiss();
   }

   // Called from presenter when it is no longer visible. (Animation is done)
   void markAsDismissed() {
      // Dereference so this can be cleaned up in the next GC
      webView = null;
      hostingActivity = null;
      OSInAppMessageController.getController().messageWasDismissed(message);
   }
}
