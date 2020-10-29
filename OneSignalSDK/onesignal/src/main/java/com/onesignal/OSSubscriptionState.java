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


import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class OSSubscriptionState implements Cloneable {

   private static final String CHANGED_KEY = "changed";

   private OSObservable<Object, OSSubscriptionState> observable;

   private String userId;
   private String pushToken;
   private boolean accepted;
   private boolean pushDisabled;

   OSSubscriptionState(boolean asFrom, boolean permissionAccepted) {
      observable = new OSObservable<>(CHANGED_KEY, false);
      
      if (asFrom) {
         pushDisabled = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_SUBSCRIPTION_LAST, true);
         userId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_PLAYER_ID_LAST, null);
         pushToken = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_PUSH_TOKEN_LAST, null);
         accepted = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST, false);
      } else {
         pushDisabled = !OneSignalStateSynchronizer.getUserSubscribePreference();
         userId = OneSignal.getUserId();
         pushToken = OneSignalStateSynchronizer.getRegistrationId();
         accepted = permissionAccepted;
      }
   }
   
   void changed(OSPermissionState state) {
      setAccepted(state.areNotificationsEnabled());
   }
   
   void setUserId(@Nullable String id) {
      boolean changed = false;
      if (id == null)
         changed = userId != null;
      else if (!id.equals(userId))
         changed = true;

      userId = id;
      if (changed)
         observable.notifyChange(this);
   }
   
   public String getUserId() {
      return userId;
   }
   
   void setPushToken(String id) {
      if (id == null)
         return;
      boolean changed = !id.equals(pushToken);
      pushToken = id;
      if (changed)
         observable.notifyChange(this);
   }
   
   public String getPushToken() {
      return pushToken;
   }
   
   void setPushDisabled(boolean disabled) {
      boolean changed = pushDisabled != disabled;
      pushDisabled = disabled;
      if (changed)
         observable.notifyChange(this);
   }
   
   public boolean isPushDisabled() {
      return pushDisabled;
   }
   
   private void setAccepted(boolean set) {
      boolean lastSubscribed = isSubscribed();
      accepted = set;
      if (lastSubscribed != isSubscribed())
         observable.notifyChange(this);
   }
   
   public boolean isSubscribed() {
      return userId != null && pushToken != null && !pushDisabled && accepted;
   }
   
   void persistAsFrom() {
      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_SUBSCRIPTION_LAST, pushDisabled);
      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_PLAYER_ID_LAST, userId);
      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_PUSH_TOKEN_LAST, pushToken);
      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST, accepted);
   }
   
   boolean compare(OSSubscriptionState from) {
      return pushDisabled != from.pushDisabled
          || !(userId != null ? userId : "").equals(from.userId != null ? from.userId : "")
          || !(pushToken != null ? pushToken : "").equals(from.pushToken != null ? from.pushToken : "")
          || accepted != from.accepted;
   }

   public OSObservable<Object, OSSubscriptionState> getObservable() {
      return observable;
   }

   protected Object clone() {
      try {
         return super.clone();
      } catch (CloneNotSupportedException e) {
         return null;
      }
   }
   
   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();
      
      try {
         if (userId != null)
            mainObj.put("userId", userId);
         else
            mainObj.put("userId", JSONObject.NULL);
   
         if (pushToken != null)
            mainObj.put("pushToken", pushToken);
         else
            mainObj.put("pushToken", JSONObject.NULL);
         
         mainObj.put("isPushDisabled", isPushDisabled());
         mainObj.put("isSubscribed", isSubscribed());
      }
      catch(JSONException e) {
         e.printStackTrace();
      }
      
      return mainObj;
   }
   
   @Override
   public String toString() {
      return toJSONObject().toString();
   }
}