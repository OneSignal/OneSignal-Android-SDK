package com.onesignal.internal

import android.content.Context
import android.os.Build
import com.onesignal.ErrorCode
import com.onesignal.IOneSignal
import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.LoginData
import com.onesignal.OneSignalError
import com.onesignal.OneSignalResult
import com.onesignal.OneSignalUserProfile
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.isMissing
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
import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.OperationWaitResult
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceStoreFix
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.debug.IDebugManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.DebugManager
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.logger.android.getCrashStoragePath
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.logger.IObservabilityEventRecorder
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.session.SessionModule
import com.onesignal.user.IUserManager
import com.onesignal.user.UserModule
import com.onesignal.user.internal.LoginHelper
import com.onesignal.user.internal.LogoutHelper
import com.onesignal.user.internal.UserSwitcher
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.isUsableAppId
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.resolveAppIdV2
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class OneSignalImpV2 : IOneSignal,
    IServiceProvider {
    internal val gate = InitGate()

    private var observabilityManager: IObservabilityLifecycleManager? = null

    override val sdkVersion: String = OneSignalUtils.sdkVersion

    override val isInitialized: Boolean
        get() = gate.isSuccess

    override var consentRequired: Boolean
        get() =
            if (isInitialized) {
                blockingGet { configModel.consentRequired ?: (_consentRequired == true) }
            } else {
                _consentRequired == true
            }
        set(value) {
            synchronized(configLock) {
                _consentRequired = value
                if (configApplied) configModel.consentRequired = value
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
            val grantedNow =
                synchronized(configLock) {
                    val oldValue = _consentGiven
                    _consentGiven = value
                    if (configApplied) configModel.consentGiven = value
                    configApplied && oldValue != value && value
                }
            if (grantedNow) {
                operationRepo.forceExecuteOperations()
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
            synchronized(configLock) {
                _disableGMSMissingPrompt = value
                if (configApplied) configModel.disableGMSMissingPrompt = value
            }
        }

    // we hardcode the DebugManager implementation so it can be used prior to calling `initWithContext`
    override val debug: IDebugManager = DebugManager()

    private val sessionSource = GatedSource(SdkOperation.SESSION, ISessionManager::class.java)
    private val notificationsSource = GatedSource(SdkOperation.NOTIFICATIONS, INotificationsManager::class.java)
    private val locationSource = GatedSource(SdkOperation.LOCATION, ILocationManager::class.java)
    private val inAppMessagesSource = GatedSource(SdkOperation.IN_APP_MESSAGES, IInAppMessagesManager::class.java)
    private val userSource = GatedSource(SdkOperation.USER, IUserManager::class.java)

    private val deferredSession = DeferredSessionManager(sessionSource)
    private val deferredNotifications = DeferredNotificationsManager(notificationsSource)
    private val deferredLocation = DeferredLocationManager(locationSource)
    private val deferredInAppMessages = DeferredInAppMessagesManager(inAppMessagesSource)
    private val deferredUser = DeferredUserManager(userSource)
    private val deferredJwtListeners = DeferredJwtListeners(GatedSource(SdkOperation.JWT_LISTENERS, JwtTokenStore::class.java))

    override val session: ISessionManager
        get() = sessionSource.peek() ?: deferredSession

    override val notifications: INotificationsManager
        get() = notificationsSource.peek() ?: deferredNotifications

    override val location: ILocationManager
        get() = locationSource.peek() ?: deferredLocation

    override val inAppMessages: IInAppMessagesManager
        get() = inAppMessagesSource.peek() ?: deferredInAppMessages

    override val user: IUserManager
        get() = userSource.peek() ?: deferredUser

    private inner class GatedSource<T : Any>(
        private val operation: SdkOperation,
        private val type: Class<T>,
    ) : ManagerSource<T> {
        override fun get(member: String): T? = if (gate.isReady(operation, member)) services.getService(type) else null

        override fun peek(): T? = if (gate.isReady(operation, log = false)) services.getService(type) else null

        override suspend fun await(member: String): T? = if (gate.awaitReady(operation, member)) services.getService(type) else null

        override fun logQueued(member: String) = gate.logQueued(operation, member)

        suspend fun awaitQuietly(): T? = if (gate.awaitReady(operation, log = false)) services.getService(type) else null
    }

    private fun replayDeferredCalls() {
        deferredUser.replay(services.getService())
        deferredNotifications.replay(services.getService())
        deferredInAppMessages.replay(services.getService())
        deferredLocation.replay(services.getService())
        deferredJwtListeners.replay(services.getService())
    }

    // Services required by this class
    // WARNING: OperationRepo depends on OperationModelStore which in-turn depends
    // on ApplicationService.appContext being non-null.
    private val operationRepo: IOperationRepo by lazy { services.getService<IOperationRepo>() }
    private val identityModelStore: IdentityModelStore by lazy { services.getService<IdentityModelStore>() }
    private val propertiesModelStore: PropertiesModelStore by lazy { services.getService<PropertiesModelStore>() }
    private val subscriptionModelStore: SubscriptionModelStore by lazy { services.getService<SubscriptionModelStore>() }
    private val preferencesService: IPreferencesService by lazy { services.getService<IPreferencesService>() }
    private val jwtTokenStore: JwtTokenStore by lazy { services.getService<JwtTokenStore>() }
    private val identityVerificationService: IdentityVerificationService by lazy { services.getService<IdentityVerificationService>() }
    private val listOfModules =
        listOf(
            "com.onesignal.notifications.NotificationsModule",
            "com.onesignal.inAppMessages.InAppMessagesModule",
            "com.onesignal.location.LocationModule",
        )
    private val services: ServiceProvider =
        ServiceBuilder()
            .apply {
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

    // Read per access: an eager val would build the IO pool on main before prewarm() and keep
    // pointing at a dead pool after resetForTest() swaps generations.
    private val ioDispatcher: CoroutineDispatcher get() = OneSignalDispatchers.IO

    // get the current config model, if there is one
    private val configModel: ConfigModel by lazy { services.getService<ConfigModelStore>().model }
    private var _consentRequired: Boolean? = null
    private var _consentGiven: Boolean? = null
    private var _disableGMSMissingPrompt: Boolean? = null

    // Guards the pre-init fields above and [configApplied], so a setter racing updateConfig is never lost.
    private val configLock: Any = Any()
    private var configApplied = false

    // Set once services are bootstrapped; a retry after FAILED must not subscribe listeners twice.
    private var startupService: StartupService? = null
    private val bootstrapper = StartupService(services)
    private val loginLogoutLock: Any = Any()
    private val applicationServiceLock: Any = Any()

    @Volatile
    private var applicationServiceStarted: Boolean = false
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
            jwtTokenStore = jwtTokenStore,
            lock = loginLogoutLock,
            subscriptionModelStore = subscriptionModelStore,
        )
    }

    private val logoutHelper by lazy {
        LogoutHelper(
            identityModelStore = identityModelStore,
            userSwitcher = userSwitcher,
            operationRepo = operationRepo,
            configModel = configModel,
            subscriptionModelStore = subscriptionModelStore,
            identityVerificationService = identityVerificationService,
            lock = loginLogoutLock,
        )
    }

    private fun initEssentials(context: Context) {
        // LoggerLifecycleManager comes up early so crash handling and remote logging can capture
        // anything that happens during the rest of init. FeatureManager is wired in via a
        // lazy supplier — `enabledFeatureFlags` is read per-event, so resolving the manager
        // can be deferred until services have bootstrapped.
        val featureManagerProvider = { services.getService<IFeatureManager>() }
        if (observabilityManager == null) {
            observabilityManager =
                LoggerLifecycleManager(context = context, featureManagerProvider = featureManagerProvider)
                    .also { it.initializeFromCachedConfig() }
        }
        logStartupDiagnostics(context)

        PreferenceStoreFix.ensureNoObfuscatedPrefStore(context)

        ensureApplicationServiceStarted(context)
    }

    /**
     * One concise WARN line at init with the build/runtime facts most useful for
     * release triage from a raw log capture: SDK version, the shared KMP module version,
     * OS/API, device, host app + version, and the crash storage dir.
     * Best-effort — never lets diagnostics interfere with init.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    internal fun logStartupDiagnostics(context: Context) {
        try {
            val appVersion =
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (t: Throwable) {
                    "unknown"
                }
            Logging.warn(
                "OneSignal init: sdkVersion=${OneSignalUtils.sdkVersion} " +
                    "observabilityModule=logger " +
                    "kmpVersion=${com.onesignal.logger.LoggerBuildInfo.KMP_VERSION} " +
                    "os=Android/${Build.VERSION.RELEASE}(API ${Build.VERSION.SDK_INT}) " +
                    "device=${Build.MANUFACTURER}/${Build.MODEL} " +
                    "app=${context.packageName}@$appVersion " +
                    "crashDir=${getCrashStoragePath(context)}",
            )
        } catch (t: Throwable) {
            Logging.warn("OneSignal init: startup diagnostics failed: ${t.message}", t)
        }
    }

    private fun ensureApplicationServiceStarted(context: Context) {
        if (applicationServiceStarted) {
            return
        }

        synchronized(applicationServiceLock) {
            if (applicationServiceStarted) {
                return
            }

            // Start application service before any model store or prefs-backed service access.
            val applicationService = services.getService<IApplicationService>()
            (applicationService as ApplicationService).start(context)
            Logging.applicationService = applicationService
            applicationServiceStarted = true
        }
    }

    // Copies values set before init into the model; setters write through from here on.
    private fun updateConfig() {
        synchronized(configLock) {
            _consentRequired?.let { configModel.consentRequired = it }
            _consentGiven?.let { configModel.consentGiven = it }
            _disableGMSMissingPrompt?.let { configModel.disableGMSMissingPrompt = it }
            configApplied = true
        }
    }

    private fun bootstrapOnce(context: Context): StartupService {
        startupService?.let { return it }

        initEssentials(context)
        val started = bootstrapper.also { it.bootstrap() }
        startupService = started

        // Now that the IoC container is ready, subscribe the observability lifecycle
        // manager to config store events so it reacts to fresh remote config.
        observabilityManager?.subscribeToConfigStore(services.getService<ConfigModelStore>())
        // The event recorder is a container service, so it can only be handed over now. The resolve
        // cannot fail in practice (bootstrap already built the feature manager it needs), and the
        // hand-over is fail-open inside the manager.
        val eventRecorder = services.getServiceOrNull<IObservabilityEventRecorder>()
        if (eventRecorder != null) {
            observabilityManager?.attachEventRecorder(eventRecorder)
        } else {
            Logging.warn("OneSignal: event recorder unavailable, observability events will not ship")
        }
        return started
    }

    /**
     * Fire-and-forget: true once init is dispatched, running or done; false only for a null [context]
     * or a synchronous start failure. Use [initWithContextSuspend] or [isInitialized] for the outcome.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun initWithContext(
        context: Context?,
        appId: String?,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "Calling deprecated initWithContext(context: $context, appId: $appId)")

        if (context == null) {
            Logging.error(MISSING_CONTEXT_MESSAGE)
            return false
        }

        // Warm OneSignalDispatchers on a dedicated daemon thread so the first production caller
        // of suspendifyOnIO / launchOnSerialIO doesn't pay the ThreadPoolExecutor + dispatcher +
        // scope construction cost on the main thread (observed as 5-20s main-thread blocks at the
        // first foreground/background lifecycle event). prewarm() is idempotent and fire-and-forget.
        OneSignalDispatchers.prewarm()

        if (!gate.tryStart()) {
            Logging.log(LogLevel.DEBUG, "initWithContext: SDK already initialized or in progress")
            return true
        }

        // The only step that can throw before dispatch (the Application cast can fail in restricted
        // hosts). It must end in FAILED, or IN_PROGRESS is never retired and every accessor blocks
        // forever. Failures inside [internalInit] are caught there.
        try {
            ensureApplicationServiceStarted(context)
        } catch (e: Exception) {
            gate.addFailureCause(e)
            gate.complete(InitState.FAILED)
            Logging.error("initWithContext failed to start the application service", e)
            return false
        }

        // Dispatch init asynchronously so this method never blocks the caller. Callers that
        // need to wait (accessors, login, logout) block on the gate.
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

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun internalInit(
        context: Context,
        appId: String?,
    ): Boolean {
        try {
            // Check whether current Android user is accessible.
            // Return early if it is inaccessible, as we are unable to complete initialization without access
            // to device storage like SharedPreferences.
            if (!AndroidUtils.isAndroidUserUnlocked(context)) {
                Logging.warn("initWithContext called when device storage is locked, no user data is accessible!")
                gate.complete(InitState.FAILED)
                return false
            }

            val startupService = bootstrapOnce(context)

            val result = resolveAppIdV2(appId, configModel, preferencesService)
            if (result.failed) {
                val message = "initWithContext skipped: no valid appId was passed and none is cached. Pass your OneSignal app ID to initWithContext()."
                gate.addFailureCause(IllegalStateException(message))
                // Null is also how background entry points (e.g. SyncJobService) start init, so it is not an app error.
                if (appId == null) Logging.warn(message) else Logging.error(message)
                gate.complete(InitState.FAILED)
                return false
            }
            if (appId != null && !isUsableAppId(appId)) {
                Logging.warn("initWithContext: appId is empty or contains a null byte, using the cached app ID instead.")
            }
            configModel.appId = result.appId!! // safe because failed is false
            val forceCreateUser = result.forceCreateUser

            updateConfig()
            userSwitcher.initUser(forceCreateUser)
            // Before start, so services never act on a setting the app already changed; before SUCCESS,
            // so no direct call can overtake a queued one. Replayed calls must not go through the gate.
            replayDeferredCalls()
            startupService.scheduleStart()
            gate.complete(InitState.SUCCESS)
            return true
        } catch (e: Exception) {
            // Never rethrow: the sync initWithContext runs this via suspendifyOnIO, with no caller to
            // catch it. Reaching FAILED unblocks every waiter; the cause is attached for the next log.
            Logging.error("OneSignal: internalInit threw unexpectedly; marking init FAILED", e)
            gate.addFailureCause(e)
            if (!gate.isSuccess) gate.complete(InitState.FAILED)
            return false
        }
    }

    override fun login(
        externalId: String,
        jwtBearerToken: String?,
    ) {
        Logging.log(LogLevel.DEBUG, "Calling deprecated login(externalId: $externalId, jwtBearerToken: ...${jwtBearerToken?.takeLast(8)})")

        if (!gate.isReady(SdkOperation.LOGIN) || isMissing(externalId, SdkOperation.LOGIN, EXTERNAL_ID_PARAM)) return

        val context = loginHelper.switchUser(externalId, jwtBearerToken).context ?: return

        suspendifyOnIO { loginHelper.enqueueLogin(context) }
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "Calling deprecated logout()")

        if (!gate.isReady(SdkOperation.LOGOUT)) return

        val context = logoutHelper.switchUser() ?: return

        suspendifyOnIO { logoutHelper.enqueueLogout(context) }
    }

    override fun updateUserJwt(
        externalId: String,
        token: String,
    ) {
        Logging.log(LogLevel.DEBUG, "updateUserJwt(externalId: $externalId, token: ...${token.takeLast(8)})")

        if (!gate.isReady(SdkOperation.UPDATE_USER_JWT)) return

        if (isMissing(externalId, SdkOperation.UPDATE_USER_JWT, EXTERNAL_ID_PARAM) || isMissing(token, SdkOperation.UPDATE_USER_JWT, TOKEN_PARAM)) return

        jwtTokenStore.putJwt(externalId, token)
        // Wake the queue so any deferred ops can dispatch with the fresh token.
        operationRepo.forceExecuteOperations()
    }

    override fun addUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) = deferredJwtListeners.add(listener)

    override fun removeUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) = deferredJwtListeners.remove(listener)

    override fun <T> hasService(c: Class<T>): Boolean = services.hasService(c)

    override fun <T> getService(c: Class<T>): T = services.getService(c)

    override fun <T> getServiceOrNull(c: Class<T>): T? = services.getServiceOrNull(c)

    override fun <T> getAllServices(c: Class<T>): List<T> = services.getAllServices(c)

    // Callers only reach this after checking [isInitialized], and SUCCESS is terminal.
    private fun <T> blockingGet(getter: () -> T): T {
        try {
            if (AndroidUtils.isRunningOnMainThread()) {
                Logging.debug("This is called on main thread. This is not recommended.")
            }
        } catch (e: RuntimeException) {
            // In test environments, AndroidUtils.isRunningOnMainThread() may fail
            // because Looper.getMainLooper() is not mocked. This is safe to ignore.
            Logging.debug("Could not check main thread status (likely in test environment): ${e.message}")
        }
        return getter()
    }

    // ===============================
    // Suspend API Implementation
    // ===============================

    override suspend fun getSession(): ISessionManager = awaitManager(sessionSource) ?: deferredSession

    override suspend fun getNotifications(): INotificationsManager = awaitManager(notificationsSource) ?: deferredNotifications

    override suspend fun getLocation(): ILocationManager = awaitManager(locationSource) ?: deferredLocation

    override suspend fun getInAppMessages(): IInAppMessagesManager = awaitManager(inAppMessagesSource) ?: deferredInAppMessages

    override suspend fun getUser(): IUserManager = awaitManager(userSource) ?: deferredUser

    // Suspends, never blocks, through an in-progress init; the returned deferred manager logs on use.
    private suspend fun <T : Any> awaitManager(source: GatedSource<T>): T? = withContext(ioDispatcher) { source.awaitQuietly() }

    // The properties hold the value locally until init applies it, so these are safe before init.
    override suspend fun getConsentRequired(): Boolean = afterInit(SdkOperation.GET_CONSENT_REQUIRED) { consentRequired }

    override suspend fun setConsentRequired(required: Boolean) = afterInit(SdkOperation.SET_CONSENT_REQUIRED) { consentRequired = required }

    override suspend fun getConsentGiven(): Boolean = afterInit(SdkOperation.GET_CONSENT_GIVEN) { consentGiven }

    override suspend fun setConsentGiven(value: Boolean) = afterInit(SdkOperation.SET_CONSENT_GIVEN) { consentGiven = value }

    override suspend fun getDisableGMSMissingPrompt(): Boolean = afterInit(SdkOperation.GET_DISABLE_GMS_MISSING_PROMPT) { disableGMSMissingPrompt }

    override suspend fun setDisableGMSMissingPrompt(value: Boolean) = afterInit(SdkOperation.SET_DISABLE_GMS_MISSING_PROMPT) { disableGMSMissingPrompt = value }

    // Waits out an in-progress init so a value set now is not lost between updateConfig and SUCCESS.
    private suspend fun <T> afterInit(
        operation: SdkOperation,
        block: () -> T,
    ): T =
        withContext(ioDispatcher) {
            gate.await(operation)
            block()
        }

    override suspend fun initWithContextSuspend(
        context: Context?,
        appId: String?,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context, appId: $appId)")

        if (context == null) {
            Logging.error(MISSING_CONTEXT_MESSAGE)
            return false
        }

        // Same warm-up as the synchronous variant. Reaching this entry point on the main thread
        // (e.g. SyncJobService.onStartJob -> suspendifyOnIO -> initWithContext(context)) pays the
        // cold-init cost on the dispatcher used to enter [withContext] below, so warm
        // OneSignalDispatchers on a background thread before we touch [ioDispatcher].
        OneSignalDispatchers.prewarm()

        // Observing a terminal SUCCESS must never require an IO worker (see InitGate.isReady).
        if (gate.isSuccess) {
            return true
        }

        if (!gate.tryStart()) {
            // Another caller started init. Suspend until it is fully done, not just kicked off, so
            // re-entrant callers (e.g. SyncJobService) never use services whose bootstrap has not run.
            // Awaited on the caller's own context so the wait never queues behind init's own work.
            Logging.log(LogLevel.DEBUG, "initWithContext: init already in progress or completed, awaiting completion")
            return gate.await(SdkOperation.INIT_WITH_CONTEXT)
        }

        // Only the caller that actually runs init needs the IO dispatcher, to keep its disk
        // and network work off the calling thread.
        var started = false
        try {
            return withContext(ioDispatcher) {
                started = true
                internalInit(context, appId)
            }
        } finally {
            // A caller cancelled before dispatch would otherwise leave this attempt IN_PROGRESS forever.
            if (!started) {
                gate.addFailureCause(IllegalStateException("initWithContext was cancelled before init ran"))
                gate.complete(InitState.FAILED)
            }
        }
    }

    override suspend fun loginSuspend(
        externalId: String,
        jwtBearerToken: String?,
    ) = withContext(ioDispatcher) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: ...${jwtBearerToken?.takeLast(8)})")

        if (!gate.awaitReady(SdkOperation.LOGIN)) return@withContext

        if (isMissing(externalId, SdkOperation.LOGIN, EXTERNAL_ID_PARAM)) return@withContext

        val context = loginHelper.switchUser(externalId, jwtBearerToken).context ?: return@withContext
        loginHelper.enqueueLogin(context)
    }

    override suspend fun login(
        externalId: String,
        profile: OneSignalUserProfile,
        jwtBearerToken: String?,
    ): OneSignalResult<LoginData> =
        withContext(ioDispatcher) {
            Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, profile: $profile, jwtBearerToken: ...${jwtBearerToken?.takeLast(8)})")

            profile.validationError()?.let {
                return@withContext OneSignalResult.failure(ErrorCode.INVALID_ARGUMENT, it)
            }

            if (!gate.awaitReady(SdkOperation.LOGIN)) {
                return@withContext OneSignalResult.failure(ErrorCode.NOT_INITIALIZED, gate.notReadyMessage("login"))
            }

            if (isMissing(externalId, SdkOperation.LOGIN, EXTERNAL_ID_PARAM)) {
                return@withContext OneSignalResult.failure(
                    ErrorCode.INVALID_ARGUMENT,
                    "login: externalId is required",
                )
            }

            val switched = loginHelper.switchUser(externalId, jwtBearerToken, profile)
            if (switched.context != null) {
                val completed = loginHelper.enqueueLogin(switched.context, profile)
                if (!completed.success) {
                    val error =
                        if (completed.httpStatusCode != null) {
                            OneSignalError.fromBackendResponse(
                                httpStatus = completed.httpStatusCode,
                                body = completed.httpResponse,
                                fallbackMessage = loginFailureMessage(completed),
                            )
                        } else {
                            OneSignalError.of(ErrorCode.UNKNOWN, loginFailureMessage(completed))
                        }
                    return@withContext OneSignalResult.failure(error)
                }
                return@withContext OneSignalResult.success(
                    loginHelper.loginData(externalId, profile, completed, switched.onesignalId),
                )
            }
            OneSignalResult.success(loginHelper.loginData(externalId, profile, fallbackOnesignalId = switched.onesignalId))
        }

    private fun loginFailureMessage(wait: OperationWaitResult): String =
        wait.httpStatusCode?.let { "Login did not complete (HTTP $it)." } ?: "Login did not complete."

    override suspend fun updateUserJwtSuspend(
        externalId: String,
        token: String,
    ) = withContext(ioDispatcher) {
        Logging.log(LogLevel.DEBUG, "updateUserJwtSuspend(externalId: $externalId, token: ...${token.takeLast(8)})")

        if (!gate.awaitReady(SdkOperation.UPDATE_USER_JWT)) return@withContext

        if (isMissing(externalId, SdkOperation.UPDATE_USER_JWT, EXTERNAL_ID_PARAM) || isMissing(token, SdkOperation.UPDATE_USER_JWT, TOKEN_PARAM)) {
            return@withContext
        }

        jwtTokenStore.putJwt(externalId, token)
        operationRepo.forceExecuteOperations()
    }

    override suspend fun logoutSuspend() =
        withContext(ioDispatcher) {
            Logging.log(LogLevel.DEBUG, "logoutSuspend()")

            if (!gate.awaitReady(SdkOperation.LOGOUT)) return@withContext

            val context = logoutHelper.switchUser() ?: return@withContext
            logoutHelper.enqueueLogout(context)
        }

    private companion object {
        const val MISSING_CONTEXT_MESSAGE = "initWithContext skipped: context is required."
        const val EXTERNAL_ID_PARAM = "externalId"
        const val TOKEN_PARAM = "token"
    }
}
