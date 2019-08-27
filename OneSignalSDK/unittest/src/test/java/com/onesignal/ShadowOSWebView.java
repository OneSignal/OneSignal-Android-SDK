package com.onesignal;

import android.renderscript.Sampler;
import android.webkit.ValueCallback;

import com.test.onesignal.TestHelpers;

import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowWebView;

import com.onesignal.WebViewManager.OSJavaScriptInterface;

import java.util.ArrayList;
import java.util.List;

@Implements(OSWebView.class)
public class ShadowOSWebView extends ShadowWebView {

   public static String lastData;
   private static List<ValueCallback<String>> evalJSCallbacks;

   public static void resetStatics() {
      lastData = null;
      evalJSCallbacks = new ArrayList<>();
   }

   private static final JSONObject MOCK_IAM_PAGE_META_DATA;
   static {
      JSONObject jsonObject = new JSONObject();
      try {
         jsonObject.put("rect", new JSONObject().put("height", 100));
      } catch (JSONException t) {
         t.printStackTrace();
      }
      MOCK_IAM_PAGE_META_DATA = jsonObject;
   }

   private static final String MOCK_IAM_RENDERING_COMPLETE_TOP_BANNER;
   static {
      JSONObject jsonObject = new JSONObject();
      try {
         jsonObject
            .put(OSJavaScriptInterface.EVENT_TYPE_KEY, OSJavaScriptInterface.EVENT_TYPE_RENDERING_COMPLETE)
            .put(OSJavaScriptInterface.IAM_DISPLAY_LOCATION_KEY, WebViewManager.Position.TOP_BANNER)
            .put(OSJavaScriptInterface.IAM_PAGE_META_DATA_KEY, MOCK_IAM_PAGE_META_DATA);
      } catch (JSONException t) {
         t.printStackTrace();
      }
      MOCK_IAM_RENDERING_COMPLETE_TOP_BANNER = jsonObject.toString();
   }

   public void loadData(String data, String mimeType, String encoding) {
      TestHelpers.assertMainThread();
      lastData = data;

      OSJavaScriptInterface jsInterface = (OSJavaScriptInterface)getJavascriptInterface(WebViewManager.OSJavaScriptInterface.JS_OBJ_NAME);
      jsInterface.postMessage(MOCK_IAM_RENDERING_COMPLETE_TOP_BANNER);
   }

   public void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
      evalJSCallbacks.add(resultCallback);
   }

   static public void fireEvalJSCallbacks() {
      for(ValueCallback<String> callback : evalJSCallbacks)
         callback.onReceiveValue(MOCK_IAM_PAGE_META_DATA.toString());
   }
}