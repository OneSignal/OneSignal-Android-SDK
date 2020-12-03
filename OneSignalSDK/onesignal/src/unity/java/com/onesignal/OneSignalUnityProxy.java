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
import android.support.annotation.Nullable;
import com.onesignal.OneSignal;
import java.lang.reflect.Method;
import org.json.JSONException;
import org.json.JSONObject;

public class OneSignalUnityProxy implements OneSignal.NotificationOpenedHandler, OneSignal.NotificationReceivedHandler, OSPermissionObserver, OSSubscriptionObserver, OSEmailSubscriptionObserver, OneSignal.InAppMessageClickHandler {
   private static String unityListenerName;
   private static Method unitySendMessage;

   public OneSignalUnityProxy(String listenerName, String googleProjectNumber, String oneSignalAppId, int logLevel, int visualLogLevel, boolean requiresUserPrivacyConsent) {
      unityListenerName = listenerName;
      try {
         OneSignal.setRequiresUserPrivacyConsent(requiresUserPrivacyConsent);
         Class unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
         unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", new Class[]{String.class, String.class, String.class});
         OneSignal.sdkType = BuildConfig.BUILD_TYPE;
         OneSignal.setLogLevel(logLevel, visualLogLevel);
         OneSignal.Builder builder = OneSignal.getCurrentOrNewInitBuilder();
         builder.unsubscribeWhenNotificationsAreDisabled(true);
         builder.filterOtherGCMReceivers(true);
         builder.setInAppMessageClickHandler(this);
         OneSignal.init((Activity) unityPlayerClass.getField("currentActivity").get((Object) null), googleProjectNumber, oneSignalAppId, this, this);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   public void notificationOpened(OSNotificationOpenResult result) {
      unitySafeInvoke("onPushNotificationOpened", result.toJSONObject().toString());
   }

   public void notificationReceived(OSNotification notification) {
      unitySafeInvoke("onPushNotificationReceived", notification.toJSONObject().toString());
   }

   public void sendTag(String key, String value) {
      OneSignal.sendTag(key, value);
   }

   public void sendTags(String json) {
      OneSignal.sendTags(json);
   }

   public void setEmail(final String delegateIdSuccess, final String delegateIdFailure, String email, String authHash) {
      OneSignal.setEmail(email, authHash, new OneSignal.EmailUpdateHandler() {
         public void onSuccess() {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("success", delegateIdSuccess).put("failure", delegateIdFailure).toString());
               params.put("response", "success");
               OneSignalUnityProxy.unitySafeInvoke("onSetEmailSuccess", params.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }

         public void onFailure(OneSignal.EmailUpdateError error) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("success", delegateIdSuccess).put("failure", delegateIdFailure).toString());
               params.put("response", error.getMessage());
               OneSignalUnityProxy.unitySafeInvoke("onSetEmailFailure", params.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void logoutEmail(final String delegateIdSuccess, final String delegateIdFailure) {
      OneSignal.logoutEmail(new OneSignal.EmailUpdateHandler() {
         public void onSuccess() {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("success", delegateIdSuccess).put("failure", delegateIdFailure).toString());
               params.put("response", "success");
               OneSignalUnityProxy.unitySafeInvoke("onLogoutEmailSuccess", params.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }

         public void onFailure(OneSignal.EmailUpdateError error) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("success", delegateIdSuccess).put("failure", delegateIdFailure).toString());
               params.put("response", error.getMessage());
               OneSignalUnityProxy.unitySafeInvoke("onLogoutEmailFailure", params.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void getTags(final String delegateId) {
      OneSignal.getTags(new OneSignal.GetTagsHandler() {
         public void tagsAvailable(JSONObject tags) {
            String tagsStr;
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", delegateId);
               if (tags != null) {
                  tagsStr = tags.toString();
               } else {
                  tagsStr = "{}";
               }
               params.put("response", tagsStr);
               OneSignalUnityProxy.unitySafeInvoke("onTagsReceived", params.toString());
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

   public void idsAvailable(final String delegateId) {
      OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
         public void idsAvailable(String userId, String registrationId) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", delegateId);
               JSONObject jsonIds = new JSONObject();
               jsonIds.put("userId", userId);
               if (registrationId != null) {
                  jsonIds.put("pushToken", registrationId);
               } else {
                  jsonIds.put("pushToken", "");
               }
               params.put("response", jsonIds.toString());
               OneSignalUnityProxy.unitySafeInvoke("onIdsAvailable", params.toString());
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      });
   }

   public void enableSound(boolean enable) {
      OneSignal.enableSound(enable);
   }

   public void enableVibrate(boolean enable) {
      OneSignal.enableVibrate(enable);
   }

   public void setInFocusDisplaying(int displayOption) {
      OneSignal.setInFocusDisplaying(displayOption);
   }

   public void setSubscription(boolean enable) {
      OneSignal.setSubscription(enable);
   }

   public void postNotification(final String delegateIdSuccess, final String delegateIdFailure, String json) {
      OneSignal.postNotification(json, (OneSignal.PostNotificationResponseHandler) new OneSignal.PostNotificationResponseHandler() {
         public void onSuccess(JSONObject response) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("success", delegateIdSuccess).put("failure", delegateIdFailure).toString());
               if (response == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onPostNotificationSuccess", params.toString());
                  return;
               }
               params.put("response", response.toString());
               OneSignalUnityProxy.unitySafeInvoke("onPostNotificationSuccess", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }

         public void onFailure(JSONObject response) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("success", delegateIdSuccess).put("failure", delegateIdFailure));
               if (response == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onPostNotificationFailed", params.toString());
                  return;
               }
               params.put("response", response.toString());
               OneSignalUnityProxy.unitySafeInvoke("onPostNotificationFailed", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
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

   public void addEmailSubscriptionObserver() {
      OneSignal.addEmailSubscriptionObserver(this);
   }

   public void removeEmailSubscriptionObserver() {
      OneSignal.removeEmailSubscriptionObserver(this);
   }

   public boolean userProvidedPrivacyConsent() {
      return OneSignal.userProvidedPrivacyConsent();
   }

   public void provideUserConsent(boolean granted) {
      OneSignal.provideUserConsent(granted);
   }

   public void setRequiresUserPrivacyConsent(boolean required) {
      OneSignal.setRequiresUserPrivacyConsent(required);
   }

   public void setExternalUserId(String externalId) {
      OneSignal.setExternalUserId(externalId);
   }

   public void setExternalUserId(final String delegateId, String externalId) {
      OneSignal.setExternalUserId(externalId, new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
         @Override
         public void onComplete(JSONObject results) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("completion", delegateId).toString());
               if (results == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onExternalUserIdUpdateCompletion", params.toString());
                  return;
               }
               params.put("response", results.toString());
               OneSignalUnityProxy.unitySafeInvoke("onExternalUserIdUpdateCompletion", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   public void setExternalUserId(final String delegateId, String externalId, String externalIdAuthHash) {
      OneSignal.setExternalUserId(externalId, externalIdAuthHash, new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
         @Override
         public void onComplete(JSONObject results) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", new JSONObject().put("completion", delegateId).toString());
               if (results == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onExternalUserIdUpdateCompletion", params.toString());
                  return;
               }
               params.put("response", results.toString());
               OneSignalUnityProxy.unitySafeInvoke("onExternalUserIdUpdateCompletion", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   public void removeExternalUserId() {
      OneSignal.removeExternalUserId();
   }

   public void removeExternalUserId(final String delegateId) {
      OneSignal.removeExternalUserId(new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
         @Override
         public void onComplete(JSONObject results) {
             try {
                 JSONObject params = new JSONObject();
                 params.put("delegate_id", new JSONObject().put("completion", delegateId).toString());
                 if (results == null) {
                     params.put("response", "");
                     OneSignalUnityProxy.unitySafeInvoke("onExternalUserIdUpdateCompletion", params.toString());
                     return;
                 }
                 params.put("response", results.toString());
                 OneSignalUnityProxy.unitySafeInvoke("onExternalUserIdUpdateCompletion", params.toString());
             } catch (JSONException e) {
                 e.printStackTrace();
             }
         }
      });
   }

   public String getPermissionSubscriptionState() {
      return OneSignal.getPermissionSubscriptionState().toJSONObject().toString();
   }

   public void setLocationShared(boolean shared) {
      OneSignal.setLocationShared(shared);
   }

   public void addTrigger(String key, String value) {
      OneSignal.addTrigger(key, value);
   }

   public void addTriggers(String jsonString) {
      OneSignal.addTriggersFromJsonString(jsonString);
   }

   public void removeTriggerForKey(String key) {
      OneSignal.removeTriggerForKey(key);
   }

   public void removeTriggersForKeys(String keys) {
      OneSignal.removeTriggersForKeysFromJsonArrayString(keys);
   }

   public String getTriggerValueForKey(final String key) throws JSONException {
      return new JSONObject() {
         {
            put("value", OneSignal.getTriggerValueForKey(key));
         }
      }.toString();
   }

   public void pauseInAppMessages(boolean pause) {
      OneSignal.pauseInAppMessages(pause);
   }

   public void sendOutcome(final String delegateId, String name) {
      OneSignal.sendOutcome(name, new OneSignal.OutcomeCallback() {
         public void onSuccess(@Nullable OutcomeEvent outcomeEvent) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", delegateId);
               if (outcomeEvent == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onSendOutcomeSuccess", params.toString());
                  return;
               }
               params.put("response", outcomeEvent.toJSONObject().toString());
               OneSignalUnityProxy.unitySafeInvoke("onSendOutcomeSuccess", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   public void sendUniqueOutcome(final String delegateId, String name) {
      OneSignal.sendUniqueOutcome(name, new OneSignal.OutcomeCallback() {
         public void onSuccess(@Nullable OutcomeEvent outcomeEvent) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", delegateId);
               if (outcomeEvent == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onSendOutcomeSuccess", params.toString());
                  return;
               }
               params.put("response", outcomeEvent.toJSONObject().toString());
               OneSignalUnityProxy.unitySafeInvoke("onSendOutcomeSuccess", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   public void sendOutcomeWithValue(final String delegateId, String name, float value) {
      OneSignal.sendOutcomeWithValue(name, value, new OneSignal.OutcomeCallback() {
         public void onSuccess(@Nullable OutcomeEvent outcomeEvent) {
            try {
               JSONObject params = new JSONObject();
               params.put("delegate_id", delegateId);
               if (outcomeEvent == null) {
                  params.put("response", "");
                  OneSignalUnityProxy.unitySafeInvoke("onSendOutcomeSuccess", params.toString());
                  return;
               }
               params.put("response", outcomeEvent.toJSONObject().toString());
               OneSignalUnityProxy.unitySafeInvoke("onSendOutcomeSuccess", params.toString());
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
      unitySafeInvoke("onOSPermissionChanged", stateChanges.toJSONObject().toString());
   }

   public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
      unitySafeInvoke("onOSSubscriptionChanged", stateChanges.toJSONObject().toString());
   }

   public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
      unitySafeInvoke("onOSEmailSubscriptionChanged", stateChanges.toJSONObject().toString());
   }

   public void inAppMessageClicked(OSInAppMessageAction result) {
      unitySafeInvoke("onInAppMessageClicked", result.toJSONObject().toString());
   }

   /* access modifiers changed from: private */
   public static void unitySafeInvoke(String method, String params) {
      try {
         unitySendMessage.invoke((Object) null, new Object[]{unityListenerName, method, params});
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }
}
