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

import org.json.JSONException;
import org.json.JSONObject;

class OneSignalStateSynchronizer {

   private static UserStateSynchronizer userStateSynchronizer;

   static UserStateSynchronizer getPushStateSynchronizer() {
      if (userStateSynchronizer == null)
         userStateSynchronizer = new UserStatePushSynchronizer();
      return userStateSynchronizer;
   }
   
   static boolean stopAndPersist() {
      return getPushStateSynchronizer().stopAndPersist();
   }
   
   static void clearLocation() {
      getPushStateSynchronizer().clearLocation();
   }



   static void initUserState() {
      getPushStateSynchronizer().initUserState();
   }

   static UserState getNewUserState() {
      return new UserState("nonPersist", false);
   }

   static void syncUserState(boolean fromSyncService) {
      getPushStateSynchronizer().syncUserState(fromSyncService);
   }

   static void postUpdate(UserState postSession, boolean isSession) {
      getPushStateSynchronizer().postUpdate(postSession, isSession);
   }

   static void sendTags(JSONObject newTags) {
      try {
         getPushStateSynchronizer().sendTags(new JSONObject().put("tags", newTags));
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

   public static void setEmail(String email) {
      try {
         JSONObject emailJSON = new JSONObject();
         emailJSON.put("email", email);

         getPushStateSynchronizer().setEmail(emailJSON);
         // TODO: Add getEmailStateSynchronizer().setEmail(emailJSON);

      } catch (Throwable t) {
         t.printStackTrace();
      }
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
      // TODO: Add getEmailState....
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

      OneSignal.saveUserId(null);
      // TODO: Email user Id should be set to null as well.

      OneSignal.setLastSessionTime(-60 * 61);
   }
}