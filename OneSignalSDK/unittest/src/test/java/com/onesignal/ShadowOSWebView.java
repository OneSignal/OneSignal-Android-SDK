package com.onesignal;

import com.test.onesignal.TestHelpers;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowWebView;

@Implements(OSWebView.class)
public class ShadowOSWebView extends ShadowWebView {

   public static String lastData;

   public static void resetStatics() {
      lastData = null;
   }

   public void loadData(String data, String mimeType, String encoding) {
      TestHelpers.assertMainThread();
      lastData = data;
   }
}