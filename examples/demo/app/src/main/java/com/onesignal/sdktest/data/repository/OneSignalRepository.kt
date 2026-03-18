package com.onesignal.sdktest.data.repository

import com.onesignal.OneSignal
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.data.network.OneSignalService
import com.onesignal.sdktest.data.network.UserData
import com.onesignal.sdktest.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OneSignalRepository {

    companion object {
        private const val TAG = "OneSignalRepository"
    }

    suspend fun loginUser(externalUserId: String) = withContext(Dispatchers.IO) {
        LogManager.d(TAG, "Logging in user: $externalUserId")
        OneSignal.login(externalUserId)
    }

    suspend fun logoutUser() = withContext(Dispatchers.IO) {
        LogManager.d(TAG, "Logging out user")
        OneSignal.logout()
    }

    fun addAlias(label: String, id: String) {
        LogManager.d(TAG, "Adding alias: $label -> $id")
        OneSignal.User.addAlias(label, id)
    }

    fun addAliases(aliases: Map<String, String>) {
        LogManager.d(TAG, "Adding aliases: $aliases")
        OneSignal.User.addAliases(aliases)
    }

    fun removeAlias(label: String) {
        LogManager.d(TAG, "Removing alias: $label")
        OneSignal.User.removeAlias(label)
    }

    fun removeAliases(labels: Collection<String>) {
        if (labels.isNotEmpty()) {
            LogManager.d(TAG, "Removing aliases: $labels")
            OneSignal.User.removeAliases(labels)
        }
    }

    fun addEmail(email: String) {
        LogManager.d(TAG, "Adding email: $email")
        OneSignal.User.addEmail(email)
    }

    fun removeEmail(email: String) {
        LogManager.d(TAG, "Removing email: $email")
        OneSignal.User.removeEmail(email)
    }

    fun addSms(smsNumber: String) {
        LogManager.d(TAG, "Adding SMS: $smsNumber")
        OneSignal.User.addSms(smsNumber)
    }

    fun removeSms(smsNumber: String) {
        LogManager.d(TAG, "Removing SMS: $smsNumber")
        OneSignal.User.removeSms(smsNumber)
    }

    fun addTag(key: String, value: String) {
        LogManager.d(TAG, "Adding tag: $key -> $value")
        OneSignal.User.addTag(key, value)
    }

    fun addTags(tags: Map<String, String>) {
        LogManager.d(TAG, "Adding tags: $tags")
        OneSignal.User.addTags(tags)
    }

    fun removeTag(key: String) {
        LogManager.d(TAG, "Removing tag: $key")
        OneSignal.User.removeTag(key)
    }

    fun removeTags(keys: Collection<String>) {
        if (keys.isNotEmpty()) {
            LogManager.d(TAG, "Removing tags: $keys")
            OneSignal.User.removeTags(keys)
        }
    }

    fun getTags(): Map<String, String> = OneSignal.User.getTags()

    fun addTrigger(key: String, value: String) {
        LogManager.d(TAG, "Adding trigger: $key -> $value")
        OneSignal.InAppMessages.addTrigger(key, value)
    }

    fun addTriggers(triggers: Map<String, String>) {
        LogManager.d(TAG, "Adding triggers: $triggers")
        OneSignal.InAppMessages.addTriggers(triggers)
    }

    fun removeTrigger(key: String) {
        LogManager.d(TAG, "Removing trigger: $key")
        OneSignal.InAppMessages.removeTrigger(key)
    }

    fun clearTriggers(keys: Collection<String>) {
        if (keys.isNotEmpty()) {
            LogManager.d(TAG, "Clearing triggers: $keys")
            OneSignal.InAppMessages.removeTriggers(keys)
        }
    }

    fun sendOutcome(name: String) {
        LogManager.d(TAG, "Sending outcome: $name")
        OneSignal.Session.addOutcome(name)
    }

    fun sendUniqueOutcome(name: String) {
        LogManager.d(TAG, "Sending unique outcome: $name")
        OneSignal.Session.addUniqueOutcome(name)
    }

    fun sendOutcomeWithValue(name: String, value: Float) {
        LogManager.d(TAG, "Sending outcome with value: $name -> $value")
        OneSignal.Session.addOutcomeWithValue(name, value)
    }

    fun trackEvent(name: String, properties: Map<String, Any?>?) {
        LogManager.d(TAG, "Tracking event: $name with properties: $properties")
        OneSignal.User.trackEvent(name, properties)
    }

    fun getPushSubscriptionId(): String? = OneSignal.User.pushSubscription.id
    fun isPushEnabled(): Boolean = OneSignal.User.pushSubscription.optedIn

    fun setPushEnabled(enabled: Boolean) {
        LogManager.d(TAG, "Setting push enabled: $enabled")
        if (enabled) OneSignal.User.pushSubscription.optIn()
        else OneSignal.User.pushSubscription.optOut()
    }

    suspend fun promptPushPermission() {
        LogManager.d(TAG, "Prompting for push permission")
        OneSignal.Notifications.requestPermission(true)
    }

    fun hasNotificationPermission(): Boolean = OneSignal.Notifications.permission

    fun clearAllNotifications() {
        LogManager.d(TAG, "Clearing all notifications")
        OneSignal.Notifications.clearAllNotifications()
    }

    fun isInAppMessagesPaused(): Boolean = OneSignal.InAppMessages.paused

    fun setInAppMessagesPaused(paused: Boolean) {
        LogManager.d(TAG, "Setting in-app messages paused: $paused")
        OneSignal.InAppMessages.paused = paused
    }

    fun isLocationShared(): Boolean = OneSignal.Location.isShared

    fun setLocationShared(shared: Boolean) {
        LogManager.d(TAG, "Setting location shared: $shared")
        OneSignal.Location.isShared = shared
    }

    suspend fun promptLocation() = withContext(Dispatchers.IO) {
        LogManager.d(TAG, "Prompting for location permission")
        OneSignal.Location.requestPermission()
    }

    fun setConsentRequired(required: Boolean) {
        LogManager.d(TAG, "Setting consent required: $required")
        OneSignal.consentRequired = required
    }

    fun getConsentRequired(): Boolean = OneSignal.consentRequired

    fun setPrivacyConsent(granted: Boolean) {
        LogManager.d(TAG, "Setting privacy consent: $granted")
        OneSignal.consentGiven = granted
    }

    fun getPrivacyConsent(): Boolean = OneSignal.consentGiven

    fun getOneSignalId(): String? = OneSignal.User.onesignalId

    suspend fun sendNotification(type: NotificationType): Boolean {
        LogManager.d(TAG, "Sending notification: ${type.title}")
        return OneSignalService.sendNotification(type)
    }

    suspend fun sendCustomNotification(title: String, body: String): Boolean {
        LogManager.d(TAG, "Sending custom notification: $title")
        return OneSignalService.sendCustomNotification(title, body)
    }

    suspend fun fetchUser(onesignalId: String): UserData? {
        LogManager.d(TAG, "Fetching user data for: $onesignalId")
        return OneSignalService.fetchUser(onesignalId)
    }
}
