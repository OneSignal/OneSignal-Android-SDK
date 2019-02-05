package com.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

// Flow for Displaying WebView
// 1. showWebView - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. Start WebViewActivity
// 4. WebViewActivity.webView will attach WebViewManager.webView to it's layout

class WebViewManager {
   static WebView webView;

   static class JavaScriptInterface {
      @JavascriptInterface
      public void postMessage(String message) {
         Log.e("OneSignal", "Message received from WebView: " + message);
         try {
            JSONObject jsonObject = new JSONObject(message);

            if (jsonObject.getString("type").equals("rendering_complete")) {
               showActivity();
               return;
            }

            jsonObject = jsonObject.getJSONObject("body");
            boolean close = jsonObject.getBoolean("close");
            if (close)
               dismiss();
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }
   }

   static WebView showWebViewForPage(String page) {
      enableWebViewRemoteDebugging();

      WebView webView = new OSWebView(OneSignal.appContext);

      webView.getSettings().setJavaScriptEnabled(true);
      // Disable cache, it's more than what the browser cache setting from HTTP headers
      // TODO: Find setting to respect client HTTP cache setting.
      webView.getSettings().setAppCacheEnabled(false);
      webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

      webView.addJavascriptInterface(new JavaScriptInterface(), "OSAndroid");

      Log.e("HERE", "dpToPx 1 = " + OSUtils.dpToPx(1));

      // Can use webView.loadData()
      webView.loadUrl("http://192.168.2.165:3000/iam_fullscreen_test.html");
//      webView.loadUrl("http://10.0.0.17:3000/iam_fullscreen_test.html");

      return webView;
   }

   private static void enableWebViewRemoteDebugging() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
         && OneSignal.atLogLevel(OneSignal.LOG_LEVEL.DEBUG))
         WebView.setWebContentsDebuggingEnabled(true);
   }

   static void showWebView() {
      webView = showWebViewForPage("");
   }

   private static void showActivity() {
      Activity activity = ActivityLifecycleHandler.curActivity;

      Intent intent = new Intent(activity, WebViewActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      activity.startActivity(intent);
   }

   private static void dismiss() {
      if (WebViewActivity.instance != null)
         WebViewActivity.instance.dismiss();
      webView = null;
   }
}
