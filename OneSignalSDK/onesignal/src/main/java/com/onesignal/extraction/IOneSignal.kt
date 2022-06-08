package com.onesignal.extraction;

import android.content.Context;

import com.onesignal.OSDeviceState;
import com.onesignal.OSEmailSubscriptionObserver;
import com.onesignal.OSInAppMessageLifecycleHandler;
import com.onesignal.OSPermissionObserver;
import com.onesignal.OSSMSSubscriptionObserver;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OneSignal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

public interface IOneSignal {
    /** GENERAL **/
    fun getSdkVersionRaw() : String     /** OneSignal.sdkVersion **/
    fun getDeviceState() : OSDeviceState /** Covered by all the other state now? i.e. User.Subscriptions ?? **/

    /** INITIALIZATION **/
    fun setAppId(newAppId: String)        /** OneSignal.init, is there a reason these are split up? **/
    fun initWithContext(context: Context) /** OneSignal.init, is there a reason these are split up? **/

    /** USER-CONSENT **/
    fun userProvidedPrivacyConsent() : Boolean               /** OneSignal.privacyConsent (get) **/
    fun provideUserConsent(consent: Boolean)                 /** OneSignal.privacyConsent (set) **/
    fun requiresUserPrivacyConsent() : Boolean               /** OneSignal.requiresPrivacyConsent **/
    fun setRequiresUserPrivacyConsent(required: Boolean)     /** OneSignal.requiresPrivacyConsent **/
    /** USER-SMS **/
    fun setSMSNumber(smsNumber: String, callback: OneSignal.OSSMSUpdateHandler)
    fun setSMSNumber(smsNumber: String)                      /** OneSignal.user.addSmsSubscription **/
    fun setSMSNumber(smsNumber: String, smsAuthHash: String) /** OneSignal.user.addSmsSubscription **/
    fun setSMSNumber(smsNumber: String, smsAuthHash: String, callback: OneSignal.OSSMSUpdateHandler)
    fun logoutSMSNumber()                                    /** OneSignal.user.removeSubscription **/
    fun logoutSMSNumber(callback: OneSignal.OSSMSUpdateHandler)
    /** USER-EMAIL **/
    fun setEmail(email: String)                              /** OneSignal.user.addEmail **/
    fun setEmail(email: String, callback: OneSignal.EmailUpdateHandler)
    fun setEmail(email: String, emailAuthHash: String)       /** OneSignal.user.addEmail **/
    fun setEmail(email: String, emailAuthHash: String, callback: OneSignal.EmailUpdateHandler)
    fun logoutEmail()                                        /** OneSignal.user.removeSubscription **/
    fun logoutEmail(callback: OneSignal.EmailUpdateHandler)
    /** USER-LANGUAGE **/
    fun setLanguage(language: String)
    fun setLanguage(language: String, callback: OneSignal.OSSetLanguageCompletionHandler)
    /** USER-EXTERNALID **/
    fun setExternalUserId(externalId: String) /** OneSignal.user.changeExternalId **/
    fun setExternalUserId(externalId: String, callback: OneSignal.OSExternalUserIdUpdateCompletionHandler)
    fun setExternalUserId(externalId: String, externalIdAuthHash: String) /** OneSignal.user.changeExternalId **/
    fun setExternalUserId(externalId: String, externalIdAuthHash: String, callback: OneSignal.OSExternalUserIdUpdateCompletionHandler)
    fun removeExternalUserId() /** OneSignal.user.changeExternalId (change it to null or blank) **/
    fun removeExternalUserId(callback: OneSignal.OSExternalUserIdUpdateCompletionHandler)

    /** LOGGING **/
    fun onesignalLog(level: OneSignal.LOG_LEVEL, message: String) /** Logging.Log **/
    fun setLogLevel(inLogCatLevel: OneSignal.LOG_LEVEL, inVisualLogLevel: OneSignal.LOG_LEVEL) /** Logging.logLevel **/
    fun setLogLevel(inLogCatLevel: Int , inVisualLogLevel: Int)  /** Logging.logLevel **/

    /** TAGS **/
    fun sendTag(key: String, value: String) /** OneSignal.user.tags.set **/
    fun sendTags(jsonString: String)        /** OneSignal.user.tags.set **/
    fun sendTags(keyValues: JSONObject)     /** OneSignal.user.tags.set **/
    fun sendTags(keyValues: JSONObject, callback: OneSignal.ChangeTagsUpdateHandler)
    fun getTags(callback: OneSignal.OSGetTagsHandler) /** OneSignal.user.tags.get **/
    fun deleteTag(key: String) /** OneSignal.user.tags.remove **/
    fun deleteTag(key: String, callback: OneSignal.ChangeTagsUpdateHandler)
    fun deleteTags(keys: Collection<String>) /** OneSignal.user.tags.remove **/
    fun deleteTags(keys: Collection<String>, callback: OneSignal.ChangeTagsUpdateHandler)
    fun deleteTags(jsonArrayString: String)
    fun deleteTags(jsonArrayString: String, callback: OneSignal.ChangeTagsUpdateHandler)
    fun deleteTags(jsonArray: JSONArray, callback: OneSignal.ChangeTagsUpdateHandler) /** OneSignal.user.tags.remove but no callback **/

    /** LOCATION TRACKING **/
    fun isLocationShared() : Boolean  /** OneSignal.locationManager.isLocationShared **/
    fun promptLocation() /** OneSignal.locationManager.promptLocation **/
    fun setLocationShared(enable: Boolean) /** OneSignal.locationManager.isLocationShared **/


    /** PUSH NOTIFICATIONS **/
    fun postNotification(json: String, callback: OneSignal.PostNotificationResponseHandler) /** OneSignal.notificationManager..postNotification **/
    fun postNotification(json: JSONObject, callback: OneSignal.PostNotificationResponseHandler) /** OneSignal.notificationManager..postNotification **/
    fun disablePush(disable: Boolean) /** OneSignal.user.subscriptions<PushNotification>.enabled **/
    fun disableGMSMissingPrompt(promptDisable: Boolean)
    fun clearOneSignalNotifications() /** OneSignal.notificationManager..clearAll **/
    fun removeNotification(id: Int) /** OneSignal.notificationManager..removeNotification **/
    fun removeGroupedNotifications(group: String) /** OneSignal.notificationManager..removeGroupNotifications **/
    fun unsubscribeWhenNotificationsAreDisabled(set: Boolean)
    fun setNotificationWillShowInForegroundHandler(callback: OneSignal.OSNotificationWillShowInForegroundHandler) /** NotificationManager.setNotificationWillShowInForegroundHandler **/
    fun setNotificationOpenedHandler(callback: OneSignal.OSNotificationOpenedHandler) /** NotificationManager.setNotificationOpenedHandler **/

    /** OBSERVERS **/
    fun addPermissionObserver(observer: OSPermissionObserver) /** OneSignal.notificationManager.addPushPermissionHandler */
    fun removePermissionObserver(observer: OSPermissionObserver) /** OneSignal.notificationManager.removePermissionObserver */
    fun addSubscriptionObserver(observer: OSSubscriptionObserver) /** OneSignal.user.subscriptions.removeObserver */
    fun removeSubscriptionObserver(observer: OSSubscriptionObserver) /** OneSignal.user.subscriptions.removeObserver */
    fun addEmailSubscriptionObserver(observer: OSEmailSubscriptionObserver) /** OneSignal.user.subscriptions.removeObserver */
    fun removeEmailSubscriptionObserver(observer: OSEmailSubscriptionObserver) /** OneSignal.user.subscriptions.removeObserver */
    fun addSMSSubscriptionObserver(observer: OSSMSSubscriptionObserver) /** OneSignal.user.subscriptions.removeObserver */
    fun removeSMSSubscriptionObserver(observer: OSSMSSubscriptionObserver) /** OneSignal.user.subscriptions.removeObserver */

    /** TRIGGERS TODO: String only? **/
    fun addTriggers(triggers: Map<String, Object>) /** OneSignal.user.triggers.set **/
    fun addTrigger(key: String, obj: Object) /** OneSignal.user.triggers.set **/
    fun removeTriggersForKeys(keys: Collection<String>) /** OneSignal.user.triggers.remove **/
    fun removeTriggerForKey(key: String) /** OneSignal.user.triggers.remove **/
    fun getTriggerValueForKey(key: String) : Object /** OneSignal.user.triggers.get **/
    fun getTriggers() : Map<String, Object> /** OneSignal.user.triggers.getAll */

    /** IAM **/
    fun pauseInAppMessages(pause: Boolean) /** OneSignal.iamManager.paused (set) **/
    fun isInAppMessagingPaused() : Boolean /** OneSignal.iamManager.paused (get) **/
    fun setInAppMessageLifecycleHandler(callback: OSInAppMessageLifecycleHandler)  /** OneSignal.iamManager.setInAppMessageLifecycleHandler **/
    fun setInAppMessageClickHandler(callback: OneSignal.OSInAppMessageClickHandler) /** OneSignal.iamManager.setInAppMessageClickHandler **/

    /** OUTCOMES **/
    fun sendOutcome(name: String) /** OneSignal.user.sendOutcome **/
    fun sendOutcome(name: String, callback: OneSignal.OutcomeCallback)
    fun sendUniqueOutcome(name: String) /** OneSignal.user.sendUniqueOutcome **/
    fun sendUniqueOutcome(name: String, callback: OneSignal.OutcomeCallback)
    fun sendOutcomeWithValue(name: String, value: Float) /** OneSignal.user.sendOutcomeWithValue **/
    fun sendOutcomeWithValue(name: String, value: Float, callback: OneSignal.OutcomeCallback)
}
