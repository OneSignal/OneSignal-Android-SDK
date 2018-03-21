/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
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

import org.json.JSONException;
import org.json.JSONObject;

class OneSignalStateSynchronizer {

   private static UserStatePushSynchronizer userStatePushSynchronizer;
   private static UserStateEmailSynchronizer userStateEmailSynchronizer;

   static UserStatePushSynchronizer getPushStateSynchronizer() {
      if (userStatePushSynchronizer == null)
         userStatePushSynchronizer = new UserStatePushSynchronizer();
      return userStatePushSynchronizer;
   }

   static UserStateEmailSynchronizer getEmailStateSynchronizer() {
      if (userStateEmailSynchronizer == null)
         userStateEmailSynchronizer = new UserStateEmailSynchronizer();
      return userStateEmailSynchronizer;
   }
   
   static boolean persist() {
      boolean pushPersisted = getPushStateSynchronizer().persist();
      boolean emailPersisted = getEmailStateSynchronizer().persist();
      if (emailPersisted)
         emailPersisted = getEmailStateSynchronizer().getRegistrationId() != null;

      return pushPersisted || emailPersisted;
   }
   
   static void clearLocation() {
      getPushStateSynchronizer().clearLocation();
      getEmailStateSynchronizer().clearLocation();
   }

   static void initUserState() {
      getPushStateSynchronizer().initUserState();
      getEmailStateSynchronizer().initUserState();
   }

   static void syncUserState(boolean fromSyncService) {
      getPushStateSynchronizer().syncUserState(fromSyncService);
      getEmailStateSynchronizer().syncUserState(fromSyncService);
   }

   static void sendTags(JSONObject newTags) {
      try {
         JSONObject jsonField = new JSONObject().put("tags", newTags);
         getPushStateSynchronizer().sendTags(jsonField);
         getEmailStateSynchronizer().sendTags(jsonField);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static void syncHashedEmail(String email) {
      try {
         JSONObject emailFields = new JSONObject();
         emailFields.put("em_m", OSUtils.hexDigest(email, "MD5"));
         emailFields.put("em_s", OSUtils.hexDigest(email, "SHA-1"));

         getPushStateSynchronizer().syncHashedEmail(emailFields);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   static void setEmail(String email, String emailAuthHash) {
      getPushStateSynchronizer().setEmail(email, emailAuthHash);
      getEmailStateSynchronizer().setEmail(email, emailAuthHash);
   }

   static void setSubscription(boolean enable) {
      getPushStateSynchronizer().setSubscription(enable);
   }
   
   static boolean getUserSubscribePreference() {
      return getPushStateSynchronizer().getUserSubscribePreference();
   }
   
   static void setPermission(boolean enable) {
      getPushStateSynchronizer().setPermission(enable);
   }

   static void updateLocation(LocationGMS.LocationPoint point) {
      getPushStateSynchronizer().updateLocation(point);
      getEmailStateSynchronizer().updateLocation(point);
   }

   static boolean getSubscribed() {
      return getPushStateSynchronizer().getSubscribed();
   }

   static String getRegistrationId() {
      return getPushStateSynchronizer().getRegistrationId();
   }

   static UserStateSynchronizer.GetTagsResult getTags(boolean fromServer) {
      return getPushStateSynchronizer().getTags(fromServer);
   }

   static void resetCurrentState() {
      getPushStateSynchronizer().resetCurrentState();
      getEmailStateSynchronizer().resetCurrentState();

      OneSignal.saveUserId(null);
      OneSignal.saveEmailId(null);

      OneSignal.setLastSessionTime(-60 * 61);
   }

   static void updateDeviceInfo(JSONObject deviceInfo) {
      getPushStateSynchronizer().updateDeviceInfo(deviceInfo);
      getEmailStateSynchronizer().updateDeviceInfo(deviceInfo);
   }

   static void updatePushState(JSONObject pushState) {
      getPushStateSynchronizer().updateState(pushState);
   }

   static void refreshEmailState() {
      getEmailStateSynchronizer().refresh();
   }

   static void setSyncAsNewSession() {
      getPushStateSynchronizer().setSyncAsNewSession();
      getEmailStateSynchronizer().setSyncAsNewSession();
   }

   static void setSyncAsNewSessionForEmail() {
      getEmailStateSynchronizer().setSyncAsNewSession();
   }

   static void logoutEmail() {
      getPushStateSynchronizer().logoutEmail();
      getEmailStateSynchronizer().logoutEmail();
   }
}