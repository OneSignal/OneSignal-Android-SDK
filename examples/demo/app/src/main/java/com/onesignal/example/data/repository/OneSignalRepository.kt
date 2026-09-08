package com.onesignal.example.data.repository

import com.onesignal.OneSignal
import com.onesignal.example.data.model.NotificationType
import com.onesignal.example.data.network.OneSignalService
import com.onesignal.example.data.network.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.onesignal.example.util.DemoLog

/**
 * Repository for all OneSignal SDK operations.
 * All methods are suspend functions to be called from coroutines on background threads.
 */
class OneSignalRepository {

    // User operations
    suspend fun loginUser(externalUserId: String, jwtToken: String? = null) = withContext(Dispatchers.IO) {
        DemoLog.d("Logging in user with externalUserId: $externalUserId, jwt: ${if (jwtToken != null) "provided" else "none"}")
        OneSignal.login(externalUserId, jwtToken)
        DemoLog.d("Logged in user with onesignalId: ${OneSignal.User.onesignalId}")
    }

    suspend fun updateUserJwt(externalUserId: String, jwtToken: String) = withContext(Dispatchers.IO) {
        DemoLog.d("Updating JWT for externalUserId: $externalUserId")
        OneSignal.updateUserJwt(externalUserId, jwtToken)
    }

    suspend fun logoutUser() = withContext(Dispatchers.IO) {
        DemoLog.d("Logging out user")
        OneSignal.logout()
    }

    // Alias operations
    fun addAlias(label: String, id: String) {
        DemoLog.d("Adding alias: $label -> $id")
        OneSignal.User.addAlias(label, id)
    }

    fun addAliases(aliases: Map<String, String>) {
        DemoLog.d("Adding aliases: $aliases")
        OneSignal.User.addAliases(aliases)
    }

    fun removeAlias(label: String) {
        DemoLog.d("Removing alias: $label")
        OneSignal.User.removeAlias(label)
    }

    fun removeAliases(labels: Collection<String>) {
        DemoLog.d("Removing aliases: $labels")
        if (labels.isNotEmpty()) {
            OneSignal.User.removeAliases(labels)
        }
    }

    // Email operations
    fun addEmail(email: String) {
        DemoLog.d("Adding email: $email")
        OneSignal.User.addEmail(email)
    }

    fun removeEmail(email: String) {
        DemoLog.d("Removing email: $email")
        OneSignal.User.removeEmail(email)
    }

    // SMS operations
    fun addSms(smsNumber: String) {
        DemoLog.d("Adding SMS: $smsNumber")
        OneSignal.User.addSms(smsNumber)
    }

    fun removeSms(smsNumber: String) {
        DemoLog.d("Removing SMS: $smsNumber")
        OneSignal.User.removeSms(smsNumber)
    }

    // Tag operations
    fun addTag(key: String, value: String) {
        DemoLog.d("Adding tag: $key -> $value")
        OneSignal.User.addTag(key, value)
    }

    fun addTags(tags: Map<String, String>) {
        DemoLog.d("Adding tags: $tags")
        OneSignal.User.addTags(tags)
    }

    fun removeTag(key: String) {
        DemoLog.d("Removing tag: $key")
        OneSignal.User.removeTag(key)
    }

    fun removeTags(keys: Collection<String>) {
        DemoLog.d("Removing tags: $keys")
        if (keys.isNotEmpty()) {
            OneSignal.User.removeTags(keys)
        }
    }

    fun getTags(): Map<String, String> {
        return OneSignal.User.getTags()
    }

    // Trigger operations
    fun addTrigger(key: String, value: String) {
        DemoLog.d("Adding trigger: $key -> $value")
        OneSignal.InAppMessages.addTrigger(key, value)
    }

    fun addTriggers(triggers: Map<String, String>) {
        DemoLog.d("Adding triggers: $triggers")
        OneSignal.InAppMessages.addTriggers(triggers)
    }

    fun removeTrigger(key: String) {
        DemoLog.d("Removing trigger: $key")
        OneSignal.InAppMessages.removeTrigger(key)
    }

    fun clearTriggers(keys: Collection<String>) {
        DemoLog.d("Clearing triggers: $keys")
        if (keys.isNotEmpty()) {
            OneSignal.InAppMessages.removeTriggers(keys)
        }
    }

    // Outcome operations
    fun sendOutcome(name: String) {
        DemoLog.d("Sending outcome: $name")
        OneSignal.Session.addOutcome(name)
    }

    fun sendUniqueOutcome(name: String) {
        DemoLog.d("Sending unique outcome: $name")
        OneSignal.Session.addUniqueOutcome(name)
    }

    fun sendOutcomeWithValue(name: String, value: Float) {
        DemoLog.d("Sending outcome with value: $name -> $value")
        OneSignal.Session.addOutcomeWithValue(name, value)
    }

    // Track Event
    fun trackEvent(name: String, properties: Map<String, Any?>?) {
        DemoLog.d("Tracking event: $name with properties: $properties")
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
        DemoLog.d("Setting push enabled: $enabled")
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
        DemoLog.d("Setting in-app messages paused: $paused")
        OneSignal.InAppMessages.paused = paused
    }

    // Location
    fun isLocationShared(): Boolean {
        return OneSignal.Location.isShared
    }

    fun setLocationShared(shared: Boolean) {
        DemoLog.d("Setting location shared: $shared")
        OneSignal.Location.isShared = shared
    }

    suspend fun promptLocation() = withContext(Dispatchers.IO) {
        DemoLog.d("Prompting for location permission")
        OneSignal.Location.requestPermission()
    }

    // Notifications
    suspend fun promptPushPermission() = withContext(Dispatchers.IO) {
        DemoLog.d("Prompting for push permission")
        OneSignal.Notifications.requestPermission(true)
    }

    fun hasNotificationPermission(): Boolean {
        return OneSignal.Notifications.permission
    }

    // Send notifications
    suspend fun sendNotification(type: NotificationType): Boolean {
        DemoLog.d("Sending notification: ${type.title}")
        return OneSignalService.sendNotification(type)
    }

    suspend fun sendCustomNotification(title: String, body: String): Boolean {
        DemoLog.d("Sending custom notification: $title")
        return OneSignalService.sendCustomNotification(title, body)
    }

    // Privacy consent
    fun setConsentRequired(required: Boolean) {
        DemoLog.d("Setting consent required: $required")
        OneSignal.consentRequired = required
    }

    fun getConsentRequired(): Boolean {
        return OneSignal.consentRequired
    }

    fun setPrivacyConsent(granted: Boolean) {
        DemoLog.d("Setting privacy consent: $granted")
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
    suspend fun fetchUser(aliasLabel: String, aliasValue: String, jwt: String? = null): UserData? = withContext(Dispatchers.IO) {
        DemoLog.d("Fetching user data by $aliasLabel: $aliasValue")
        OneSignalService.fetchUser(aliasLabel, aliasValue, jwt)
    }
}
