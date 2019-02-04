package com.onesignal;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

class WebViewManager {
   private static WebView webView;

   static class JavaScriptInterface {
      @JavascriptInterface
      public void postMessage(String message) {
         Log.e("OneSignal", "Message received from WebView: " + message);
         try {
            JSONObject jsonObject = new JSONObject(message);
            jsonObject = jsonObject.getJSONObject("body");
            boolean close = jsonObject.getBoolean("close");
            if (close) {
               // TODO: Should finish() activity, just some test code for now
               OSUtils.runOnMainUIThread(new Runnable() {
                  @Override
                  public void run() {
                     webView.destroy();
                  }
               });
            }
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }
   }

   static WebView showWebViewForPage(String page) {
      WebView.setWebContentsDebuggingEnabled(true);
      webView = new OSWebView(OneSignal.appContext);

      webView.getSettings().setJavaScriptEnabled(true);
      // Disable cache, it's than what the browser cache setting from HTTP headers
      webView.getSettings().setAppCacheEnabled(false);
      webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

      webView.addJavascriptInterface(new JavaScriptInterface(), "OSAndroid");

      // Can use webView.loadData()
//      webView.loadUrl("http://192.168.2.165:3000/iam_fullscreen_test.html");
      webView.loadUrl("http://10.0.0.17:3000/iam_fullscreen_test.html");

      return webView;
   }
}
