package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.modules.IModule
import com.onesignal.common.services.IServiceProvider
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.CoreModule
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceStoreFix
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.debug.IDebugManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.DebugManager
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.session.SessionModule
import com.onesignal.user.IUserManager
import com.onesignal.user.UserModule
import com.onesignal.user.internal.LoginHelper
import com.onesignal.user.internal.LogoutHelper
import com.onesignal.user.internal.UserSwitcher
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.resolveAppId
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class OneSignalImp(
    private val ioDispatcher: CoroutineDispatcher = OneSignalDispatchers.IO,
) : IOneSignal, IServiceProvider {

    private val suspendCompletion = CompletableDeferred<Unit>()

    @Volatile
    private var initState: InitState = InitState.NOT_STARTED

    // Save the exception pointing to the caller that triggered init, not the async worker thread.
    private var initFailureException: Exception? = null

    override val sdkVersion: String = OneSignalUtils.sdkVersion

    override val isInitialized: Boolean
        get() = initState == InitState.SUCCESS

    override var consentRequired: Boolean
        get() =
            if (isInitialized) {
                blockingGet { configModel.consentRequired ?: (_consentRequired == true) }
            } else {
                _consentRequired == true
            }
        set(value) {
            _consentRequired = value
            if (isInitialized) {
                configModel.consentRequired = value
            }
        }

    override var consentGiven: Boolean
        get() =
            if (isInitialized) {
                blockingGet { configModel.consentGiven ?: (_consentGiven == true) }
            } else {
                _consentGiven == true
            }
        set(value) {
            val oldValue = _consentGiven
            _consentGiven = value
            if (isInitialized) {
                configModel.consentGiven = value
                if (oldValue != value && value) {
                    operationRepo.forceExecuteOperations()
                }
            }
        }

    override var disableGMSMissingPrompt: Boolean
        get() =
            if (isInitialized) {
                blockingGet { configModel.disableGMSMissingPrompt ?: (_disableGMSMissingPrompt == true) }
            } else {
                _disableGMSMissingPrompt == true
            }
        set(value) {
            _disableGMSMissingPrompt = value
            if (isInitialized) {
                configModel.disableGMSMissingPrompt = value
            }
        }

    // we hardcode the DebugManager implementation so it can be used prior to calling `initWithContext`
    override val debug: IDebugManager = DebugManager()

    override val session: ISessionManager
        get() =
            waitAndReturn { services.getService() }

    override val notifications: INotificationsManager
        get() =
            waitAndReturn { services.getService() }

    override val location: ILocationManager
        get() =
            waitAndReturn { services.getService() }

    override val inAppMessages: IInAppMessagesManager
        get() =
            waitAndReturn { services.getService() }

    override val user: IUserManager
        get() =
            waitAndReturn { services.getService() }

    // Services required by this class
    // WARNING: OperationRepo depends on OperationModelStore which in-turn depends
    // on ApplicationService.appContext being non-null.
    private val operationRepo: IOperationRepo by lazy { services.getService<IOperationRepo>() }
    private val identityModelStore: IdentityModelStore by lazy { services.getService<IdentityModelStore>() }
    private val propertiesModelStore: PropertiesModelStore by lazy { services.getService<PropertiesModelStore>() }
    private val subscriptionModelStore: SubscriptionModelStore by lazy { services.getService<SubscriptionModelStore>() }
    private val preferencesService: IPreferencesService by lazy { services.getService<IPreferencesService>() }
    private val listOfModules =
        listOf(
            "com.onesignal.notifications.NotificationsModule",
            "com.onesignal.inAppMessages.InAppMessagesModule",
            "com.onesignal.location.LocationModule",
        )
    private val services: ServiceProvider =
        ServiceBuilder().apply {
            val modules = mutableListOf<IModule>()
            modules.add(CoreModule())
            modules.add(SessionModule())
            modules.add(UserModule())
            for (moduleClassName in listOfModules) {
                try {
                    val moduleClass = Class.forName(moduleClassName)
                    val moduleInstance = moduleClass.newInstance() as IModule
                    modules.add(moduleInstance)
                } catch (e: ClassNotFoundException) {
                    e.printStackTrace()
                }
            }
            for (module in modules) {
                module.register(this)
            }
        }.build()

    // get the current config model, if there is one
    private val configModel: ConfigModel by lazy { services.getService<ConfigModelStore>().model }
    private var _consentRequired: Boolean? = null
    private var _consentGiven: Boolean? = null
    private var _disableGMSMissingPrompt: Boolean? = null
    private val initLock: Any = Any()
    private val loginLogoutLock: Any = Any()
    private val userSwitcher by lazy {
        val appContext = services.getService<IApplicationService>().appContext
        UserSwitcher(
            identityModelStore = identityModelStore,
            propertiesModelStore = propertiesModelStore,
            subscriptionModelStore = subscriptionModelStore,
            configModel = configModel,
            carrierName = DeviceUtils.getCarrierName(appContext),
            deviceOS = android.os.Build.VERSION.RELEASE,
            appContextProvider = { appContext },
            preferencesService = preferencesService,
            operationRepo = operationRepo,
            services = services,
        )
    }

    private val loginHelper by lazy {
        LoginHelper(
            identityModelStore = identityModelStore,
            userSwitcher = userSwitcher,
            operationRepo = operationRepo,
            configModel = configModel,
            lock = loginLogoutLock,
        )
    }

    private val logoutHelper by lazy {
        LogoutHelper(
            identityModelStore = identityModelStore,
            userSwitcher = userSwitcher,
            operationRepo = operationRepo,
            configModel = configModel,
            lock = loginLogoutLock,
        )
    }

    private fun initEssentials(context: Context) {
        PreferenceStoreFix.ensureNoObfuscatedPrefStore(context)

        // start the application service. This is called explicitly first because we want
        // to make sure it has the context provided on input, for all other startable services
        // to depend on if needed.
        val applicationService = services.getService<IApplicationService>()
        (applicationService as ApplicationService).start(context)

        // Give the logging singleton access to the application service to support visual logging.
        Logging.applicationService = applicationService
    }

    private fun updateConfig() {
        // if requires privacy consent was set prior to init, set it in the model now
        if (_consentRequired != null) {
            configModel.consentRequired = _consentRequired!!
        }

        // if privacy consent was set prior to init, set it in the model now
        if (_consentGiven != null) {
            configModel.consentGiven = _consentGiven!!
        }

        if (_disableGMSMissingPrompt != null) {
            configModel.disableGMSMissingPrompt = _disableGMSMissingPrompt!!
        }
    }

    private fun bootstrapServices(): StartupService {
        val startupService = StartupService(services)
        // bootstrap all services
        startupService.bootstrap()
        return startupService
    }

    override fun initWithContext(
        context: Context,
        appId: String,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "Calling deprecated initWithContextSuspend(context: $context, appId: $appId)")

        // do not do this again if already initialized or init is in progress
        synchronized(initLock) {
            if (initState.isSDKAccessible()) {
                Logging.log(LogLevel.DEBUG, "initWithContext: SDK already initialized or in progress")
                return true
            }

            initState = InitState.IN_PROGRESS
        }

        // init in background and return immediately to ensure non-blocking
        suspendifyOnIO {
            internalInit(context, appId)
        }
        return true
    }

    /**
     * Called from internal classes only. Remain suspend until initialization is fully completed.
     */
    override suspend fun initWithContext(context: Context): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)")
        return initWithContextSuspend(context, null)
    }

    private fun internalInit(
        context: Context,
        appId: String?,
    ): Boolean {
        initEssentials(context)

        val startupService = bootstrapServices()
        val result = resolveAppId(appId, configModel, preferencesService)
        if (result.failed) {
            val message = "No OneSignal appId provided or found in local storage. Please pass a valid appId to initWithContext()."
            val exception = IllegalStateException(message)
            // attach the real crash cause to the init failure exception that will be throw shortly after
            initFailureException?.addSuppressed(exception)
            Logging.warn(message)
            initState = InitState.FAILED
            notifyInitComplete()
            return false
        }
        configModel.appId = result.appId!! // safe because failed is false
        val forceCreateUser = result.forceCreateUser

        updateConfig()
        userSwitcher.initUser(forceCreateUser)
        startupService.scheduleStart()
        initState = InitState.SUCCESS
        notifyInitComplete()
        return true
    }

    override fun login(
        externalId: String,
        jwtBearerToken: String?,
    ) {
        Logging.log(LogLevel.DEBUG, "Calling deprecated login(externalId: $externalId, jwtBearerToken: $jwtBearerToken)")

        waitForInit(operationName = "login")

        suspendifyOnIO { loginHelper.login(externalId, jwtBearerToken) }
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "Calling deprecated logout()")

        waitForInit(operationName = "logout")

        suspendifyOnIO { logoutHelper.logout() }
    }

    override fun <T> hasService(c: Class<T>): Boolean = services.hasService(c)

    override fun <T> getService(c: Class<T>): T = services.getService(c)

    override fun <T> getServiceOrNull(c: Class<T>): T? = services.getServiceOrNull(c)

    override fun <T> getAllServices(c: Class<T>): List<T> = services.getAllServices(c)

    /**
     * Blocking version that waits for initialization to complete.
     * Uses runBlocking to bridge to the suspend implementation.
     * Waits indefinitely until init completes and logs how long it took.
     *
     * @param operationName Optional operation name to include in error messages (e.g., "login", "logout")
     */
    private fun waitForInit(operationName: String? = null) {
        runBlocking(ioDispatcher) {
            waitUntilInitInternal(operationName)
        }
    }

    /**
     * Notifies both blocking and suspend callers that initialization is complete
     */
    private fun notifyInitComplete() {
        suspendCompletion.complete(Unit)
    }

    /**
     * Suspend version that waits for initialization to complete.
     * Waits indefinitely until init completes and logs how long it took.
     *
     * @param operationName Optional operation name to include in error messages (e.g., "login", "logout")
     */
    private suspend fun suspendUntilInit(operationName: String? = null) {
        waitUntilInitInternal(operationName)
    }

    /**
     * Common implementation for waiting until initialization completes.
     * Waits indefinitely until init completes (SUCCESS or FAILED) to ensure consistent state.
     * Logs how long initialization took when it completes.
     *
     * @param operationName Optional operation name to include in error messages (e.g., "login", "logout")
     */
    private suspend fun waitUntilInitInternal(operationName: String? = null) {
        when (initState) {
            InitState.NOT_STARTED -> {
                val message = if (operationName != null) {
                    "Must call 'initWithContext' before '$operationName'"
                } else {
                    "Must call 'initWithContext' before use"
                }
                throw IllegalStateException(message)
            }
            InitState.IN_PROGRESS -> {
                Logging.debug("Waiting for init to complete...")

                val startTime = System.currentTimeMillis()

                // Wait indefinitely until init actually completes - ensures consistent state
                // Function only returns when initState is SUCCESS or FAILED
                // NOTE: This is a suspend function, so it's non-blocking when called from coroutines.
                // However, if waitForInit() (which uses runBlocking) is called from the main thread,
                // it will block the main thread indefinitely until init completes, which can cause ANRs.
                // This is intentional per PR #2412: "ANR is the lesser of two evils and the app can recover,
                // where an uncaught throw it can not." To avoid ANRs, call SDK methods from background threads
                // or use the suspend API from coroutines.
                suspendCompletion.await()

                // Log how long initialization took
                val elapsed = System.currentTimeMillis() - startTime
                val message = if (operationName != null) {
                    "OneSignalImp initialization completed before '$operationName' (took ${elapsed}ms)"
                } else {
                    "OneSignalImp initialization completed (took ${elapsed}ms)"
                }
                Logging.debug(message)

                // Re-check state after waiting - init might have failed during the wait
                if (initState == InitState.FAILED) {
                    throw initFailureException ?: IllegalStateException("Initialization failed. Cannot proceed.")
                }
                // initState is guaranteed to be SUCCESS here - consistent state
            }
            InitState.FAILED -> {
                throw initFailureException ?: IllegalStateException("Initialization failed. Cannot proceed.")
            }
            else -> {
                // SUCCESS - already initialized, no need to wait
            }
        }
    }

    private suspend fun <T> suspendAndReturn(getter: () -> T): T {
        suspendUntilInit()
        return getter()
    }

    private fun <T> waitAndReturn(getter: () -> T): T {
        waitForInit()
        return getter()
    }

    private fun <T> blockingGet(getter: () -> T): T {
        try {
            if (AndroidUtils.isRunningOnMainThread()) {
                Logging.warn("This is called on main thread. This is not recommended.")
            }
        } catch (e: RuntimeException) {
            // In test environments, AndroidUtils.isRunningOnMainThread() may fail
            // because Looper.getMainLooper() is not mocked. This is safe to ignore.
            Logging.debug("Could not check main thread status (likely in test environment): ${e.message}")
        }
        // Call suspendAndReturn directly to avoid nested runBlocking (waitAndReturn -> waitForInit -> runBlocking)
        return runBlocking(ioDispatcher) {
            suspendAndReturn(getter)
        }
    }

    // ===============================
    // Suspend API Implementation
    // ===============================

    override suspend fun getSession(): ISessionManager =
        withContext(ioDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getNotifications(): INotificationsManager =
        withContext(ioDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getLocation(): ILocationManager =
        withContext(ioDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getInAppMessages(): IInAppMessagesManager =
        withContext(ioDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getUser(): IUserManager =
        withContext(ioDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getConsentRequired(): Boolean =
        withContext(ioDispatcher) {
            configModel.consentRequired ?: (_consentRequired == true)
        }

    override suspend fun setConsentRequired(required: Boolean) =
        withContext(ioDispatcher) {
            _consentRequired = required
            configModel.consentRequired = required
        }

    override suspend fun getConsentGiven(): Boolean =
        withContext(ioDispatcher) {
            configModel.consentGiven ?: (_consentGiven == true)
        }

    override suspend fun setConsentGiven(value: Boolean) =
        withContext(ioDispatcher) {
            val oldValue = _consentGiven
            _consentGiven = value
            configModel.consentGiven = value
            if (oldValue != value && value) {
                operationRepo.forceExecuteOperations()
            }
        }

    override suspend fun getDisableGMSMissingPrompt(): Boolean =
        withContext(ioDispatcher) {
            configModel.disableGMSMissingPrompt
        }

    override suspend fun setDisableGMSMissingPrompt(value: Boolean) =
        withContext(ioDispatcher) {
            _disableGMSMissingPrompt = value
            configModel.disableGMSMissingPrompt = value
        }

    override suspend fun initWithContextSuspend(
        context: Context,
        appId: String?,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context, appId: $appId)")

        // This ensures the stack trace points to the caller that triggered init, not the async worker thread.
        initFailureException = IllegalStateException("OneSignal initWithContext failed.")

        // Use IO dispatcher for initialization to prevent ANRs and optimize for I/O operations
        return withContext(ioDispatcher) {
            // do not do this again if already initialized or init is in progress
            synchronized(initLock) {
                if (initState.isSDKAccessible()) {
                    Logging.log(LogLevel.DEBUG, "initWithContext: SDK already initialized or in progress")
                    return@withContext true
                }

                initState = InitState.IN_PROGRESS
            }

            val result = internalInit(context, appId)
            // initState is already set correctly in internalInit, no need to overwrite it
            result
        }
    }

    override suspend fun loginSuspend(
        externalId: String,
        jwtBearerToken: String?,
    ) = withContext(ioDispatcher) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: $jwtBearerToken)")

        suspendUntilInit(operationName = "login")

        if (!isInitialized) {
            throw IllegalStateException("'initWithContext failed' before 'login'")
        }

        loginHelper.login(externalId, jwtBearerToken)
    }

    override suspend fun logoutSuspend() =
        withContext(ioDispatcher) {
            Logging.log(LogLevel.DEBUG, "logoutSuspend()")

            suspendUntilInit(operationName = "logout")

            if (!isInitialized) {
                throw IllegalStateException("'initWithContext failed' before 'logout'")
            }

            logoutHelper.logout()
        }
}
