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


import org.json.JSONObject;

public class OSSubscriptionState implements Cloneable {
   
   OSObservable<Object, OSSubscriptionState> observable;
   
   OSSubscriptionState(boolean asFrom, boolean permissionAccepted) {
      observable = new OSObservable<>("changed", false);
      
      if (asFrom) {
         userSubscriptionSetting = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_SUBSCRIPTION_LAST, false);
         userId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_PLAYER_ID_LAST, null);
         pushToken = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_PUSH_TOKEN_LAST, null);
         accepted = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST, false);
      }
      else {
         userSubscriptionSetting = OneSignalStateSynchronizer.getUserSubscribePreference();
         userId = OneSignal.getUserId();
         pushToken = OneSignalStateSynchronizer.getRegistrationId();
         accepted = permissionAccepted;
      }
   }
   
   private boolean accepted;
   private boolean userSubscriptionSetting;
   private String userId;
   private String pushToken;
   
   void changed(OSPermissionState state) {
      setAccepted(state.getEnabled());
   }
   
   void setUserId(String id) {
      boolean changed = !id.equals(userId);
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
   
   
   void setUserSubscriptionSetting(boolean set) {
      boolean changed = userSubscriptionSetting != set;
      userSubscriptionSetting = set;
      if (changed)
         observable.notifyChange(this);
   }
   
   public boolean getUserSubscriptionSetting() {
      return userSubscriptionSetting;
   }
   
   private void setAccepted(boolean set) {
      boolean lastSubscribed = getSubscribed();
      accepted = set;
      if (lastSubscribed != getSubscribed())
         observable.notifyChange(this);
   }
   
   public boolean getSubscribed() {
      return userId != null && pushToken != null && userSubscriptionSetting && accepted;
   }
   
   void persistAsFrom() {
      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_SUBSCRIPTION_LAST, userSubscriptionSetting);
      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_PLAYER_ID_LAST, userId);
      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_PUSH_TOKEN_LAST, pushToken);
      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST, accepted);
   }
   
   boolean compare(OSSubscriptionState from) {
      return userSubscriptionSetting != from.userSubscriptionSetting
          || !(userId != null ? userId : "").equals(from.userId != null ? from.userId : "")
          || !(pushToken != null ? pushToken : "").equals(from.pushToken != null ? from.pushToken : "")
          || accepted != from.accepted;
   }
   
   protected Object clone() {
      try {
         return super.clone();
      } catch (Throwable t) {}
      return null;
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
         
         mainObj.put("userSubscriptionSetting", userSubscriptionSetting);
         mainObj.put("subscribed", getSubscribed());
      }
      catch(Throwable t) {
         t.printStackTrace();
      }
      
      return mainObj;
   }
   
   @Override
   public String toString() {
      return toJSONObject().toString();
   }
}