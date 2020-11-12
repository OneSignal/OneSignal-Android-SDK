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

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import com.onesignal.OneSignal.ChangeTagsUpdateHandler;

import java.util.HashMap;

class OneSignalStateSynchronizer {

   enum UserStateSynchronizerType {
      PUSH,
      EMAIL;

      public boolean isPush() {
         return this.equals(PUSH);
      }

      public boolean isEmail() {
         return this.equals(EMAIL);
      }
   }

   // Each class abstracts from UserStateSynchronizer and this will allow us to handle different channels for specific method calls and requests
   // Each time we create a new UserStateSynchronizer we should add it to the userStateSynchronizers HashMap
   // Currently we have 2 channels:
   //    1. Push
   //    2. Email
   //    Add more channels...
   private static HashMap<UserStateSynchronizerType, UserStateSynchronizer> userStateSynchronizers =  new HashMap<>();

   // #1 UserStateSynchronizer -> Push Channel
   static UserStatePushSynchronizer getPushStateSynchronizer() {
      if (!userStateSynchronizers.containsKey(UserStateSynchronizerType.PUSH) || userStateSynchronizers.get(UserStateSynchronizerType.PUSH) == null)
         userStateSynchronizers.put(UserStateSynchronizerType.PUSH, new UserStatePushSynchronizer());

      return (UserStatePushSynchronizer) userStateSynchronizers.get(UserStateSynchronizerType.PUSH);
   }

   // #2 UserStateSynchronizer -> Email Channel
   static UserStateEmailSynchronizer getEmailStateSynchronizer() {
      if (!userStateSynchronizers.containsKey(UserStateSynchronizerType.EMAIL) || userStateSynchronizers.get(UserStateSynchronizerType.EMAIL) == null)
         userStateSynchronizers.put(UserStateSynchronizerType.EMAIL, new UserStateEmailSynchronizer());

      return (UserStateEmailSynchronizer) userStateSynchronizers.get(UserStateSynchronizerType.EMAIL);
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

   static void sendTags(JSONObject newTags, @Nullable ChangeTagsUpdateHandler handler) {
      try {
         JSONObject jsonField = new JSONObject().put("tags", newTags);
         getPushStateSynchronizer().sendTags(jsonField, handler);
         getEmailStateSynchronizer().sendTags(jsonField, handler);
      } catch (JSONException e) {
         if (handler != null)
            handler.onFailure(new OneSignal.SendTagsError(-1, "Encountered an error attempting to serialize your tags into JSON: " + e.getMessage() + "\n" + e.getStackTrace()));
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

   static void updateLocation(LocationController.LocationPoint point) {
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

   static void setNewSession() {
      getPushStateSynchronizer().setNewSession();
      getEmailStateSynchronizer().setNewSession();
   }

   static boolean getSyncAsNewSession() {
      return getPushStateSynchronizer().getSyncAsNewSession() ||
             getEmailStateSynchronizer().getSyncAsNewSession();
   }

   static void setNewSessionForEmail() {
      getEmailStateSynchronizer().setNewSession();
   }

   static void logoutEmail() {
      getPushStateSynchronizer().logoutEmail();
      getEmailStateSynchronizer().logoutEmail();
   }

   static void setExternalUserId(String externalId, String externalIdAuthHash, final OneSignal.OSExternalUserIdUpdateCompletionHandler completionHandler) throws JSONException {
      final JSONObject responses = new JSONObject();
      // Create a handler for internal usage, this is where the developers completion handler will be called,
      //  which happens once all handlers for each channel have been processed
      OneSignal.OSInternalExternalUserIdUpdateCompletionHandler handler = new OneSignal.OSInternalExternalUserIdUpdateCompletionHandler() {
         @Override
         public void onComplete(String channel, boolean success) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Completed request to update external user id for channel: " + channel + " and success: " + success);
            try {
               // Latest channel success status will be overwriting previous ones since we only care about latest request status
               // That way the completion handler is allowing the developer to handle the most recent scenario
               responses.put(channel, new JSONObject().put("success", success));
            } catch (JSONException e) {
               OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Error while adding the success status of external id for channel: " + channel);
               e.printStackTrace();
            }

            for (UserStateSynchronizer userStateSynchronizer : userStateSynchronizers.values()) {
               if (userStateSynchronizer.hasQueuedHandlers()) {
                  OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "External user id handlers are still being processed for channel: " + userStateSynchronizer.getChannelString() + " , wait until finished before proceeding");
                  return;
               }
            }

            // Need to call completion handler on main thread since the request response came from an async PUT
            OSUtils.runOnMainUIThread(new Runnable() {
               @Override
               public void run() {
                  if (completionHandler != null)
                     completionHandler.onComplete(responses);
               }
            });
         }
      };

      getPushStateSynchronizer().setExternalUserId(externalId, externalIdAuthHash, handler);

      // Make sure we are only setting external user id for email when an email is actually set
      if (OneSignal.hasEmailId())
         getEmailStateSynchronizer().setExternalUserId(externalId, externalIdAuthHash, handler);
   }

   // This is to indicate that StateSynchronizer can start making REST API calls
   // We do this to roll up as many field updates in a single create / on_session call to
   //   optimize the number of api calls that are made
   static void readyToUpdate(boolean canMakeUpdates) {
      getPushStateSynchronizer().readyToUpdate(canMakeUpdates);
      getEmailStateSynchronizer().readyToUpdate(canMakeUpdates);
   }

}