/**
 * Modified MIT License
 * 
 * Copyright 2017 OneSignal
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

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

class TrackFirebaseAnalytics {
   
   private static Class<?> FirebaseAnalyticsClass;
   private Object mFirebaseAnalyticsInstance;
   private Context appContext;

   private static AtomicLong lastReceivedTime;
   private static AtomicLong lastOpenedTime;
   
   private static OSNotificationPayload lastReceivedPayload;

   private static final String EVENT_NOTIFICATION_OPENED = "os_notification_opened";
   private static final String EVENT_NOTIFICATION_INFLUENCE_OPEN = "os_notification_influence_open";
   private static final String EVENT_NOTIFICATION_RECEIVED = "os_notification_received";

   TrackFirebaseAnalytics(Context activity) {
      appContext = activity;
   }

   static boolean CanTrack() {
      try {
         FirebaseAnalyticsClass = Class.forName("com.google.firebase.analytics.FirebaseAnalytics");
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   void trackInfluenceOpenEvent() {
      if (lastReceivedTime == null || lastReceivedPayload == null)
         return;
   
      // Attribute if app was opened in 2 minutes or less after displaying the notification
      long now = System.currentTimeMillis();
      if (now - lastReceivedTime.get() > 1000 * 60 * 2)
         return;
   
      // Don't attribute if we opened a notification in the last 30 seconds.
      //  To prevent an open and an influenced open from firing for the same notification.
      if (lastOpenedTime != null && now - lastOpenedTime.get() < 1000 * 30)
         return;
      
      try {
         Object firebaseAnalyticsInstance = getFirebaseAnalyticsInstance(appContext);

         Method trackMethod = getTrackMethod(FirebaseAnalyticsClass);

         String event = EVENT_NOTIFICATION_INFLUENCE_OPEN;

        //construct the firebase analytics event bundle
         Bundle bundle = new Bundle();
         bundle.putString("source", "OneSignal");
         bundle.putString("medium", "notification");
         bundle.putString("notification_id", lastReceivedPayload.notificationID);
         bundle.putString("campaign", getCampaignNameFromPayload(lastReceivedPayload));

         trackMethod.invoke(firebaseAnalyticsInstance, event, bundle);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   void trackOpenedEvent(OSNotificationOpenResult openResult) {
      if(lastOpenedTime == null)
         lastOpenedTime = new AtomicLong();
      lastOpenedTime.set(System.currentTimeMillis());
      
      try {
         //get the source, medium, campaign params from the openResult
         Object firebaseAnalyticsInstance = getFirebaseAnalyticsInstance(appContext);

         Method trackMethod = getTrackMethod(FirebaseAnalyticsClass);

         //construct the firebase analytics event bundle
         Bundle bundle = new Bundle();
         bundle.putString("source", "OneSignal");
         bundle.putString("medium", "notification");
         bundle.putString("notification_id", openResult.notification.payload.notificationID);
         bundle.putString("campaign", getCampaignNameFromPayload(openResult.notification.payload));

         trackMethod.invoke(firebaseAnalyticsInstance, EVENT_NOTIFICATION_OPENED, bundle);

      } catch (Throwable t) {
         t.printStackTrace();
      }


   }

   void trackReceivedEvent(OSNotificationOpenResult receivedResult) {
      try {
         //get the source, medium, campaign params from the openResult
         Object firebaseAnalyticsInstance = getFirebaseAnalyticsInstance(appContext);

         Method trackMethod = getTrackMethod(FirebaseAnalyticsClass);
         //construct the firebase analytics event bundle
         Bundle bundle = new Bundle();
         bundle.putString("source", "OneSignal");
         bundle.putString("medium", "notification");
         bundle.putString("notification_id", receivedResult.notification.payload.notificationID);
         bundle.putString("campaign", getCampaignNameFromPayload(receivedResult.notification.payload));

         trackMethod.invoke(firebaseAnalyticsInstance, EVENT_NOTIFICATION_RECEIVED, bundle);

         if(lastReceivedTime == null)
            lastReceivedTime = new AtomicLong();
         lastReceivedTime.set(System.currentTimeMillis());

         lastReceivedPayload = receivedResult.notification.payload;

      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   private String getCampaignNameFromPayload(OSNotificationPayload payload) {
      if (!payload.templateName.isEmpty() && !payload.templateId.isEmpty())
         return payload.templateName + " - " + payload.templateId;
      else if (payload.title != null)
         return payload.title.substring(0, Math.min(10, payload.title.length()));
      
      return "";
   }

   private Object getFirebaseAnalyticsInstance(Context context) {

      if(mFirebaseAnalyticsInstance == null) {
         Method getInstanceMethod = getInstanceMethod(FirebaseAnalyticsClass);
         try {
            mFirebaseAnalyticsInstance = getInstanceMethod.invoke(null,context);
         } catch (Throwable e) {
            e.printStackTrace();
            return null;
         }
      }

      return mFirebaseAnalyticsInstance;
   }

   private static Method getTrackMethod(Class clazz) {
      try {
         return clazz.getMethod("logEvent", String.class, Bundle.class);
      } catch (NoSuchMethodException e) {
         e.printStackTrace();
         return null;
      }
   }

   private static Method getInstanceMethod(Class clazz) {
      try {
         return clazz.getMethod("getInstance", Context.class);
      } catch (NoSuchMethodException e) {
         e.printStackTrace();
         return null;
      }
   }
}


/*
  // FirebaseAnalytics methods

  public static FirebaseAnalytics getInstance(Context context)
  public void logEvent(String name, Bundle params)

 */