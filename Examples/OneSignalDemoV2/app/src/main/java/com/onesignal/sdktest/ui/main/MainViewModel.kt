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
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application), IPushSubscriptionObserver, IPermissionObserver {

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
    }

    // IPermissionObserver
    override fun onNotificationPermissionChange(permission: Boolean) {
        _hasNotificationPermission.postValue(permission)
    }

    private fun loadInitialState() {
        val context = getApplication<Application>()
        
        _appId.value = SharedPreferenceUtil.getOneSignalAppId(context) ?: ""
        _privacyConsentGiven.value = SharedPreferenceUtil.getUserPrivacyConsent(context)
        _inAppMessagesPaused.value = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context)
        _locationShared.value = SharedPreferenceUtil.getCachedLocationSharedStatus(context)
        
        refreshPushSubscription()
        refreshAliases()
        refreshEmails()
        refreshSmsNumbers()
        refreshTags()
        refreshTriggers()
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.loginUser(externalUserId)
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), externalUserId)
                showToast("Logged in as: $externalUserId")
                refreshPushSubscription()
            }
        }
    }

    fun logoutUser() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.logoutUser()
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), "")
                showToast("Logged out")
                refreshPushSubscription()
                // Clear local lists
                aliasesList.clear()
                emailsList.clear()
                smsNumbersList.clear()
                tagsList.clear()
                triggersList.clear()
                refreshAliases()
                refreshEmails()
                refreshSmsNumbers()
                refreshTags()
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
                aliasesList.add(Pair(label, id))
                refreshAliases()
                showToast("Alias added: $label")
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
                tagsList.removeAll { it.first == key }
                tagsList.add(Pair(key, value))
                refreshTags()
                showToast("Tag added: $key")
            }
        }
    }

    fun removeTag(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTag(key)
            withContext(Dispatchers.Main) {
                tagsList.removeAll { it.first == key }
                refreshTags()
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
