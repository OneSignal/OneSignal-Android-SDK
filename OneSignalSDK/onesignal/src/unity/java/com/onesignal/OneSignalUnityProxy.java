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
import com.onesignal.OneSignal.NotificationReceivedHandler;
import com.onesignal.OneSignal.GetTagsHandler;
import com.onesignal.OneSignal.IdsAvailableHandler;
import com.onesignal.OneSignal.PostNotificationResponseHandler;

public class OneSignalUnityProxy implements NotificationOpenedHandler, NotificationReceivedHandler, OSPermissionObserver, OSSubscriptionObserver, OSEmailSubscriptionObserver {

   private static String unityListenerName;
   private static java.lang.reflect.Method unitySendMessage;

   @SuppressWarnings({ "unchecked", "rawtypes" })
   public OneSignalUnityProxy(String listenerName, String googleProjectNumber, String oneSignalAppId, int logLevel, int visualLogLevel, boolean requiresUserPrivacyConsent) {
      unityListenerName = listenerName;
      
      try {
         OneSignal.setRequiresUserPrivacyConsent(requiresUserPrivacyConsent);

         // We use reflection here so we don't have to include a Unity jar to build this project.
         Class unityPlayerClass;
         unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
         unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);

         OneSignal.sdkType = "unity";
         OneSignal.setLogLevel(logLevel, visualLogLevel);
   
         OneSignal.Builder builder = OneSignal.getCurrentOrNewInitBuilder();
         builder.unsubscribeWhenNotificationsAreDisabled(true);
         builder.filterOtherGCMReceivers(true);
         OneSignal.init((Activity) unityPlayerClass.getField("currentActivity").get(null), googleProjectNumber, oneSignalAppId, this, this);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {
      unitySafeInvoke("onPushNotificationOpened", result.toJSONObject().toString());
   }

   @Override
   public void notificationReceived(OSNotification notification) {
      unitySafeInvoke("onPushNotificationReceived", notification.toJSONObject().toString());
   }

   public void sendTag(String key, String value) {
      OneSignal.sendTag(key, value);
   }

   public void sendTags(String json) {
      OneSignal.sendTags(json);
   }

   public void setEmail(String email, String authHash) {
      OneSignal.setEmail(email, authHash, new OneSignal.EmailUpdateHandler() {
         @Override
         public void onSuccess() {
            unitySafeInvoke("onSetEmailSuccess", "{\"status\": \"success\"}");
         }

         @Override
         public void onFailure(OneSignal.EmailUpdateError error) {
            unitySafeInvoke("onSetEmailFailure", "{\"error\": \"" + error.getMessage() + "\"}");
         }
      });
   }

   public void logoutEmail() {
      OneSignal.logoutEmail(new OneSignal.EmailUpdateHandler() {
         @Override
         public void onSuccess() {
            unitySafeInvoke("onLogoutEmailSuccess", "{\"status\": \"success\"}");
         }

         @Override
         public void onFailure(OneSignal.EmailUpdateError error) {
            unitySafeInvoke("onLogoutEmailFailure", "{\"error\": \"" + error.getMessage() + "\"}");
         }
      });
   }

   public void getTags() {
      OneSignal.getTags(new GetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            String tagsStr;
            if (tags != null)
               tagsStr = tags.toString();
            else
               tagsStr = "{}";
            unitySafeInvoke("onTagsReceived", tagsStr);
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

               unitySafeInvoke("onIdsAvailable", jsonIds.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void enableSound(boolean enable) { OneSignal.enableSound(enable); }

   public void enableVibrate(boolean enable) { OneSignal.enableVibrate(enable); }

   public void setInFocusDisplaying(int displayOption) { OneSignal.setInFocusDisplaying(displayOption); }

   public void setSubscription(boolean enable) { OneSignal.setSubscription(enable); }

   public void postNotification(String json) {
      OneSignal.postNotification(json, new PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {
            unitySafeInvoke("onPostNotificationSuccess", response.toString());
         }

         @Override
         public void onFailure(JSONObject response) {
            unitySafeInvoke("onPostNotificationFailed", response.toString());
         }
      });
   }

   public void promptLocation() {
      OneSignal.promptLocation();
   }
   public void syncHashedEmail(String email) {
      OneSignal.syncHashedEmail(email);
   }
   public void clearOneSignalNotifications() {
      OneSignal.clearOneSignalNotifications();
   }
   public void cancelNotification(int id) {
      OneSignal.cancelNotification(id);
   }
   public void cancelGroupedNotifications(String group) {
      OneSignal.cancelGroupedNotifications(group);
   }
   
   public void addPermissionObserver() {
      OneSignal.addPermissionObserver(this);
   }
   
   public void removePermissionObserver() {
      OneSignal.removePermissionObserver(this);
   }
   
   public void addSubscriptionObserver() {
      OneSignal.addSubscriptionObserver(this);
   }
   
   public void removeSubscriptionObserver() {
      OneSignal.removeSubscriptionObserver(this);
   }

   public void addEmailSubscriptionObserver() { OneSignal.addEmailSubscriptionObserver(this); }

   public void removeEmailSubscriptionObserver() { OneSignal.removeEmailSubscriptionObserver(this); }

   public boolean userProvidedPrivacyConsent() { return OneSignal.userProvidedPrivacyConsent(); }

   public void provideUserConsent(boolean granted) { OneSignal.provideUserConsent(granted); }

   public void setRequiresUserPrivacyConsent(boolean required) { OneSignal.setRequiresUserPrivacyConsent(required); }
   
   public String getPermissionSubscriptionState() {
      return OneSignal.getPermissionSubscriptionState().toJSONObject().toString();
   }

   public void setLocationShared(boolean shared) { OneSignal.setLocationShared(shared); }
   
   @Override
   public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
      unitySafeInvoke("onOSPermissionChanged", stateChanges.toJSONObject().toString());
   }
   
   @Override
   public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
      unitySafeInvoke("onOSSubscriptionChanged", stateChanges.toJSONObject().toString());
   }

   @Override
   public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
      unitySafeInvoke("onOSEmailSubscriptionChanged", stateChanges.toJSONObject().toString());
   }
   
   private static void unitySafeInvoke(String method, String params) {
      try {
         unitySendMessage.invoke(null, unityListenerName, method, params);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }
}
