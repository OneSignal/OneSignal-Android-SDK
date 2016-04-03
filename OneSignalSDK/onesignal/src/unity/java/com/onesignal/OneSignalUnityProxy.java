/**
 * Modified MIT License
 * 
 * Copyright 2016 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.Activity;

import org.json.JSONObject;

import com.onesignal.OneSignal.NotificationOpenedHandler;
import com.onesignal.OneSignal.GetTagsHandler;
import com.onesignal.OneSignal.IdsAvailableHandler;
import com.onesignal.OneSignal.PostNotificationResponseHandler;

public class OneSignalUnityProxy implements NotificationOpenedHandler {

   private String unityListenerName;
   private static java.lang.reflect.Method unitySendMessage;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   public OneSignalUnityProxy(String listenerName, String googleProjectNumber, String oneSignalAppId, int logLevel, int visualLogLevel) {
      unityListenerName = listenerName;
      
      try {
         // We use reflection here so the default proguard config does not get an error for native apps.
         Class unityPlayerClass;
         unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
         unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);

         OneSignal.sdkType = "unity";
         OneSignal.setLogLevel(logLevel, visualLogLevel);
         OneSignal.init((Activity) unityPlayerClass.getField("currentActivity").get(null), googleProjectNumber, oneSignalAppId, this);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   @Override
   public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
      JSONObject outerObject = new JSONObject();
      try {
         outerObject.put("isActive", isActive);
         outerObject.put("alert", message);
         outerObject.put("custom", additionalData);
         unitySendMessage.invoke(null, unityListenerName, "onPushNotificationReceived", outerObject.toString());
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   public void sendTag(String key, String value) {
      OneSignal.sendTag(key, value);
   }

   public void sendTags(String json) {
      OneSignal.sendTags(json);
   }

   public void getTags() {
      OneSignal.getTags(new GetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            try {
               unitySendMessage.invoke(null, unityListenerName, "onTagsReceived", tags.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void deleteTag(String key) {
      OneSignal.deleteTag(key);
   }

   public void deleteTags(String json) {
      OneSignal.deleteTags(json);
   }

   public void idsAvailable() {
      OneSignal.idsAvailable(new IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            JSONObject jsonIds = new JSONObject();
            try {
               jsonIds.put("userId", userId);
               if (registrationId != null)
                  jsonIds.put("pushToken", registrationId);
               else
                  jsonIds.put("pushToken", "");

               unitySendMessage.invoke(null, unityListenerName, "onIdsAvailable", jsonIds.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void enableSound(boolean enable) { OneSignal.enableSound(enable); }

   public void enableVibrate(boolean enable) { OneSignal.enableVibrate(enable); }

   public void enableNotificationsWhenActive(boolean enable) { OneSignal.enableNotificationsWhenActive(enable); }

   public void enableInAppAlertNotification(boolean enable) { OneSignal.enableInAppAlertNotification(enable); }

   public void setSubscription(boolean enable) { OneSignal.setSubscription(enable); }

   public void postNotification(String json) {
      OneSignal.postNotification(json, new PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {
            try {
               unitySendMessage.invoke(null, unityListenerName, "onPostNotificationSuccess", response.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }

         @Override
         public void onFailure(JSONObject response) {
            try {
               unitySendMessage.invoke(null, unityListenerName, "onPostNotificationFailed", response.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void promptLocation() {
      OneSignal.promptLocation();
   }
   public void setEmail(String email) {
      OneSignal.setEmail(email);
   }
}
