package com.onesignal.example.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.onesignal.OneSignal
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.example.data.model.NotificationExtensionOptions
import com.onesignal.example.data.model.NotificationType
import com.onesignal.example.data.network.UserFetchResult
import com.onesignal.example.data.repository.OneSignalRepository
import com.onesignal.example.util.DemoLog
import com.onesignal.example.util.JwtAskStore
import com.onesignal.example.util.SharedPreferenceUtil
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application), IPushSubscriptionObserver, IPermissionObserver, IUserStateObserver {

    private companion object {
    }

    private val repository = OneSignalRepository()

    // App ID
    private val _appId = MutableLiveData<String>()
    val appId: LiveData<String> = _appId

    // OneSignal ID
    private val _oneSignalId = MutableLiveData<String?>()
    val oneSignalId: LiveData<String?> = _oneSignalId

    // Push Subscription
    private val _pushSubscriptionId = MutableLiveData<String?>()
    val pushSubscriptionId: LiveData<String?> = _pushSubscriptionId

    private val _pushSubscriptionToken = MutableLiveData<String>()
    val pushSubscriptionToken: LiveData<String> = _pushSubscriptionToken

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

    // Notification service extension switches (demo app only, read by DemoNotificationServiceExtension)
    private val _notificationExtensionOptions = MutableLiveData<NotificationExtensionOptions>()
    val notificationExtensionOptions: LiveData<NotificationExtensionOptions> = _notificationExtensionOptions

    // Picks the alias for the demo's own REST fetch. When on, it fetches by external_id with
    // that user's stored JWT as bearer. It does not turn Identity Verification on for the app.
    private val _useIdentityVerification = MutableLiveData<Boolean>()
    val useIdentityVerification: LiveData<Boolean> = _useIdentityVerification

    // External id the SDK is waiting on a JWT for. Drives the banner in UserSection.
    // MainApplication's listener fills it.
    val jwtAskExternalId: LiveData<String?> = JwtAskStore.externalId

    // Outcome of the last demo /users fetch, "OK" or the failure reason.
    private val _userFetchStatus = MutableLiveData<String?>()
    val userFetchStatus: LiveData<String?> = _userFetchStatus

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // External User ID (for login state display)
    private val _externalUserId = MutableLiveData<String?>()
    val externalUserId: LiveData<String?> = _externalUserId

    private val _language = MutableLiveData<String>()
    val language: LiveData<String> = _language

    // Local lists to track added items
    private val aliasesList = mutableListOf<Pair<String, String>>()
    private val emailsList = mutableListOf<String>()
    private val smsNumbersList = mutableListOf<String>()
    private val tagsList = mutableListOf<Pair<String, String>>()
    private val triggersList = mutableListOf<Pair<String, String>>()

    // Monotonic counter for fetchUserDataFromApi. Mirrors the Capacitor demo's
    // `requestSequenceRef` so stale fetches after rapid login/logout cycles
    // don't overwrite fresh state. A user switch or logout bumps it too, through
    // resetUserFetch. Touched only on the main thread.
    private var fetchRequestSequence = 0L

    init {
        DemoLog.i("App initialized")
        loadInitialState()
        OneSignal.User.pushSubscription.addObserver(this)
        OneSignal.Notifications.addPermissionObserver(this)
        OneSignal.User.addObserver(this)
        DemoLog.d("init: observers registered, current onesignalId=${OneSignal.User.onesignalId}")
        DemoLog.d("OneSignal ID: ${OneSignal.User.onesignalId ?: "not set"}")
    }

    // IPermissionObserver
    override fun onNotificationPermissionChange(permission: Boolean) {
        _hasNotificationPermission.postValue(permission)
    }

    // IUserStateObserver - called when user changes (login/logout)
    override fun onUserStateChange(state: UserChangedState) {
        DemoLog.d("onUserStateChange fired: ${state.current.onesignalId}")
        _oneSignalId.postValue(state.current.onesignalId)
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
        _notificationExtensionOptions.value = SharedPreferenceUtil.getNotificationExtensionOptions(context)
        _language.value = SharedPreferenceUtil.getCachedLanguage(context)

        val externalId = OneSignal.User.externalId
        _externalUserId.value = if (externalId.isEmpty()) null else externalId
        
        refreshPushSubscription()
        loadExistingAliases()
        loadExistingTags()
        refreshEmails()
        refreshSmsNumbers()
        refreshTriggers()

        // No onesignal_id guard here. A user whose create is waiting on a JWT has none yet,
        // and with the toggle on the fetch goes by external_id. fetchUserDataFromApi checks
        // for the alias it needs.
        fetchUserDataFromApi()
    }
    
    fun fetchUserDataFromApi() {
        val requestId = ++fetchRequestSequence
        val useIV = _useIdentityVerification.value == true
        val aliasLabel: String
        val aliasValue: String

        if (useIV) {
            val externalId = _externalUserId.value
            if (externalId.isNullOrEmpty()) {
                _userFetchStatus.value = null
                _isLoading.value = false
                return
            }
            aliasLabel = "external_id"
            aliasValue = externalId
        } else {
            val onesignalId = OneSignal.User.onesignalId
            if (onesignalId.isNullOrEmpty()) {
                _userFetchStatus.value = null
                _isLoading.value = false
                return
            }
            aliasLabel = "onesignal_id"
            aliasValue = onesignalId
        }

        val jwt = if (useIV) SharedPreferenceUtil.getCachedJwtToken(getApplication(), aliasValue) else null

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.fetchUser(aliasLabel, aliasValue, jwt)
                withContext(Dispatchers.Main) {
                    if (requestId != fetchRequestSequence) {
                        // A newer fetch superseded this one; drop the stale result.
                        return@withContext
                    }
                    when (result) {
                        is UserFetchResult.Failure -> {
                            _userFetchStatus.value = "$aliasLabel fetch failed: ${result.reason}"
                        }
                        is UserFetchResult.Success -> {
                            val userData = result.userData
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
                            _userFetchStatus.value = "OK"

                            kotlinx.coroutines.delay(100)
                            if (requestId != fetchRequestSequence) return@withContext
                        }
                    }
                    _isLoading.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DemoLog.e("Error fetching user data", e)
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
        _pushSubscriptionToken.value = repository.getPushSubscriptionToken()
        _pushEnabled.value = repository.isPushEnabled()
        _hasNotificationPermission.value = repository.hasNotificationPermission()
        _oneSignalId.value = repository.getOneSignalId()
    }

    private fun refreshAliases() { _aliases.value = aliasesList.toList() }
    private fun refreshEmails() { _emails.value = emailsList.toList() }
    private fun refreshSmsNumbers() { _smsNumbers.value = smsNumbersList.toList() }
    private fun refreshTags() { _tags.value = tagsList.toList() }
    private fun refreshTriggers() { _triggers.value = triggersList.toList() }

    // User operations
    fun loginUser(externalUserId: String, jwtToken: String? = null) {
        val externalId = externalUserId.trim()
        if (externalId.isEmpty()) return
        val token = jwtToken?.trim()?.ifEmpty { null }
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sameUser = externalId == OneSignal.User.externalId
            repository.loginUser(externalId, token)
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), externalId)
                // A blank token field leaves any token already stored for this user.
                if (token != null) {
                    SharedPreferenceUtil.cacheJwtToken(getApplication(), externalId, token)
                    JwtAskStore.answered(externalId)
                }
                _externalUserId.value = externalId
                refreshPushSubscription()
                if (sameUser) {
                    // No user-state event fires for the same user, so refetch here instead of
                    // clearing lists that nothing would repopulate.
                    fetchUserDataFromApi()
                    return@withContext
                }
                resetUserFetch()
                aliasesList.clear()
                emailsList.clear()
                smsNumbersList.clear()
                triggersList.clear()
                refreshAliases()
                refreshEmails()
                refreshSmsNumbers()
                refreshTriggers()
                loadExistingTags()
                _isLoading.value = false
            }
        }
    }

    fun loginUserWithProfile(
        externalUserId: String,
        email: String?,
        sms: String?,
        jwtToken: String? = null,
    ) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.loginUserWithProfile(externalUserId, email, sms, jwtToken)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), externalUserId)
                    SharedPreferenceUtil.cacheJwtToken(getApplication(), jwtToken)
                    _externalUserId.value = externalUserId
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
                } else {
                    DemoLog.e("Composite login failed: ${result.error}")
                }
                _isLoading.value = false
            }
        }
    }

    fun updateUserJwt(externalUserId: String, jwtToken: String) {
        val externalId = externalUserId.trim()
        val token = jwtToken.trim()
        if (externalId.isEmpty() || token.isEmpty()) return
        // Stored whoever it's for. The SDK can hold parked requests for a user who isn't the
        // current one, including one who has logged out.
        SharedPreferenceUtil.cacheJwtToken(getApplication(), externalId, token)
        JwtAskStore.answered(externalId)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateUserJwt(externalId, token)
            withContext(Dispatchers.Main) {
                DemoLog.i("Updated JWT for: $externalId")
                if (externalId == _externalUserId.value) {
                    // updateUserJwt may not fire a user-state event, so refetch instead of
                    // leaving the last failure on screen.
                    fetchUserDataFromApi()
                }
            }
        }
    }

    fun logoutUser() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            repository.logoutUser()
            withContext(Dispatchers.Main) {
                SharedPreferenceUtil.cacheUserExternalUserId(getApplication(), "")
                SharedPreferenceUtil.clearCachedJwtTokens(getApplication())
                _externalUserId.value = null
                JwtAskStore.clear()
                resetUserFetch()
                setLanguage("")
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

    // A fetch still in flight belongs to the previous user, so drop its result. Clear the row
    // too, because a new user's create can wait on a JWT, and then no user-state event comes
    // to refetch.
    private fun resetUserFetch() {
        fetchRequestSequence++
        _userFetchStatus.value = null
    }

    fun setUseIdentityVerification(enabled: Boolean) {
        SharedPreferenceUtil.cacheIdentityVerification(getApplication(), enabled)
        _useIdentityVerification.value = enabled
        DemoLog.i(if (enabled) "Fetch by external_id enabled" else "Fetch by external_id disabled")
        fetchUserDataFromApi()
    }

    fun setLanguage(language: String) {
        repository.setLanguage(language)
        SharedPreferenceUtil.cacheLanguage(getApplication(), language)
        _language.value = language
        DemoLog.i("Language set to: ${language.ifEmpty { "device default" }}")
    }

    // Consent required
    fun setConsentRequired(required: Boolean) {
        repository.setConsentRequired(required)
        SharedPreferenceUtil.cacheConsentRequired(getApplication(), required)
        _consentRequired.value = required
        DemoLog.i(if (required) "Consent required enabled" else "Consent required disabled")
    }

    // Privacy consent
    fun setPrivacyConsent(granted: Boolean) {
        repository.setPrivacyConsent(granted)
        SharedPreferenceUtil.cacheUserPrivacyConsent(getApplication(), granted)
        _privacyConsentGiven.value = granted
        DemoLog.i(if (granted) "Consent granted" else "Consent revoked")
    }

    // Alias operations (single and batch)
    fun addAlias(label: String, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAlias(label, id)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first == label }
                aliasesList.add(Pair(label, id))
                refreshAliases()
                DemoLog.i("Alias added: $label")
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
                DemoLog.i("${pairs.size} alias(es) added")
            }
        }
    }

    fun removeAlias(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAlias(label)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first == label }
                refreshAliases()
                DemoLog.i("Alias removed: $label")
            }
        }
    }

    fun removeSelectedAliases(labels: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAliases(labels)
            withContext(Dispatchers.Main) {
                aliasesList.removeAll { it.first in labels }
                refreshAliases()
                DemoLog.i("${labels.size} alias(es) removed")
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
                DemoLog.i("Email added: $email")
            }
        }
    }

    fun removeEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeEmail(email)
            withContext(Dispatchers.Main) {
                emailsList.remove(email)
                refreshEmails()
                DemoLog.i("Email removed: $email")
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
                DemoLog.i("SMS added: $smsNumber")
            }
        }
    }

    fun removeSms(smsNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSms(smsNumber)
            withContext(Dispatchers.Main) {
                smsNumbersList.remove(smsNumber)
                refreshSmsNumbers()
                DemoLog.i("SMS removed: $smsNumber")
            }
        }
    }

    // Tag operations (single and batch)
    fun addTag(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTag(key, value)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                DemoLog.i("Tag added: $key")
            }
        }
    }

    fun addTags(pairs: List<Pair<String, String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = pairs.associate { it.first to it.second }
            repository.addTags(map)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                DemoLog.i("${pairs.size} tag(s) added")
            }
        }
    }

    fun removeTag(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTag(key)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                DemoLog.i("Tag removed: $key")
            }
        }
    }

    fun removeSelectedTags(keys: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTags(keys)
            withContext(Dispatchers.Main) {
                loadExistingTags()
                DemoLog.i("${keys.size} tag(s) removed")
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
                DemoLog.i("Trigger added: $key")
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
                DemoLog.i("${pairs.size} trigger(s) added")
            }
        }
    }

    fun removeTrigger(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrigger(key)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == key }
                refreshTriggers()
                DemoLog.i("Trigger removed: $key")
            }
        }
    }

    fun removeSelectedTriggers(keys: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearTriggers(keys)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first in keys }
                refreshTriggers()
                DemoLog.i("${keys.size} trigger(s) removed")
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
                DemoLog.i("All triggers cleared")
            }
        }
    }

    // Outcome operations
    fun sendOutcome(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendOutcome(name)
            withContext(Dispatchers.Main) { DemoLog.i("Outcome sent: $name") }
        }
    }

    fun sendUniqueOutcome(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendUniqueOutcome(name)
            withContext(Dispatchers.Main) { DemoLog.i("Unique outcome sent: $name") }
        }
    }

    fun sendOutcomeWithValue(name: String, value: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendOutcomeWithValue(name, value)
            withContext(Dispatchers.Main) { DemoLog.i("Outcome sent: $name = $value") }
        }
    }

    // Track Event
    fun trackEvent(name: String, properties: Map<String, Any?>?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.trackEvent(name, properties)
            withContext(Dispatchers.Main) { DemoLog.i("Event tracked: $name") }
        }
    }

    // Push subscription
    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPushEnabled(enabled)
            withContext(Dispatchers.Main) {
                _pushEnabled.value = enabled
                DemoLog.i(if (enabled) "Push enabled" else "Push disabled")
            }
        }
    }

    fun promptPush() {
        viewModelScope.launch(Dispatchers.Main) {
            OneSignal.Notifications.requestPermission(true)
            refreshPushSubscription()
        }
    }

    // Auto-prompt is fired from a LaunchedEffect that re-runs on every
    // composition (rotation/theme/font-scale). Gate it to once per VM lifetime
    // so config changes don't re-trigger the OS prompt or the SDK's settings
    // prepompt while the manual `prompt_push_button` remains the fallback.
    private var hasAutoPrompted = false
    fun autoPromptPushOnce() {
        if (hasAutoPrompted) return
        hasAutoPrompted = true
        promptPush()
    }

    // In-App Messages
    fun setInAppMessagesPaused(paused: Boolean) {
        repository.setInAppMessagesPaused(paused)
        SharedPreferenceUtil.cacheInAppMessagingPausedStatus(getApplication(), paused)
        _inAppMessagesPaused.value = paused
        DemoLog.i(if (paused) "In-app messages paused" else "In-app messages resumed")
    }

    // Location
    fun setLocationShared(shared: Boolean) {
        repository.setLocationShared(shared)
        SharedPreferenceUtil.cacheLocationSharedStatus(getApplication(), shared)
        _locationShared.value = shared
        DemoLog.i(if (shared) "Location sharing enabled" else "Location sharing disabled")
    }

    fun checkLocationShared(): Boolean {
        val shared = repository.isLocationShared()
        DemoLog.i("Location shared: $shared")
        return shared
    }

    fun promptLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.promptLocation()
            withContext(Dispatchers.Main) { DemoLog.i("Location permission requested") }
        }
    }

    // Send notification
    fun sendNotification(type: NotificationType) {
        logDebug("Sending notification: ${type.title}")
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.sendNotification(type)
            withContext(Dispatchers.Main) {
                if (success) {
                    DemoLog.i("Notification sent: ${type.title}")
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
                    DemoLog.i("Notification sent: $title")
                } else {
                    logError("Failed to send notification: $title")
                }
            }
        }
    }

    fun clearAllNotifications() {
        OneSignal.Notifications.clearAllNotifications()
        DemoLog.i("All notifications cleared")
    }

    // Notification service extension
    fun setNotificationExtensionOptions(options: NotificationExtensionOptions) {
        SharedPreferenceUtil.cacheNotificationExtensionOptions(getApplication(), options)
        _notificationExtensionOptions.value = options
        DemoLog.i("Notification service extension options: $options")
    }

    fun sendInAppMessage(title: String, triggerKey: String, triggerValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrigger(triggerKey, triggerValue)
            withContext(Dispatchers.Main) {
                triggersList.removeAll { it.first == triggerKey }
                triggersList.add(Pair(triggerKey, triggerValue))
                refreshTriggers()
                DemoLog.i("Sent In-App Message: $title")
            }
        }
    }

    private fun logError(message: String) = DemoLog.e(message)
    private fun logDebug(message: String) = DemoLog.d(message)

    override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
        DemoLog.i("onPushSubscriptionChange: ${state.toJSONObject()}")
        _pushSubscriptionId.postValue(state.current.id)
        _pushSubscriptionToken.postValue(state.current.token)
        _pushEnabled.postValue(state.current.optedIn)
    }

    override fun onCleared() {
        super.onCleared()
        OneSignal.User.pushSubscription.removeObserver(this)
        OneSignal.Notifications.removePermissionObserver(this)
        OneSignal.User.removeObserver(this)
    }
}
