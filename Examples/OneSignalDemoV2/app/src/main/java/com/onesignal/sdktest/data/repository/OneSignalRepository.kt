package com.onesignal.sdktest.data.repository

import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.data.network.OneSignalService
import com.onesignal.sdktest.data.network.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for all OneSignal SDK operations.
 * All methods are suspend functions to be called from coroutines on background threads.
 */
class OneSignalRepository {

    companion object {
        private const val TAG = "OneSignalRepository"
    }

    // User operations
    suspend fun loginUser(externalUserId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Logging in user with externalUserId: $externalUserId")
        OneSignal.login(externalUserId)
        Log.d(TAG, "Logged in user with onesignalId: ${OneSignal.User.onesignalId}")
    }

    suspend fun logoutUser() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Logging out user")
        OneSignal.logout()
    }

    // Alias operations
    fun addAlias(label: String, id: String) {
        Log.d(TAG, "Adding alias: $label -> $id")
        OneSignal.User.addAlias(label, id)
    }

    fun removeAlias(label: String) {
        Log.d(TAG, "Removing alias: $label")
        OneSignal.User.removeAlias(label)
    }

    fun removeAllAliases(labels: Collection<String>) {
        Log.d(TAG, "Removing all aliases: $labels")
        if (labels.isNotEmpty()) {
            OneSignal.User.removeAliases(labels)
        }
    }

    // Email operations
    fun addEmail(email: String) {
        Log.d(TAG, "Adding email: $email")
        OneSignal.User.addEmail(email)
    }

    fun removeEmail(email: String) {
        Log.d(TAG, "Removing email: $email")
        OneSignal.User.removeEmail(email)
    }

    // SMS operations
    fun addSms(smsNumber: String) {
        Log.d(TAG, "Adding SMS: $smsNumber")
        OneSignal.User.addSms(smsNumber)
    }

    fun removeSms(smsNumber: String) {
        Log.d(TAG, "Removing SMS: $smsNumber")
        OneSignal.User.removeSms(smsNumber)
    }

    // Tag operations
    fun addTag(key: String, value: String) {
        Log.d(TAG, "Adding tag: $key -> $value")
        OneSignal.User.addTag(key, value)
    }

    fun removeTag(key: String) {
        Log.d(TAG, "Removing tag: $key")
        OneSignal.User.removeTag(key)
    }

    fun getTags(): Map<String, String> {
        return OneSignal.User.getTags()
    }

    // Trigger operations
    fun addTrigger(key: String, value: String) {
        Log.d(TAG, "Adding trigger: $key -> $value")
        OneSignal.InAppMessages.addTrigger(key, value)
    }

    fun removeTrigger(key: String) {
        Log.d(TAG, "Removing trigger: $key")
        OneSignal.InAppMessages.removeTrigger(key)
    }

    fun clearTriggers(keys: Collection<String>) {
        Log.d(TAG, "Clearing all triggers: $keys")
        if (keys.isNotEmpty()) {
            OneSignal.InAppMessages.removeTriggers(keys)
        }
    }

    // Outcome operations
    fun sendOutcome(name: String) {
        Log.d(TAG, "Sending outcome: $name")
        OneSignal.Session.addOutcome(name)
    }

    fun sendUniqueOutcome(name: String) {
        Log.d(TAG, "Sending unique outcome: $name")
        OneSignal.Session.addUniqueOutcome(name)
    }

    fun sendOutcomeWithValue(name: String, value: Float) {
        Log.d(TAG, "Sending outcome with value: $name -> $value")
        OneSignal.Session.addOutcomeWithValue(name, value)
    }

    // Track Event
    fun trackEvent(name: String, value: Map<String, Any>?) {
        Log.d(TAG, "Tracking event: $name with value: $value")
        val properties: Map<String, Any?>? = if (!value.isNullOrEmpty()) {
            mapOf("value" to value)
        } else {
            null
        }
        OneSignal.User.trackEvent(name, properties)
    }

    // Push subscription
    fun getPushSubscriptionId(): String? {
        return OneSignal.User.pushSubscription.id
    }

    fun isPushEnabled(): Boolean {
        return OneSignal.User.pushSubscription.optedIn
    }

    fun setPushEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting push enabled: $enabled")
        if (enabled) {
            OneSignal.User.pushSubscription.optIn()
        } else {
            OneSignal.User.pushSubscription.optOut()
        }
    }

    // In-App Messaging
    fun isInAppMessagesPaused(): Boolean {
        return OneSignal.InAppMessages.paused
    }

    fun setInAppMessagesPaused(paused: Boolean) {
        Log.d(TAG, "Setting in-app messages paused: $paused")
        OneSignal.InAppMessages.paused = paused
    }

    // Location
    fun isLocationShared(): Boolean {
        return OneSignal.Location.isShared
    }

    fun setLocationShared(shared: Boolean) {
        Log.d(TAG, "Setting location shared: $shared")
        OneSignal.Location.isShared = shared
    }

    suspend fun promptLocation() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Prompting for location permission")
        OneSignal.Location.requestPermission()
    }

    // Notifications
    suspend fun promptPushPermission() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Prompting for push permission")
        OneSignal.Notifications.requestPermission(true)
    }

    fun hasNotificationPermission(): Boolean {
        return OneSignal.Notifications.permission
    }

    // Send notifications
    suspend fun sendNotification(type: NotificationType): Boolean {
        Log.d(TAG, "Sending notification: ${type.title}")
        return OneSignalService.sendNotification(type)
    }

    suspend fun sendCustomNotification(title: String, body: String): Boolean {
        Log.d(TAG, "Sending custom notification: $title")
        return OneSignalService.sendCustomNotification(title, body)
    }

    // Privacy consent
    fun setPrivacyConsent(granted: Boolean) {
        Log.d(TAG, "Setting privacy consent: $granted")
        OneSignal.consentGiven = granted
    }

    fun getPrivacyConsent(): Boolean {
        return OneSignal.consentGiven
    }

    // OneSignal ID
    fun getOneSignalId(): String? {
        return OneSignal.User.onesignalId
    }

    // Fetch user data from API
    suspend fun fetchUser(onesignalId: String): UserData? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching user data for: $onesignalId")
        OneSignalService.fetchUser(onesignalId)
    }
}
