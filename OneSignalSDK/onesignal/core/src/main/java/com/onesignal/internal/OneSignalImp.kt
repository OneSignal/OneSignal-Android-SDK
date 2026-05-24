package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.IUserJwtInvalidatedListener
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
import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.features.FeatureFlag
import com.onesignal.core.internal.features.IFeatureManager
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
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.resolveAppId
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class OneSignalImp(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IOneSignal,
    IServiceProvider {
    // Reset every time the synchronized(initLock) block flips state to IN_PROGRESS so that
    // a retry-after-FAILED gets a fresh latch instead of an already-completed one. Mutated only
    // under initLock; reads outside that lock must local-capture before suspending on it.
    @Volatile
    private var suspendCompletion = CompletableDeferred<Unit>()

    @Volatile
    private var initState: InitState = InitState.NOT_STARTED

    // Save the exception pointing to the caller that triggered init, not the async worker thread.
    private var initFailureException: Exception? = null

    private var otelManager: OtelLifecycleManager? = null

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
            getServiceWithFeatureGate { services.getService() }

    override val notifications: INotificationsManager
        get() =
            getServiceWithFeatureGate { services.getService() }

    override val location: ILocationManager
        get() =
            getServiceWithFeatureGate { services.getService() }

    override val inAppMessages: IInAppMessagesManager
        get() =
            getServiceWithFeatureGate { services.getService() }

    override val user: IUserManager
        get() =
            getServiceWithFeatureGate { services.getService() }

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

    private val featureManager: IFeatureManager by lazy { services.getService<IFeatureManager>() }
    private val runtimeIoDispatcher: CoroutineDispatcher
        get() = if (isBackgroundThreadingEnabled) OneSignalDispatchers.IO else ioDispatcher

    @Suppress("TooGenericExceptionCaught")
    private val isBackgroundThreadingEnabled: Boolean
        get() {
            if (!applicationServiceStarted) {
                return false
            }

            return try {
                featureManager.isEnabled(FeatureFlag.SDK_BACKGROUND_THREADING)
            } catch (t: Throwable) {
                Logging.warn("OneSignal: Failed to resolve BACKGROUND_THREADING feature, defaulting to legacy mode.", t)
                false
            }
        }

    // get the current config model, if there is one
    private val configModel: ConfigModel by lazy { services.getService<ConfigModelStore>().model }
    private var _consentRequired: Boolean? = null
    private var _consentGiven: Boolean? = null
    private var _disableGMSMissingPrompt: Boolean? = null
    private val initLock: Any = Any()
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
        // OtelLifecycleManager comes up early so crash handling and remote logging can capture
        // anything that happens during the rest of init. FeatureManager is wired in via a
        // lazy supplier — `enabledFeatureFlags` is read per-event, so resolving the manager
        // can be deferred until services have bootstrapped.
        otelManager =
            OtelLifecycleManager(
                context = context,
                featureManagerProvider = { services.getService<IFeatureManager>() },
            ).also { it.initializeFromCachedConfig() }

        PreferenceStoreFix.ensureNoObfuscatedPrefStore(context)

        ensureApplicationServiceStarted(context)
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

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun initWithContext(
        context: Context,
        appId: String,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "Calling deprecated initWithContext(context: $context, appId: $appId)")

        // Warm OneSignalDispatchers on a dedicated daemon thread so the first production caller
        // of suspendifyOnIO / launchOnSerialIO doesn't pay the ThreadPoolExecutor + dispatcher +
        // scope construction cost on the main thread. See SDK-4507; OTel showed that cost as
        // 5–20s main-thread blocks at first foreground/background lifecycle event under
        // sdk_background_threading. Calling prewarm() is idempotent, fire-and-forget, and safe
        // even if a prior initWithContext attempt already started the prewarm.
        OneSignalDispatchers.prewarm()

        synchronized(initLock) {
            if (initState.isSDKAccessible()) {
                Logging.log(LogLevel.DEBUG, "initWithContext: SDK already initialized or in progress")
                return true
            }

            // Publish state and its associated metadata atomically.
            initFailureException = IllegalStateException("OneSignal initWithContext failed.")
            initState = InitState.IN_PROGRESS
            // Fresh latch for this init attempt -- prevents retry-after-FAILED waiters from
            // observing the prior init's already-completed deferred.
            suspendCompletion = CompletableDeferred()
        }

        // Tight try/catch around the only step that can throw synchronously *before* the
        // dispatch (or runBlocking). ApplicationService.start does
        // `context.applicationContext as Application`, which can ClassCastException in
        // restricted hosts (Robolectric custom application factories, instrumentation context
        // wrappers, multi-process content provider init order). If we don't transition to
        // FAILED here, IN_PROGRESS would never be retired — every subsequent
        // [initWithContext] would short-circuit (IN_PROGRESS counts as "accessible") and every
        // accessor would deadlock on suspendCompletion.await(). Failures *inside*
        // [internalInit] are owned by its own try/catch.
        try {
            ensureApplicationServiceStarted(context)
        } catch (e: Exception) {
            initFailureException?.addSuppressed(e)
            completeInit(InitState.FAILED)
            throw e
        }

        if (isBackgroundThreadingEnabled) {
            // FF-on: dispatch init asynchronously so this method never blocks the caller.
            // Callers that need to wait (accessors, login, logout) will block via suspendCompletion.
            suspendifyOnIO {
                internalInit(context, appId)
            }
            return true
        }

        // FF-off: legacy behavior. Block the caller thread until initialization completes.
        // [internalInit] owns its own terminal-state cleanup on failure (catches, transitions
        // to FAILED via [completeInit], returns `false` with the cause attached to
        // `initFailureException.suppressed`) so the caller gets `false` returned through
        // `runBlocking` and a subsequent accessor surfaces the failure cause via
        // [waitUntilInitInternal]. The synchronous-bootstrap throw above (the only path that
        // can throw out of `initWithContext`) is preserved by the `try { ensureApplicationServiceStarted }`
        // catch — same shape pre-#2605 callers got from runBlocking propagation.
        return runBlocking(runtimeIoDispatcher) {
            internalInit(context, appId)
        }
    }

    /**
     * Called from internal classes only. Remain suspend until initialization is fully completed.
     */
    override suspend fun initWithContext(context: Context): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)")
        return initWithContextSuspend(context, null)
    }

    // ReturnCount: 4 returns vs detekt's limit of 2. The function intentionally has
    // multiple early-return failure paths (user-locked, missing appId, exception). The
    // existing detekt baseline entry was for the unannotated signature; once we added
    // @Suppress(TooGenericExceptionCaught) the baseline ID no longer matched, so we
    // suppress ReturnCount inline rather than churning the baseline.
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
                completeInit(InitState.FAILED)
                return false
            }

            initEssentials(context)

            val startupService = bootstrapServices()

            // Now that the IoC container is ready, subscribe the Otel lifecycle
            // manager to config store events so it reacts to fresh remote config.
            otelManager?.subscribeToConfigStore(services.getService<ConfigModelStore>())

            val result = resolveAppId(appId, configModel, preferencesService)
            if (result.failed) {
                val message = "suspendInitInternal: no appId provided or found in local storage. Please pass a valid appId to initWithContext()."
                val exception = IllegalStateException(message)
                // attach the real crash cause to the init failure exception that will be throw shortly after
                initFailureException?.addSuppressed(exception)
                Logging.warn(message)
                completeInit(InitState.FAILED)
                return false
            }
            configModel.appId = result.appId!! // safe because failed is false
            val forceCreateUser = result.forceCreateUser

            updateConfig()
            userSwitcher.initUser(forceCreateUser)
            startupService.scheduleStart()
            completeInit(InitState.SUCCESS)
            return true
        } catch (e: Exception) {
            // Any unchecked throw from initEssentials / bootstrapServices / subscribeToConfigStore /
            // updateConfig / userSwitcher.initUser / startupService.scheduleStart would otherwise
            // leave initState at IN_PROGRESS forever and `suspendCompletion` uncompleted —
            // accessors and re-entrant suspend callers (e.g. SyncJobService) would deadlock on
            // `await()`. Reach a terminal state via [completeInit] (atomic state+completion) and
            // return `false`. The throw is captured on `initFailureException.addSuppressed(...)`
            // so a subsequent accessor surfaces it to the caller. We deliberately don't rethrow
            // here: the FF-on `suspendifyOnIO { ... }` arm would swallow it anyway, the FF-off
            // `runBlocking { ... }` arm would propagate it, and the [initWithContextSuspend]
            // path would propagate it — three different observable behaviors for the same kind
            // of failure. `return false` gives one consistent shape; callers can query
            // `initFailureException` (or just the returned `Boolean`) to react.
            Logging.error("OneSignal: internalInit threw unexpectedly; marking init FAILED", e)
            initFailureException?.addSuppressed(e)
            completeInit(InitState.FAILED)
            return false
        }
    }

    override fun login(
        externalId: String,
        jwtBearerToken: String?,
    ) {
        Logging.log(LogLevel.DEBUG, "Calling deprecated login(externalId: $externalId, jwtBearerToken: ...${jwtBearerToken?.takeLast(8)})")

        if (isBackgroundThreadingEnabled) {
            waitForInit(operationName = "login")
        } else {
            requireInitForOperation("login")
        }

        val context = loginHelper.switchUser(externalId, jwtBearerToken) ?: return

        if (isBackgroundThreadingEnabled) {
            suspendifyOnIO { loginHelper.enqueueLogin(context) }
        } else {
            Thread {
                runBlocking(runtimeIoDispatcher) {
                    loginHelper.enqueueLogin(context)
                }
            }.start()
        }
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "Calling deprecated logout()")

        if (isBackgroundThreadingEnabled) {
            waitForInit(operationName = "logout")
        } else {
            requireInitForOperation("logout")
        }

        val context = logoutHelper.switchUser() ?: return

        if (isBackgroundThreadingEnabled) {
            suspendifyOnIO { logoutHelper.enqueueLogout(context) }
        } else {
            Thread {
                runBlocking(runtimeIoDispatcher) {
                    logoutHelper.enqueueLogout(context)
                }
            }.start()
        }
    }

    override fun updateUserJwt(
        externalId: String,
        token: String,
    ) {
        Logging.log(LogLevel.DEBUG, "updateUserJwt(externalId: $externalId, token: ...${token.takeLast(8)})")

        if (isBackgroundThreadingEnabled) {
            waitForInit(operationName = "updateUserJwt")
        } else {
            if (!isInitialized) {
                throw IllegalStateException("Must call 'initWithContext' before 'updateUserJwt'")
            }
        }

        jwtTokenStore.putJwt(externalId, token)
        // Wake the queue so any deferred ops can dispatch with the fresh token.
        operationRepo.forceExecuteOperations()
    }

    override fun addUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) {
        if (isBackgroundThreadingEnabled) {
            waitForInit(operationName = "addUserJwtInvalidatedListener")
        } else {
            if (!isInitialized) {
                throw IllegalStateException("Must call 'initWithContext' before 'addUserJwtInvalidatedListener'")
            }
        }
        jwtTokenStore.addUserJwtInvalidatedListener(listener)
    }

    override fun removeUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) {
        if (isBackgroundThreadingEnabled) {
            waitForInit(operationName = "removeUserJwtInvalidatedListener")
        } else {
            if (!isInitialized) {
                throw IllegalStateException("Must call 'initWithContext' before 'removeUserJwtInvalidatedListener'")
            }
        }
        jwtTokenStore.removeUserJwtInvalidatedListener(listener)
    }

    override fun <T> hasService(c: Class<T>): Boolean = services.hasService(c)

    override fun <T> getService(c: Class<T>): T = services.getService(c)

    override fun <T> getServiceOrNull(c: Class<T>): T? = services.getServiceOrNull(c)

    override fun <T> getAllServices(c: Class<T>): List<T> = services.getAllServices(c)

    /**
     * Ensures initialization is complete before proceeding with an operation.
     * Blocks if init is in progress; throws immediately if not started or failed.
     */
    @Suppress("UseCheckOrError")
    private fun requireInitForOperation(operationName: String) {
        when (initState) {
            InitState.NOT_STARTED -> {
                throw IllegalStateException("Must call 'initWithContext' before '$operationName'")
            }

            InitState.IN_PROGRESS -> {
                warnIfBlockingOnMainThread(operationName)
                waitForInit(operationName = operationName)
            }

            InitState.FAILED -> {
                throw initFailureException
                    ?: IllegalStateException("Initialization failed before '$operationName'")
            }

            InitState.SUCCESS -> {}
        }
    }

    /**
     * Surfaces the narrow race in legacy (FF-off) mode where [initWithContext] is running on a
     * background thread (so it's still IN_PROGRESS) and a *concurrent* call on the main thread
     * — an accessor via [getServiceWithFeatureGate], or [login]/[logout] via
     * [requireInitForOperation] — falls into the IN_PROGRESS branch and blocks on
     * [runBlocking] inside [waitForInit] / [waitAndReturn]. The common case (init called from
     * the main thread) doesn't hit this path because [initWithContext] itself blocks the caller
     * via `runBlocking`, so by the time any accessor runs init is already SUCCESS or FAILED.
     *
     * FF-on mode already accepts the ANR-vs-throw trade-off (see [waitUntilInitInternal]); the
     * warning is short-circuited there.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun warnIfBlockingOnMainThread(operationName: String?) {
        if (isBackgroundThreadingEnabled) return
        val onMain =
            try {
                AndroidUtils.isRunningOnMainThread()
            } catch (e: RuntimeException) {
                // Looper.getMainLooper() may be unavailable in test environments — skip the warning.
                Logging.debug("Could not determine main-thread status; skipping ANR-risk warning: ${e.message}")
                return
            }
        if (!onMain) return
        val target = operationName?.let { "'$it'" } ?: "this OneSignal API"
        Logging.warn(
            "Calling $target on the main thread while OneSignal initialization is still in progress. " +
                "This will block the UI thread until init completes (ANR risk on slow devices). " +
                "Prefer calling from a background thread, or use the suspend API " +
                "(OneSignal.initWithContextSuspend, OneSignal.getUser(), OneSignal.loginSuspend(), etc.) " +
                "from a coroutine.",
        )
    }

    /**
     * Blocking version that waits for initialization to complete.
     * Uses runBlocking to bridge to the suspend implementation.
     * Waits indefinitely until init completes and logs how long it took.
     *
     * @param operationName Optional operation name to include in error messages (e.g., "login", "logout")
     */
    private fun waitForInit(operationName: String? = null) {
        runBlocking(runtimeIoDispatcher) {
            waitUntilInitInternal(operationName)
        }
    }

    /**
     * Atomically transitions [initState] to a terminal state (SUCCESS or FAILED)
     * and completes [suspendCompletion] so any waiters unblock.
     *
     * Both the state write and the completion run under [initLock]. This closes a
     * race where another caller could observe the terminal state in between the two
     * writes, call `initWithContext` to flip back to IN_PROGRESS, and replace
     * [suspendCompletion] with a fresh deferred — at which point this thread's
     * completion would prematurely unblock waiters of the *second* init.
     */
    private fun completeInit(terminalState: InitState) {
        require(terminalState == InitState.SUCCESS || terminalState == InitState.FAILED) {
            "completeInit requires a terminal state, got $terminalState"
        }
        synchronized(initLock) {
            initState = terminalState
            suspendCompletion.complete(Unit)
        }
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
        // Local-capture state + deferred under initLock so we await on the same generation
        // we observed (a concurrent retry-after-FAILED can replace `suspendCompletion`).
        val observedState: InitState
        val completionToAwait: CompletableDeferred<Unit>?
        synchronized(initLock) {
            observedState = initState
            completionToAwait = if (observedState == InitState.IN_PROGRESS) suspendCompletion else null
        }

        when (observedState) {
            InitState.NOT_STARTED -> {
                val message =
                    if (operationName != null) {
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
                completionToAwait!!.await()

                // Log how long initialization took
                val elapsed = System.currentTimeMillis() - startTime
                val message =
                    if (operationName != null) {
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

    private fun <T> getServiceWithFeatureGate(getter: () -> T): T {
        if (isBackgroundThreadingEnabled) {
            return waitAndReturn(getter)
        }
        return when (initState) {
            InitState.SUCCESS -> {
                getter()
            }

            InitState.IN_PROGRESS -> {
                warnIfBlockingOnMainThread(operationName = null)
                waitAndReturn(getter)
            }

            InitState.FAILED -> {
                throw initFailureException
                    ?: IllegalStateException("Initialization failed. Cannot proceed.")
            }

            InitState.NOT_STARTED -> {
                throw IllegalStateException("Must call 'initWithContext' before use")
            }
        }
    }

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
        // Call suspendAndReturn directly to avoid nested runBlocking (waitAndReturn -> waitForInit -> runBlocking)
        return runBlocking(runtimeIoDispatcher) {
            suspendAndReturn(getter)
        }
    }

    // ===============================
    // Suspend API Implementation
    // ===============================

    override suspend fun getSession(): ISessionManager =
        withContext(runtimeIoDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getNotifications(): INotificationsManager =
        withContext(runtimeIoDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getLocation(): ILocationManager =
        withContext(runtimeIoDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getInAppMessages(): IInAppMessagesManager =
        withContext(runtimeIoDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getUser(): IUserManager =
        withContext(runtimeIoDispatcher) {
            suspendAndReturn { services.getService() }
        }

    override suspend fun getConsentRequired(): Boolean =
        withContext(runtimeIoDispatcher) {
            configModel.consentRequired ?: (_consentRequired == true)
        }

    override suspend fun setConsentRequired(required: Boolean) =
        withContext(runtimeIoDispatcher) {
            _consentRequired = required
            configModel.consentRequired = required
        }

    override suspend fun getConsentGiven(): Boolean =
        withContext(runtimeIoDispatcher) {
            configModel.consentGiven ?: (_consentGiven == true)
        }

    override suspend fun setConsentGiven(value: Boolean) =
        withContext(runtimeIoDispatcher) {
            val oldValue = _consentGiven
            _consentGiven = value
            configModel.consentGiven = value
            if (oldValue != value && value) {
                operationRepo.forceExecuteOperations()
            }
        }

    override suspend fun getDisableGMSMissingPrompt(): Boolean =
        withContext(runtimeIoDispatcher) {
            configModel.disableGMSMissingPrompt
        }

    override suspend fun setDisableGMSMissingPrompt(value: Boolean) =
        withContext(runtimeIoDispatcher) {
            _disableGMSMissingPrompt = value
            configModel.disableGMSMissingPrompt = value
        }

    override suspend fun initWithContextSuspend(
        context: Context,
        appId: String?,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context, appId: $appId)")

        // Same SDK-4507 warm-up as the synchronous variant. Reaching this entry point on the
        // main thread (e.g. SyncJobService.onStartJob -> suspendifyOnIO -> initWithContext(context))
        // pays the cold-init cost on the dispatcher used to enter [withContext] below, so warm
        // OneSignalDispatchers on a background thread before we touch [runtimeIoDispatcher].
        OneSignalDispatchers.prewarm()

        // Use IO dispatcher for initialization to prevent ANRs and optimize for I/O operations
        return withContext(runtimeIoDispatcher) {
            val shouldRunInit: Boolean
            // Local-capture under the lock so that even if a concurrent retry-after-FAILED
            // resets `suspendCompletion`, we await on the same generation we observed.
            val completionToAwait: CompletableDeferred<Unit>?
            synchronized(initLock) {
                if (initState.isSDKAccessible()) {
                    shouldRunInit = false
                    completionToAwait = suspendCompletion
                } else {
                    shouldRunInit = true
                    completionToAwait = null
                    initState = InitState.IN_PROGRESS
                    // Fresh latch for this init attempt.
                    suspendCompletion = CompletableDeferred()
                    // Only the call that actually starts init owns the failure-attribution exception.
                    // Re-entrant callers must not overwrite it -- otherwise the failure stack trace
                    // would point at the SyncJobService coroutine instead of the original initiator.
                    initFailureException = IllegalStateException("OneSignal initWithContext failed.")
                }
            }

            if (!shouldRunInit) {
                // Another caller has already started (or completed) init. Honor this method's
                // contract by suspending until initialization is *fully* completed -- not just
                // kicked off. This closes a race where re-entrant suspend callers (e.g. the
                // SyncJobService entry point under SDK_BACKGROUND_THREADING) would otherwise
                // proceed to use IBackgroundService implementations like SessionService whose
                // bootstrap() had not yet run, NPE'ing on still-null model fields.
                Logging.log(LogLevel.DEBUG, "initWithContext: init already in progress or completed, awaiting completion")
                completionToAwait!!.await()
                return@withContext initState == InitState.SUCCESS
            }

            val result = internalInit(context, appId)
            result
        }
    }

    override suspend fun loginSuspend(
        externalId: String,
        jwtBearerToken: String?,
    ) = withContext(runtimeIoDispatcher) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: ...${jwtBearerToken?.takeLast(8)})")

        // suspendUntilInit throws on NOT_STARTED / FAILED (preserving initFailureException as the
        // cause), and only returns once initState == SUCCESS — so no post-check is needed here.
        suspendUntilInit(operationName = "login")

        val context = loginHelper.switchUser(externalId, jwtBearerToken) ?: return@withContext
        loginHelper.enqueueLogin(context)
    }

    override suspend fun updateUserJwtSuspend(
        externalId: String,
        token: String,
    ) = withContext(runtimeIoDispatcher) {
        Logging.log(LogLevel.DEBUG, "updateUserJwtSuspend(externalId: $externalId, token: ...${token.takeLast(8)})")

        suspendUntilInit(operationName = "updateUserJwt")

        if (!isInitialized) {
            throw IllegalStateException("'initWithContext failed' before 'updateUserJwt'")
        }

        jwtTokenStore.putJwt(externalId, token)
        operationRepo.forceExecuteOperations()
    }

    override suspend fun logoutSuspend() =
        withContext(runtimeIoDispatcher) {
            Logging.log(LogLevel.DEBUG, "logoutSuspend()")

            suspendUntilInit(operationName = "logout")

            val context = logoutHelper.switchUser() ?: return@withContext
            logoutHelper.enqueueLogout(context)
        }
}
