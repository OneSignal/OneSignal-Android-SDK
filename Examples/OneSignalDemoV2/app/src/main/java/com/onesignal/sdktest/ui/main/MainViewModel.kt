package com.onesignal.sdktest.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.onesignal.OneSignal
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.data.repository.OneSignalRepository
import com.onesignal.sdktest.util.SharedPreferenceUtil
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application), IPushSubscriptionObserver, IPermissionObserver, IUserStateObserver {

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

    init {
        loadInitialState()
        OneSignal.User.pushSubscription.addObserver(this)
        OneSignal.Notifications.addPermissionObserver(this)
        OneSignal.User.addObserver(this)
        android.util.Log.d("MainViewModel", "init: observers registered, current onesignalId=${OneSignal.User.onesignalId}")
    }

    // IPermissionObserver
    override fun onNotificationPermissionChange(permission: Boolean) {
        _hasNotificationPermission.postValue(permission)
    }

    // IUserStateObserver - called when user changes (login/logout)
    // Note: This is called on a background thread, so we need to post to main thread
    override fun onUserStateChange(state: UserChangedState) {
        android.util.Log.d("MainViewModel", "onUserStateChange fired: ${state.current.onesignalId}")
        viewModelScope.launch(Dispatchers.Main) {
            // Reload local data when user changes
            loadExistingAliases()
            loadExistingTags()
            refreshPushSubscription()
            // Fetch full user data from API now that we have a real onesignalId
            fetchUserDataFromApi()
        }
    }

    private fun loadInitialState() {
        val context = getApplication<Application>()
        
        _appId.value = SharedPreferenceUtil.getOneSignalAppId(context) ?: ""
        _privacyConsentGiven.value = repository.getPrivacyConsent()
        _inAppMessagesPaused.value = repository.isInAppMessagesPaused()
        _locationShared.value = repository.isLocationShared()
        
        // Load external user ID from SDK
        val externalId = OneSignal.User.externalId
        _externalUserId.value = if (externalId.isEmpty()) null else externalId
        
        refreshPushSubscription()
        
        // Load existing data from OneSignal
        loadExistingAliases()
        loadExistingTags()
        
        refreshEmails()
        refreshSmsNumbers()
        refreshTriggers()
        
        // Fetch user data from API if onesignalId exists
        val onesignalId = OneSignal.User.onesignalId
        if (!onesignalId.isNullOrEmpty()) {
            fetchUserDataFromApi()
        }
    }
    
    /**
     * Fetch user data from OneSignal REST API.
     * This populates aliases, tags, emails, and SMS from the server.
     */
    fun fetchUserDataFromApi() {
        val onesignalId = OneSignal.User.onesignalId
        if (onesignalId.isNullOrEmpty()) {
            android.util.Log.d("MainViewModel", "fetchUserDataFromApi: no onesignalId, hiding loading")
            _isLoading.value = false
            return
        }
        
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("MainViewModel", "fetchUserDataFromApi: calling repository.fetchUser")
                val userData = repository.fetchUser(onesignalId)
                android.util.Log.d("MainViewModel", "fetchUserDataFromApi: repository returned, userData=${userData != null}")
                withContext(Dispatchers.Main) {
                    android.util.Log.d("MainViewModel", "fetchUserDataFromApi: on Main thread")
                    if (userData != null) {
                        // Update aliases from API (filtered, excludes external_id and onesignal_id)
                        aliasesList.clear()
                        aliasesList.addAll(userData.aliases.map { Pair(it.key, it.value) })
                        refreshAliases()
                        
                        // Update tags from API
                        tagsList.clear()
                        tagsList.addAll(userData.tags.map { Pair(it.key, it.value) })
                        refreshTags()
                        
                        // Update emails from API
                        emailsList.clear()
                        emailsList.addAll(userData.emails)
                        refreshEmails()
                        
                        // Update SMS from API
                        smsNumbersList.clear()
                        smsNumbersList.addAll(userData.smsNumbers)
                        refreshSmsNumbers()
                        
                        // Update external user ID if available
                        if (!userData.externalId.isNullOrEmpty()) {
                            _externalUserId.value = userData.externalId
                            SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), userData.externalId)
                        }
                        
                        android.util.Log.d("MainViewModel", "User data loaded from API: aliases=${aliasesList.size}, tags=${tagsList.size}, emails=${emailsList.size}, sms=${smsNumbersList.size}")
                        android.util.Log.d("MainViewModel", "LiveData values - aliases=${_aliases.value?.size}, tags=${_tags.value?.size}, emails=${_emails.value?.size}, sms=${_smsNumbers.value?.size}")
                        
                        // Small delay to let UI render before dismissing loading indicator
                        kotlinx.coroutines.delay(100)
                    } else {
                        android.util.Log.w("MainViewModel", "Failed to fetch user data from API")
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error fetching user data", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun loadExistingAliases() {
        // Note: OneSignal SDK doesn't have a getAliases() method,
        // so aliases are managed locally. On login/logout, we clear the local list
        // since the new user won't have the same aliases.
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

    private fun refreshAliases() {
        _aliases.value = aliasesList.toList()
    }

    private fun refreshEmails() {
        _emails.value = emailsList.toList()
    }

    private fun refreshSmsNumbers() {
        _smsNumbers.value = smsNumbersList.toList()
    }

    private fun refreshTags() {
        _tags.value = tagsList.toList()
    }

    private fun refreshTriggers() {
        _triggers.value = triggersList.toList()
    }

    // User operations
    fun loginUser(externalUserId: String) {
        android.util.Log.d("MainViewModel", "loginUser called with: $externalUserId")
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("MainViewModel", "loginUser: calling repository.loginUser")
            repository.loginUser(externalUserId)
            android.util.Log.d("MainViewModel", "loginUser: repository.loginUser returned")
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), externalUserId)
                _externalUserId.value = externalUserId
                showToast("Logged in as: $externalUserId")
                // Clear old user data immediately
                aliasesList.clear()
                emailsList.clear()
                smsNumbersList.clear()
                triggersList.clear()
                refreshAliases()
                refreshEmails()
                refreshSmsNumbers()
                refreshTriggers()
                // Reload tags and push for new user
                loadExistingTags()
                refreshPushSubscription()
                
                // Fetch new user data from API (loading indicator handled by fetchUserDataFromApi)
                kotlinx.coroutines.delay(1000)
                fetchUserDataFromApi()
            }
        }
    }

    fun logoutUser() {
        android.util.Log.d("MainViewModel", "logoutUser called")
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("MainViewModel", "logoutUser: calling repository.logoutUser")
            repository.logoutUser()
            android.util.Log.d("MainViewModel", "logoutUser: repository.logoutUser returned")
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), "")
                _externalUserId.value = null
                showToast("Logged out")
                // Reload data from SDK (will be empty for new device-scoped user)
                loadExistingAliases()
                loadExistingTags()
                refreshPushSubscription()
                _isLoading.value = false
                // Clear emails/sms/triggers which aren't persisted in SDK
                emailsList.clear()
                smsNumbersList.clear()
                triggersList.clear()
                refreshEmails()
                refreshSmsNumbers()
                refreshTriggers()
            }
        }
    }

    // Privacy consent
    fun setPrivacyConsent(granted: Boolean) {
        repository.setPrivacyConsent(granted)
        SharedPreferenceUtil.cacheUserPrivacyConsent(getApplication(), granted)
        _privacyConsentGiven.value = granted
        showToast(if (granted) "Consent granted" else "Consent revoked")
    }

    fun revokeConsent() {
        setPrivacyConsent(false)
    }

    // Alias operations
    fun addAlias(label: String, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAlias(label, id)
            withContext(Dispatchers.Main) {
                // Add to local list directly (SDK syncs to server asynchronously)
                aliasesList.removeAll { it.first == label }
                aliasesList.add(Pair(label, id))
                refreshAliases()
                showToast("Alias added: $label")
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
                showToast("${pairs.size} alias(es) added")
            }
        }
    }

    fun removeAlias(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAlias(label)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first == label }
                refreshAliases()
                showToast("Alias removed: $label")
            }
        }
    }

    fun removeSelectedAliases(labels: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAliases(labels)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first in labels }
                refreshAliases()
                showToast("${labels.size} alias(es) removed")
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
                showToast("Email added: $email")
            }
        }
    }

    fun removeEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeEmail(email)
            withContext(Dispatchers.Main) {
                emailsList.remove(email)
                refreshEmails()
                showToast("Email removed: $email")
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
                showToast("SMS added: $smsNumber")
            }
        }
    }

    fun removeSms(smsNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSms(smsNumber)
            withContext(Dispatchers.Main) {
                smsNumbersList.remove(smsNumber)
                refreshSmsNumbers()
                showToast("SMS removed: $smsNumber")
            }
        }
    }

    // Tag operations
    fun addTag(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTag(key, value)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                showToast("Tag added: $key")
            }
        }
    }

    fun addTags(pairs: List<Pair<String, String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = pairs.associate { it.first to it.second }
            repository.addTags(map)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                showToast("${pairs.size} tag(s) added")
            }
        }
    }

    fun removeSelectedTags(keys: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTags(keys)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                showToast("${keys.size} tag(s) removed")
            }
        }
    }

    fun removeTag(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTag(key)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                showToast("Tag removed: $key")
            }
        }
    }

    // Trigger operations
    fun addTrigger(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrigger(key, value)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == key }
                triggersList.add(Pair(key, value))
                refreshTriggers()
                showToast("Trigger added: $key")
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
                showToast("${pairs.size} trigger(s) added")
            }
        }
    }

    fun removeSelectedTriggers(keys: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearTriggers(keys)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first in keys }
                refreshTriggers()
                showToast("${keys.size} trigger(s) removed")
            }
        }
    }

    fun removeTrigger(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrigger(key)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == key }
                refreshTriggers()
                showToast("Trigger removed: $key")
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
                showToast("All triggers cleared")
            }
        }
    }

    // Outcome operations
    fun sendOutcome(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendOutcome(name)
            withContext(Dispatchers.Main) {
                showToast("Outcome sent: $name")
            }
        }
    }

    fun sendUniqueOutcome(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendUniqueOutcome(name)
            withContext(Dispatchers.Main) {
                showToast("Unique outcome sent: $name")
            }
        }
    }

    fun sendOutcomeWithValue(name: String, value: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendOutcomeWithValue(name, value)
            withContext(Dispatchers.Main) {
                showToast("Outcome with value sent: $name = $value")
            }
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
                showToast(if (enabled) "Push enabled" else "Push disabled")
            }
        }
    }

    fun promptPush() {
        viewModelScope.launch(Dispatchers.Main) {
            // Request permission on main thread (required for showing system dialog)
            OneSignal.Notifications.requestPermission(true)
            refreshPushSubscription()
        }
    }

    // In-App Messages
    fun setInAppMessagesPaused(paused: Boolean) {
        repository.setInAppMessagesPaused(paused)
        SharedPreferenceUtil.cacheInAppMessagingPausedStatus(getApplication(), paused)
        _inAppMessagesPaused.value = paused
        showToast(if (paused) "In-app messages paused" else "In-app messages resumed")
    }

    // Location
    fun setLocationShared(shared: Boolean) {
        repository.setLocationShared(shared)
        SharedPreferenceUtil.cacheLocationSharedStatus(getApplication(), shared)
        _locationShared.value = shared
        showToast(if (shared) "Location sharing enabled" else "Location sharing disabled")
    }

    fun promptLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.promptLocation()
            withContext(Dispatchers.Main) {
                showToast("Location permission requested")
            }
        }
    }

    // Send notification
    fun sendNotification(type: NotificationType) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.sendNotification(type)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("Notification sent: ${type.title}")
                } else {
                    showToast("Failed to send notification")
                }
            }
        }
    }

    fun sendCustomNotification(title: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.sendCustomNotification(title, body)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("Notification sent: $title")
                } else {
                    showToast("Failed to send notification")
                }
            }
        }
    }

    // Send In-App Message (via trigger)
    // Note: In-app messages must be configured in the OneSignal dashboard.
    // This only adds a trigger - the message shows only if a matching campaign exists.
    fun sendInAppMessage(triggerKey: String, triggerValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrigger(triggerKey, triggerValue)
            withContext(Dispatchers.Main) {
                showToast("Trigger added: $triggerKey=$triggerValue (IAM must be configured in dashboard)")
            }
        }
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
        _pushSubscriptionId.postValue(state.current.id)
        _pushEnabled.postValue(state.current.optedIn)
    }

    override fun onCleared() {
        super.onCleared()
        OneSignal.User.pushSubscription.removeObserver(this)
    }
}
