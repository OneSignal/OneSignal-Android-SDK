package com.onesignal;

import org.json.JSONObject;
import org.robolectric.annotation.Implements;

@Implements(OneSignalRestClient.class)
public class ShadowOneSignalRestClientForTimeouts {
   
   public static boolean threadInterrupted;
   
   public static int getThreadTimeout(int timeout) {
      return 1;
   }
   
   public static void startHTTPConnection(String url, String method, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler, int timeout) {
      try {
         Thread.sleep(120000);
      } catch (InterruptedException e) {
         threadInterrupted = true;
      }
   }
}
