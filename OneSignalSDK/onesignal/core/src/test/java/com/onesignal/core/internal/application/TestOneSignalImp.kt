package com.onesignal.core.internal.application

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.debug.IDebugManager
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.mockk.mockk
import io.mockk.every

/**
 * Test-only implementation of IOneSignal for testing suspend API behavior
 * with realistic state management and proper mock behavior for verification.
 * 
 * @param ioDispatcher The coroutine dispatcher to use for suspend operations (defaults to Dispatchers.IO)
 */
class TestOneSignalImp(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IOneSignal {
    
    // Realistic test state that changes with operations
    private var initialized = false
    private var currentExternalId: String = ""
    private var initializationCount = 0
    private var loginCount = 0
    private var logoutCount = 0
    
    // Mock managers with configurable behavior
    private val mockUserManager = mockk<IUserManager>(relaxed = true).apply {
        every { externalId } answers { currentExternalId }
    }
    private val mockSessionManager = mockk<ISessionManager>(relaxed = true)
    private val mockNotificationsManager = mockk<INotificationsManager>(relaxed = true)
    private val mockInAppMessagesManager = mockk<IInAppMessagesManager>(relaxed = true)
    private val mockLocationManager = mockk<ILocationManager>(relaxed = true)
    private val mockDebugManager = mockk<IDebugManager>(relaxed = true)
    
    override val sdkVersion: String = "5.0.0-test"
    override val isInitialized: Boolean get() = initialized
    
    // Test accessors for verification
    fun getInitializationCount() = initializationCount
    fun getLoginCount() = loginCount
    fun getLogoutCount() = logoutCount
    fun getCurrentExternalId() = currentExternalId
    
    // Deprecated properties - throw exceptions to encourage suspend usage
    override val user: IUserManager
        get() = throw IllegalStateException("Use suspend getUser() instead")
    override val session: ISessionManager
        get() = throw IllegalStateException("Use suspend getSession() instead")
    override val notifications: INotificationsManager
        get() = throw IllegalStateException("Use suspend getNotifications() instead")
    override val location: ILocationManager
        get() = throw IllegalStateException("Use suspend getLocation() instead")
    override val inAppMessages: IInAppMessagesManager
        get() = throw IllegalStateException("Use suspend getInAppMessages() instead")
    override val debug: IDebugManager = mockDebugManager
    
    override var consentRequired: Boolean = false
    override var consentGiven: Boolean = false
    override var disableGMSMissingPrompt: Boolean = false
    
    // Deprecated blocking methods
    override fun initWithContext(context: Context, appId: String): Boolean {
        initializationCount++
        initialized = true
        return true
    }
    
    override fun login(externalId: String, jwtBearerToken: String?) {
        loginCount++
        currentExternalId = externalId
    }
    
    override fun login(externalId: String) {
        login(externalId, null)
    }
    
    override fun logout() {
        logoutCount++
        currentExternalId = ""
    }
    
    // Suspend methods - these are what we want to test
    override suspend fun initWithContext(context: Context): Boolean = withContext(ioDispatcher) {
        initializationCount++
        initialized = true
        true
    }
    
    override suspend fun initWithContext(context: Context, appId: String?): Boolean = withContext(ioDispatcher) {
        initializationCount++
        initialized = true
        true
    }
    
    override suspend fun getSession(): ISessionManager = withContext(ioDispatcher) {
        if (!initialized) throw IllegalStateException("Not initialized")
        mockSessionManager
    }
    
    override suspend fun getNotifications(): INotificationsManager = withContext(ioDispatcher) {
        if (!initialized) throw IllegalStateException("Not initialized")
        mockNotificationsManager
    }
    
    override suspend fun getLocation(): ILocationManager = withContext(ioDispatcher) {
        if (!initialized) throw IllegalStateException("Not initialized")
        mockLocationManager
    }
    
    override suspend fun getInAppMessages(): IInAppMessagesManager = withContext(ioDispatcher) {
        if (!initialized) throw IllegalStateException("Not initialized")
        mockInAppMessagesManager
    }
    
    override suspend fun getUser(): IUserManager = withContext(ioDispatcher) {
        if (!initialized) throw IllegalStateException("Not initialized")
        mockUserManager
    }
    
    override suspend fun getConsentRequired(): Boolean = withContext(ioDispatcher) {
        consentRequired
    }
    
    override suspend fun setConsentRequired(required: Boolean) = withContext(ioDispatcher) {
        consentRequired = required
    }
    
    override suspend fun getConsentGiven(): Boolean = withContext(ioDispatcher) {
        consentGiven
    }
    
    override suspend fun setConsentGiven(value: Boolean) = withContext(ioDispatcher) {
        consentGiven = value
    }
    
    override suspend fun getDisableGMSMissingPrompt(): Boolean = withContext(ioDispatcher) {
        disableGMSMissingPrompt
    }
    
    override suspend fun setDisableGMSMissingPrompt(value: Boolean) = withContext(ioDispatcher) {
        disableGMSMissingPrompt = value
    }
    
    override suspend fun login(context: Context, externalId: String, jwtBearerToken: String?): Unit = withContext(ioDispatcher) {
        // Auto-initialize if needed
        if (!initialized) {
            initWithContext(context, null)
        }
        loginCount++
        currentExternalId = externalId
    }
    
    override suspend fun logout(context: Context): Unit = withContext(ioDispatcher) {
        // Auto-initialize if needed
        if (!initialized) {
            initWithContext(context, null)
        }
        logoutCount++
        currentExternalId = ""
    }
}