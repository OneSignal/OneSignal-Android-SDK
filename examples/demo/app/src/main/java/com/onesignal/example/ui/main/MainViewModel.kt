package com.onesignal.example.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.OneSignal
import com.onesignal.UserJwtInvalidatedEvent
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.example.data.model.NotificationType
import com.onesignal.example.data.repository.OneSignalRepository
import com.onesignal.example.util.SharedPreferenceUtil
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application), IPushSubscriptionObserver, IPermissionObserver, IUserStateObserver, IUserJwtInvalidatedListener {

    private companion object {
        const val TAG = "MainViewModel"
    }

    private val repository = OneSignalRepository()

    // App ID
    private val _appId = MutableLiveData<String>()
    val appId: LiveData<String> = _appId

    // Push Subscription
    private val _pushSubscriptionId = MutableLiveData<String?>()
    val pushSubscriptionId: LiveData<String?> = _pushSubscriptionId

    private val _pushEnabled = MutableLiveData<Boolean>()
    val pushEnabled: LiveData<Boolean> = _pushEnabled

    // Notification Permission
    private val _hasNotificationPermission = MutableLiveData<Boolean>()
    val hasNotificationPermission: LiveData<Boolean> = _hasNotificationPermission

    // Consent Required
    private val _consentRequired = MutableLiveData<Boolean>()
    val consentRequired: LiveData<Boolean> = _consentRequired

    // Privacy Consent
    private val _privacyConsentGiven = MutableLiveData<Boolean>()
    val privacyConsentGiven: LiveData<Boolean> = _privacyConsentGiven

    // Aliases
    private val _aliases = MutableLiveData<List<Pair<String, String>>>()
    val aliases: LiveData<List<Pair<String, String>>> = _aliases

    // Emails
    private val _emails = MutableLiveData<List<String>>()
    val emails: LiveData<List<String>> = _emails

    // SMS numbers
    private val _smsNumbers = MutableLiveData<List<String>>()
    val smsNumbers: LiveData<List<String>> = _smsNumbers

    // Tags
    private val _tags = MutableLiveData<List<Pair<String, String>>>()
    val tags: LiveData<List<Pair<String, String>>> = _tags

    // Triggers
    private val _triggers = MutableLiveData<List<Pair<String, String>>>()
    val triggers: LiveData<List<Pair<String, String>>> = _triggers

    // In-App Messages Paused
    private val _inAppMessagesPaused = MutableLiveData<Boolean>()
    val inAppMessagesPaused: LiveData<Boolean> = _inAppMessagesPaused

    // Location Shared
    private val _locationShared = MutableLiveData<Boolean>()
    val locationShared: LiveData<Boolean> = _locationShared

    // Identity Verification toggle (demo app only, controls alias used for API calls)
    private val _useIdentityVerification = MutableLiveData<Boolean>()
    val useIdentityVerification: LiveData<Boolean> = _useIdentityVerification

    // Toast messages
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // External User ID (for login state display)
    private val _externalUserId = MutableLiveData<String?>()
    val externalUserId: LiveData<String?> = _externalUserId

    // Local lists to track added items
    private val aliasesList = mutableListOf<Pair<String, String>>()
    private val emailsList = mutableListOf<String>()
    private val smsNumbersList = mutableListOf<String>()
    private val tagsList = mutableListOf<Pair<String, String>>()
    private val triggersList = mutableListOf<Pair<String, String>>()

    // Monotonic counter for fetchUserDataFromApi. Mirrors the Capacitor demo's
    // `requestSequenceRef` so stale fetches after rapid login/logout cycles
    // don't overwrite fresh state. Touched only on the main thread.
    private var fetchRequestSequence = 0L

    init {
        Log.i(TAG, "App initialized")
        loadInitialState()
        OneSignal.User.pushSubscription.addObserver(this)
        OneSignal.Notifications.addPermissionObserver(this)
        OneSignal.User.addObserver(this)
        OneSignal.addUserJwtInvalidatedListener(this)
        Log.d(TAG, "init: observers registered, current onesignalId=${OneSignal.User.onesignalId}")
        Log.d(TAG, "OneSignal ID: ${OneSignal.User.onesignalId ?: "not set"}")
    }

    // IPermissionObserver
    override fun onNotificationPermissionChange(permission: Boolean) {
        _hasNotificationPermission.postValue(permission)
    }

    // IUserStateObserver - called when user changes (login/logout)
    override fun onUserStateChange(state: UserChangedState) {
        Log.d(TAG, "onUserStateChange fired: ${state.current.onesignalId}")
        viewModelScope.launch(Dispatchers.Main) {
            loadExistingAliases()
            loadExistingTags()
            refreshPushSubscription()
            fetchUserDataFromApi()
        }
    }

    private fun loadInitialState() {
        val context = getApplication<Application>()
        
        _appId.value = com.onesignal.example.BuildConfig.ONESIGNAL_APP_ID
        _consentRequired.value = repository.getConsentRequired()
        _privacyConsentGiven.value = repository.getPrivacyConsent()
        _inAppMessagesPaused.value = repository.isInAppMessagesPaused()
        _locationShared.value = repository.isLocationShared()
        _useIdentityVerification.value = SharedPreferenceUtil.getCachedIdentityVerification(context)
        
        val externalId = OneSignal.User.externalId
        _externalUserId.value = if (externalId.isEmpty()) null else externalId
        
        refreshPushSubscription()
        loadExistingAliases()
        loadExistingTags()
        refreshEmails()
        refreshSmsNumbers()
        refreshTriggers()
        
        val onesignalId = OneSignal.User.onesignalId
        if (!onesignalId.isNullOrEmpty()) {
            fetchUserDataFromApi()
        }
    }
    
    fun fetchUserDataFromApi() {
        val useIV = _useIdentityVerification.value == true
        val aliasLabel: String
        val aliasValue: String

        if (useIV) {
            val externalId = _externalUserId.value
            if (externalId.isNullOrEmpty()) {
                _isLoading.value = false
                return
            }
            aliasLabel = "external_id"
            aliasValue = externalId
        } else {
            val onesignalId = OneSignal.User.onesignalId
            if (onesignalId.isNullOrEmpty()) {
                _isLoading.value = false
                return
            }
            aliasLabel = "onesignal_id"
            aliasValue = onesignalId
        }

        val jwt = if (useIV) SharedPreferenceUtil.getCachedJwtToken(getApplication()) else null

        val requestId = ++fetchRequestSequence
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userData = repository.fetchUser(aliasLabel, aliasValue, jwt)
                withContext(Dispatchers.Main) {
                    if (requestId != fetchRequestSequence) {
                        // A newer fetch superseded this one; drop the stale result.
                        return@withContext
                    }
                    if (userData != null) {
                        aliasesList.clear()
                        aliasesList.addAll(userData.aliases.map { Pair(it.key, it.value) })
                        refreshAliases()

                        tagsList.clear()
                        tagsList.addAll(userData.tags.map { Pair(it.key, it.value) })
                        refreshTags()

                        emailsList.clear()
                        emailsList.addAll(userData.emails)
                        refreshEmails()

                        smsNumbersList.clear()
                        smsNumbersList.addAll(userData.smsNumbers)
                        refreshSmsNumbers()

                        if (!userData.externalId.isNullOrEmpty()) {
                            _externalUserId.value = userData.externalId
                            SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), userData.externalId)
                        }

                        kotlinx.coroutines.delay(100)
                        if (requestId != fetchRequestSequence) return@withContext
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error fetching user data", e)
                withContext(Dispatchers.Main) {
                    if (requestId != fetchRequestSequence) return@withContext
                    logError("Failed to fetch user data: ${e.message}")
                    _isLoading.value = false
                }
            }
        }
    }

    private fun loadExistingAliases() {
        aliasesList.clear()
        refreshAliases()
    }

    private fun loadExistingTags() {
        val existingTags = repository.getTags()
        tagsList.clear()
        tagsList.addAll(existingTags.map { Pair(it.key, it.value) })
        refreshTags()
    }

    fun refreshPushSubscription() {
        _pushSubscriptionId.value = repository.getPushSubscriptionId()
        _pushEnabled.value = repository.isPushEnabled()
        _hasNotificationPermission.value = repository.hasNotificationPermission()
    }

    private fun refreshAliases() { _aliases.value = aliasesList.toList() }
    private fun refreshEmails() { _emails.value = emailsList.toList() }
    private fun refreshSmsNumbers() { _smsNumbers.value = smsNumbersList.toList() }
    private fun refreshTags() { _tags.value = tagsList.toList() }
    private fun refreshTriggers() { _triggers.value = triggersList.toList() }

    // User operations
    fun loginUser(externalUserId: String, jwtToken: String? = null) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            repository.loginUser(externalUserId, jwtToken)
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), externalUserId)
                SharedPreferenceUtil.cacheJwtToken(getApplication(), jwtToken)
                _externalUserId.value = externalUserId
                showToast("Logged in as: $externalUserId")
                aliasesList.clear()
                emailsList.clear()
                smsNumbersList.clear()
                triggersList.clear()
                refreshAliases()
                refreshEmails()
                refreshSmsNumbers()
                refreshTriggers()
                loadExistingTags()
                refreshPushSubscription()
                _isLoading.value = false
            }
        }
    }

    fun updateUserJwt(externalUserId: String, jwtToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateUserJwt(externalUserId, jwtToken)
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheJwtToken(getApplication(), jwtToken)
                Log.i(TAG, "Updated JWT for: $externalUserId")
            }
        }
    }

    fun logoutUser() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            repository.logoutUser()
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), "")
                _externalUserId.value = null
                showToast("Logged out")
                loadExistingAliases()
                loadExistingTags()
                refreshPushSubscription()
                _isLoading.value = false
                emailsList.clear()
                smsNumbersList.clear()
                triggersList.clear()
                refreshEmails()
                refreshSmsNumbers()
                refreshTriggers()
            }
        }
    }

    fun setUseIdentityVerification(enabled: Boolean) {
        SharedPreferenceUtil.cacheIdentityVerification(getApplication(), enabled)
        _useIdentityVerification.value = enabled
        Log.i(TAG, if (enabled) "Identity verification enabled" else "Identity verification disabled")
    }

    // Consent required
    fun setConsentRequired(required: Boolean) {
        repository.setConsentRequired(required)
        SharedPreferenceUtil.cacheConsentRequired(getApplication(), required)
        _consentRequired.value = required
        Log.i(TAG, if (required) "Consent required enabled" else "Consent required disabled")
    }

    // Privacy consent
    fun setPrivacyConsent(granted: Boolean) {
        repository.setPrivacyConsent(granted)
        SharedPreferenceUtil.cacheUserPrivacyConsent(getApplication(), granted)
        _privacyConsentGiven.value = granted
        Log.i(TAG, if (granted) "Consent granted" else "Consent revoked")
    }

    // Alias operations (single and batch)
    fun addAlias(label: String, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAlias(label, id)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first == label }
                aliasesList.add(Pair(label, id))
                refreshAliases()
                Log.i(TAG, "Alias added: $label")
            }
        }
    }

    fun addAliases(pairs: List<Pair<String, String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = pairs.associate { it.first to it.second }
            repository.addAliases(map)
            withContext(Dispatchers.Main) {
                for ((label, id) in pairs) {
                    aliasesList.removeAll { it.first == label }
                    aliasesList.add(Pair(label, id))
                }
                refreshAliases()
                Log.i(TAG, "${pairs.size} alias(es) added")
            }
        }
    }

    fun removeAlias(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAlias(label)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first == label }
                refreshAliases()
                Log.i(TAG, "Alias removed: $label")
            }
        }
    }

    fun removeSelectedAliases(labels: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAliases(labels)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first in labels }
                refreshAliases()
                Log.i(TAG, "${labels.size} alias(es) removed")
            }
        }
    }

    // Email operations
    fun addEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addEmail(email)
            withContext(Dispatchers.Main) {
                if (!emailsList.contains(email)) {
                    emailsList.add(email)
                    refreshEmails()
                }
                Log.i(TAG, "Email added: $email")
            }
        }
    }

    fun removeEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeEmail(email)
            withContext(Dispatchers.Main) {
                emailsList.remove(email)
                refreshEmails()
                Log.i(TAG, "Email removed: $email")
            }
        }
    }

    // SMS operations
    fun addSms(smsNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSms(smsNumber)
            withContext(Dispatchers.Main) {
                if (!smsNumbersList.contains(smsNumber)) {
                    smsNumbersList.add(smsNumber)
                    refreshSmsNumbers()
                }
                Log.i(TAG, "SMS added: $smsNumber")
            }
        }
    }

    fun removeSms(smsNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSms(smsNumber)
            withContext(Dispatchers.Main) {
                smsNumbersList.remove(smsNumber)
                refreshSmsNumbers()
                Log.i(TAG, "SMS removed: $smsNumber")
            }
        }
    }

    // Tag operations (single and batch)
    fun addTag(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTag(key, value)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                Log.i(TAG, "Tag added: $key")
            }
        }
    }

    fun addTags(pairs: List<Pair<String, String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = pairs.associate { it.first to it.second }
            repository.addTags(map)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                Log.i(TAG, "${pairs.size} tag(s) added")
            }
        }
    }

    fun removeTag(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTag(key)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                Log.i(TAG, "Tag removed: $key")
            }
        }
    }

    fun removeSelectedTags(keys: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTags(keys)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                Log.i(TAG, "${keys.size} tag(s) removed")
            }
        }
    }

    // Trigger operations (single and batch)
    fun addTrigger(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrigger(key, value)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == key }
                triggersList.add(Pair(key, value))
                refreshTriggers()
                Log.i(TAG, "Trigger added: $key")
            }
        }
    }

    fun addTriggers(pairs: List<Pair<String, String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = pairs.associate { it.first to it.second }
            repository.addTriggers(map)
            withContext(Dispatchers.Main) {
                for ((key, value) in pairs) {
                    triggersList.removeAll { it.first == key }
                    triggersList.add(Pair(key, value))
                }
                refreshTriggers()
                Log.i(TAG, "${pairs.size} trigger(s) added")
            }
        }
    }

    fun removeTrigger(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrigger(key)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == key }
                refreshTriggers()
                Log.i(TAG, "Trigger removed: $key")
            }
        }
    }

    fun removeSelectedTriggers(keys: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearTriggers(keys)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first in keys }
                refreshTriggers()
                Log.i(TAG, "${keys.size} trigger(s) removed")
            }
        }
    }

    fun clearTriggers() {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = triggersList.map { it.first }
            repository.clearTriggers(keys)
            withContext(Dispatchers.Main) {
                triggersList.clear()
                refreshTriggers()
                Log.i(TAG, "All triggers cleared")
            }
        }
    }

    // Outcome operations
    fun sendOutcome(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendOutcome(name)
            withContext(Dispatchers.Main) { showToast("Outcome sent: $name") }
        }
    }

    fun sendUniqueOutcome(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendUniqueOutcome(name)
            withContext(Dispatchers.Main) { showToast("Unique outcome sent: $name") }
        }
    }

    fun sendOutcomeWithValue(name: String, value: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendOutcomeWithValue(name, value)
            withContext(Dispatchers.Main) { showToast("Outcome with value sent: $name = $value") }
        }
    }

    // Track Event
    fun trackEvent(name: String, properties: Map<String, Any?>?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.trackEvent(name, properties)
            withContext(Dispatchers.Main) {
                val message = if (!properties.isNullOrEmpty()) {
                    "Event tracked: $name with properties"
                } else {
                    "Event tracked: $name"
                }
                showToast(message)
            }
        }
    }

    // Push subscription
    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPushEnabled(enabled)
            withContext(Dispatchers.Main) {
                _pushEnabled.value = enabled
                Log.i(TAG, if (enabled) "Push enabled" else "Push disabled")
            }
        }
    }

    fun promptPush() {
        viewModelScope.launch(Dispatchers.Main) {
            OneSignal.Notifications.requestPermission(true)
            refreshPushSubscription()
        }
    }

    // In-App Messages
    fun setInAppMessagesPaused(paused: Boolean) {
        repository.setInAppMessagesPaused(paused)
        SharedPreferenceUtil.cacheInAppMessagingPausedStatus(getApplication(), paused)
        _inAppMessagesPaused.value = paused
        Log.i(TAG, if (paused) "In-app messages paused" else "In-app messages resumed")
    }

    // Location
    fun setLocationShared(shared: Boolean) {
        repository.setLocationShared(shared)
        SharedPreferenceUtil.cacheLocationSharedStatus(getApplication(), shared)
        _locationShared.value = shared
        Log.i(TAG, if (shared) "Location sharing enabled" else "Location sharing disabled")
    }

    fun promptLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.promptLocation()
            withContext(Dispatchers.Main) { Log.i(TAG, "Location permission requested") }
        }
    }

    // Send notification
    fun sendNotification(type: NotificationType) {
        logDebug("Sending notification: ${type.title}")
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.sendNotification(type)
            withContext(Dispatchers.Main) {
                if (success) {
                    Log.i(TAG, "Notification sent: ${type.title}")
                } else {
                    logError("Failed to send notification: ${type.title}")
                }
            }
        }
    }

    fun sendCustomNotification(title: String, body: String) {
        logDebug("Sending custom notification: $title")
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.sendCustomNotification(title, body)
            withContext(Dispatchers.Main) {
                if (success) {
                    Log.i(TAG, "Notification sent: $title")
                } else {
                    logError("Failed to send notification: $title")
                }
            }
        }
    }

    fun clearAllNotifications() {
        OneSignal.Notifications.clearAllNotifications()
        Log.i(TAG, "All notifications cleared")
    }

    fun sendInAppMessage(title: String, triggerKey: String, triggerValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrigger(triggerKey, triggerValue)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == triggerKey }
                triggersList.add(Pair(triggerKey, triggerValue))
                refreshTriggers()
                Log.i(TAG, "Sent In-App Message: $title")
            }
        }
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
        Log.i(TAG, message)
    }

    fun clearToast() { _toastMessage.value = null }

    private fun logError(message: String) = Log.e(TAG, message)
    private fun logDebug(message: String) = Log.d(TAG, message)

    override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
        _pushSubscriptionId.postValue(state.current.id)
        _pushEnabled.postValue(state.current.optedIn)
    }

    override fun onUserJwtInvalidated(event: UserJwtInvalidatedEvent) {
        Log.w(TAG, "JWT invalidated for externalId: ${event.externalId}")
        showToast("JWT invalidated for: ${event.externalId}")
    }

    override fun onCleared() {
        super.onCleared()
        OneSignal.User.pushSubscription.removeObserver(this)
        OneSignal.removeUserJwtInvalidatedListener(this)
    }
}
