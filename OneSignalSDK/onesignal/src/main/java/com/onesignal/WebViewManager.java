package com.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

// Flow for Displaying WebView
// 1. showFullscreenWebView - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. Start WebViewActivity
// 4. WebViewActivity.webView will attach WebViewManager.webView to it's layout

class WebViewManager {
   static WebView webView;

   // TODO: Remove before merging
   private static final String TEST_PAGE_HOST = "http://192.168.2.165:3000";

   // Lets JS from the page send JSON payloads to this class
   static class OSJavaScriptInterface {
      static String JS_OBJ_NAME = "OSAndroid";

      @JavascriptInterface
      public void postMessage(String message) {
         Log.e("OneSignal", "Message received from WebView: " + message);
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

      private static void handleRenderComplete(JSONObject jsonObject) {
         try {
            int pageHeight = getPageHeightData(jsonObject);
            if (pageHeight != -1)
               pageHeight = OSUtils.dpToPx(pageHeight);
            showActivity(pageHeight);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      private static int getPageHeightData(JSONObject jsonObject) {
         try {
            return jsonObject.getJSONObject("pageMetaData").getJSONObject("rect").getInt("height");
         } catch (Exception e) {
            return -1;
         }
      }

      private static void handleActionTaken(JSONObject jsonObject) {
         try {
            JSONObject body = jsonObject.getJSONObject("body");
            boolean close = body.getBoolean("close");
            if (close)
               dismiss();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   // TODO: Reuse last WebView if one exists
   // TODO: Test with chrome://crash
   private static WebView showWebViewForPage(String page) {
      enableWebViewRemoteDebugging();

      WebView webView = new OSWebView(OneSignal.appContext);

      webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      webView.setVerticalScrollBarEnabled(false);

      webView.getSettings().setJavaScriptEnabled(true);
// TODO: Had to disable cache before, seems fine on Android 8.0 Chrome 72 though
//      webView.getSettings().setAppCacheEnabled(false); // Default is false
//      webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT); // LOAD_NO_CACHE

      webView.addJavascriptInterface(
         new OSJavaScriptInterface(),
         OSJavaScriptInterface.JS_OBJ_NAME
      );

      // Setting size before adding to Activity to prevent a resize event.
      webView.setLeft(0);
      webView.setRight(WebViewActivity.getWebViewXSize());
      // TODO: When shrinking image based on WebView port height setBottom may need to be tweaked
      // NOTE: If setTop and / or setBottom are NOT set on Android 5.0 (Chrome 72) it calcs width as 0 somehow...
      webView.setTop(0);
      webView.setBottom(WebViewActivity.getWebViewYSize());

      // TODO: Try these setting to see if web view will shrink if it does not fit
      // 500 seems close to the default scale
      // webView.setInitialScale(500);

      // TODO: Look into using scale if WebView does not fit
      webView.loadUrl(page);

      return webView;
   }

   // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
   private static void enableWebViewRemoteDebugging() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
         && OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG))
         WebView.setWebContentsDebuggingEnabled(true);
   }

   static void showFullscreenWebView() {
      webView = showWebViewForPage(TEST_PAGE_HOST + "/iam_fullscreen_test.html");
   }

   static void showModalWebView() {
      webView = showWebViewForPage(TEST_PAGE_HOST + "/iam_modal_test.html");
   }

   private static void showActivity(int pageHeight) {
      Activity activity = ActivityLifecycleHandler.curActivity;

      Intent intent = new Intent(activity, WebViewActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      intent.putExtra(WebViewActivity.PAGE_HEIGHT_INTENT_KEY, pageHeight);
      activity.startActivity(intent);
   }

   private static void dismiss() {
      if (WebViewActivity.instance != null)
         WebViewActivity.instance.dismiss();
      webView = null;
   }
}
