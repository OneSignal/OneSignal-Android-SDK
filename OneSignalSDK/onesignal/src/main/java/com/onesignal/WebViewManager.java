package com.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
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
//   private static final String TEST_PAGE_HOST = "http://10.0.1.8:3000";
//   private static final String TEST_PAGE_HOST = "http://10.0.0.17:3000";
   private static String TEST_PAGE_HOST = "http://192.168.2.165:3000";


   static void setHost(String host) {
      TEST_PAGE_HOST = "http://" + host + ":3000";
   }

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
            showActivity(pageHeight, getDisplayLocation(jsonObject));
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

      private static String getDisplayLocation(JSONObject jsonObject) {
         return jsonObject.optString("displayLocation", "");
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
//    TODO: Sometimes device just keeps using cache even those the server doesn't return any
//          cache HTTP header. Try testing on staging to see if it expires correctly.
//    webView.getSettings().setAppCacheEnabled(false); // Default is false
//    webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT); // LOAD_NO_CACHE
      // TODO: Remove after testing
      webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

      // Setup receiver for page events / data from JS
      webView.addJavascriptInterface(
         new OSJavaScriptInterface(),
         OSJavaScriptInterface.JS_OBJ_NAME
      );

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
      //       500 seems close to the default scale
      // webView.setInitialScale(500);

      Log.e("OneSignal", "Resources.getSystem().getDisplayMetrics().density: " + Resources.getSystem().getDisplayMetrics().density);
      Log.e("OneSignal", "getScale(): " + webView.getScale());
//      webView.setInitialScale(350);

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
      webView = showWebViewForPage(TEST_PAGE_HOST + "/iam_full_screen_test.html");
   }

   static void showModalWebView() {
      webView = showWebViewForPage(TEST_PAGE_HOST + "/iam_modal_test.html");
   }

   static void showBannerTopWebView() {
      webView = showWebViewForPage(TEST_PAGE_HOST + "/iam_top_banner_test.html");
   }

   static void showBannerBottomWebView() {
      webView = showWebViewForPage(TEST_PAGE_HOST + "/iam_bottom_banner_test.html");
   }

   private static void showActivity(int pageHeight, String displayLocation) {
      Activity activity = ActivityLifecycleHandler.curActivity;

      Intent intent = new Intent(activity, WebViewActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      intent.putExtra(WebViewActivity.PAGE_HEIGHT_INTENT_KEY, pageHeight);
      intent.putExtra(WebViewActivity.DISPLAY_LOCATION_INTENT_KEY, displayLocation);
      activity.startActivity(intent);
   }

   private static void dismiss() {
      if (WebViewActivity.instance != null)
         WebViewActivity.instance.dismiss();
      webView = null;
   }
}
