package com.onesignal.internal

import android.content.Context
import android.os.Build
import com.onesignal.IOneSignal
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modules.IModule
import com.onesignal.common.safeInt
import com.onesignal.common.safeString
import com.onesignal.common.services.IServiceProvider
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.LatchAwaiter
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.CoreModule
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStoreFix
import com.onesignal.core.internal.preferences.PreferenceStores
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
import com.onesignal.session.internal.session.SessionModel
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.user.IUserManager
import com.onesignal.user.UserModule
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import org.json.JSONObject

internal class OneSignalImp : IOneSignal, IServiceProvider {
    @Volatile
    private var latchAwaiter = LatchAwaiter("OneSignalImp")

    @Volatile
    private var initState: InitState = InitState.NOT_STARTED

    override val sdkVersion: String = OneSignalUtils.sdkVersion

    override val isInitialized: Boolean
        get() = initState == InitState.SUCCESS

    override var consentRequired: Boolean
        get() = configModel?.consentRequired ?: (_consentRequired == true)
        set(value) {
            _consentRequired = value
            configModel?.consentRequired = value
        }

    override var consentGiven: Boolean
        get() = configModel?.consentGiven ?: (_consentGiven == true)
        set(value) {
            val oldValue = _consentGiven
            _consentGiven = value
            configModel?.consentGiven = value
            if (oldValue != value && value) {
                operationRepo?.forceExecuteOperations()
            }
        }

    override var disableGMSMissingPrompt: Boolean
        get() = configModel?.disableGMSMissingPrompt ?: (_disableGMSMissingPrompt == true)
        set(value) {
            _disableGMSMissingPrompt = value
            configModel?.disableGMSMissingPrompt = value
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
    private var operationRepo: IOperationRepo? = null
    private val identityModelStore: IdentityModelStore
        get() = services.getService()
    private val propertiesModelStore: PropertiesModelStore
        get() = services.getService()
    private val subscriptionModelStore: SubscriptionModelStore
        get() = services.getService()
    private val preferencesService: IPreferencesService
        get() = services.getService()

    // Other State
    private val services: ServiceProvider
    private var configModel: ConfigModel? = null
    private var sessionModel: SessionModel? = null
    private var _consentRequired: Boolean? = null
    private var _consentGiven: Boolean? = null
    private var _disableGMSMissingPrompt: Boolean? = null
    private val initLock: Any = Any()
    private val loginLock: Any = Any()

    private val listOfModules =
        listOf(
            "com.onesignal.notifications.NotificationsModule",
            "com.onesignal.inAppMessages.InAppMessagesModule",
            "com.onesignal.location.LocationModule",
        )

    init {
        val serviceBuilder = ServiceBuilder()

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
            module.register(serviceBuilder)
        }

        services = serviceBuilder.build()
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

        // get the current config model, if there is one
        configModel = services.getService<ConfigModelStore>().model
    }

    private fun updateConfig() {
        // if requires privacy consent was set prior to init, set it in the model now
        if (_consentRequired != null) {
            configModel!!.consentRequired = _consentRequired!!
        }

        // if privacy consent was set prior to init, set it in the model now
        if (_consentGiven != null) {
            configModel!!.consentGiven = _consentGiven!!
        }

        if (_disableGMSMissingPrompt != null) {
            configModel!!.disableGMSMissingPrompt = _disableGMSMissingPrompt!!
        }
    }

    private fun bootstrapServices(): StartupService {
        sessionModel = services.getService<SessionModelStore>().model
        operationRepo = services.getService<IOperationRepo>()

        val startupService = StartupService(services)
        // bootstrap all services
        startupService.bootstrap()

        return startupService
    }

    private fun initUser(forceCreateUser: Boolean) {
        // create a new local user
        if (forceCreateUser ||
            !identityModelStore!!.model.hasProperty(IdentityConstants.ONESIGNAL_ID)
        ) {
            val legacyPlayerId =
                preferencesService!!.getString(
                    PreferenceStores.ONESIGNAL,
                    PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID,
                )
            if (legacyPlayerId == null) {
                Logging.debug("initWithContext: creating new device-scoped user")
                createAndSwitchToNewUser()
                operationRepo!!.enqueue(
                    LoginUserOperation(
                        configModel!!.appId,
                        identityModelStore!!.model.onesignalId,
                        identityModelStore!!.model.externalId,
                    ),
                )
            } else {
                Logging.debug("initWithContext: creating user linked to subscription $legacyPlayerId")

                // Converting a 4.x SDK to the 5.x SDK.  We pull the legacy user sync values to create the subscription model, then enqueue
                // a specialized `LoginUserFromSubscriptionOperation`, which will drive fetching/refreshing of the local user
                // based on the subscription ID we do have.
                val legacyUserSyncString =
                    preferencesService!!.getString(
                        PreferenceStores.ONESIGNAL,
                        PreferenceOneSignalKeys.PREFS_LEGACY_USER_SYNCVALUES,
                    )
                var suppressBackendOperation = false

                if (legacyUserSyncString != null) {
                    val legacyUserSyncJSON = JSONObject(legacyUserSyncString)
                    val notificationTypes =
                        legacyUserSyncJSON.safeInt("notification_types")

                    val pushSubscriptionModel = SubscriptionModel()
                    pushSubscriptionModel.id = legacyPlayerId
                    pushSubscriptionModel.type = SubscriptionType.PUSH
                    pushSubscriptionModel.optedIn =
                        notificationTypes != SubscriptionStatus.NO_PERMISSION.value && notificationTypes != SubscriptionStatus.UNSUBSCRIBE.value
                    pushSubscriptionModel.address =
                        legacyUserSyncJSON.safeString("identifier") ?: ""
                    if (notificationTypes != null) {
                        pushSubscriptionModel.status =
                            SubscriptionStatus.fromInt(notificationTypes)
                                ?: SubscriptionStatus.NO_PERMISSION
                    } else {
                        pushSubscriptionModel.status = SubscriptionStatus.SUBSCRIBED
                    }

                    pushSubscriptionModel.sdk = OneSignalUtils.sdkVersion
                    pushSubscriptionModel.deviceOS = Build.VERSION.RELEASE
                    pushSubscriptionModel.carrier = DeviceUtils.getCarrierName(
                        services.getService<IApplicationService>().appContext,
                    ) ?: ""
                    pushSubscriptionModel.appVersion = AndroidUtils.getAppVersion(
                        services.getService<IApplicationService>().appContext,
                    ) ?: ""

                    configModel!!.pushSubscriptionId = legacyPlayerId
                    subscriptionModelStore!!.add(
                        pushSubscriptionModel,
                        ModelChangeTags.NO_PROPOGATE,
                    )
                    suppressBackendOperation = true
                }

                createAndSwitchToNewUser(suppressBackendOperation = suppressBackendOperation)

                operationRepo!!.enqueue(
                    LoginUserFromSubscriptionOperation(
                        configModel!!.appId,
                        identityModelStore!!.model.onesignalId,
                        legacyPlayerId,
                    ),
                )
                preferencesService!!.saveString(
                    PreferenceStores.ONESIGNAL,
                    PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID,
                    null,
                )
            }
        } else {
            Logging.debug("initWithContext: using cached user ${identityModelStore!!.model.onesignalId}")
        }
    }

    override fun initWithContext(
        context: Context,
        appId: String,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context, appId: $appId)")

        // do not do this again if already initialized or init is in progress
        synchronized(initLock) {
            if (initState.isSDKAccessible()) {
                Logging.log(LogLevel.DEBUG, "initWithContext: SDK already initialized or in progress")
                return true
            }

            initState = InitState.IN_PROGRESS
        }

        // init in background and return immediately to ensure non-blocking
        suspendifyOnThread {
            internalInit(context, appId)
        }
        initState = InitState.SUCCESS
        return true
    }

    /**
     * Called from internal classes only. Remain suspend until initialization is fully completed.
     */
    override suspend fun initWithContext(context: Context): Boolean {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)")

        // do not do this again if already initialized or init is in progress
        synchronized(initLock) {
            if (initState.isSDKAccessible()) {
                Logging.log(LogLevel.DEBUG, "initWithContext: SDK already initialized or in progress")
                return true
            }

            initState = InitState.IN_PROGRESS
        }

        val result = internalInit(context, null)
        initState = if (result) InitState.SUCCESS else InitState.FAILED
        return result
    }

    private fun internalInit(
        context: Context,
        appId: String?,
    ): Boolean {
        initEssentials(context)

        var forceCreateUser = false
        if (appId != null) {
            // If new appId is different from stored one, flag user recreation
            if (!configModel!!.hasProperty(ConfigModel::appId.name) || configModel!!.appId != appId) {
                forceCreateUser = true
            }
            configModel!!.appId = appId
        } else {
            // appId is null — fallback to legacy
            if (!configModel!!.hasProperty(ConfigModel::appId.name)) {
                val legacyAppId = getLegacyAppId()
                if (legacyAppId == null) {
                    Logging.warn("suspendInitInternal: no appId provided or found in legacy config.")
                    initState = InitState.FAILED
                    latchAwaiter.release()
                    return false
                }
                forceCreateUser = true
                configModel!!.appId = legacyAppId
            }
        }

        updateConfig()
        val startupService = bootstrapServices()
        initUser(forceCreateUser)
        startupService.scheduleStart()
        latchAwaiter.release()
        return true
    }

    override fun login(
        externalId: String,
        jwtBearerToken: String?,
    ) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: $jwtBearerToken)")

        if (!initState.isSDKAccessible()) {
            throw IllegalStateException("Must call 'initWithContext' before 'login'")
        }

        waitForInit()

        var currentIdentityExternalId: String? = null
        var currentIdentityOneSignalId: String? = null
        var newIdentityOneSignalId: String = ""

        // only allow one login/logout at a time
        synchronized(loginLock) {
            currentIdentityExternalId = identityModelStore!!.model.externalId
            currentIdentityOneSignalId = identityModelStore!!.model.onesignalId

            if (currentIdentityExternalId == externalId) {
                return
            }

            // TODO: Set JWT Token for all future requests.
            createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            newIdentityOneSignalId = identityModelStore!!.model.onesignalId
        }

        // on a background thread enqueue the login/fetch of the new user
        suspendifyOnThread {
            // We specify an "existingOneSignalId" here when the current user is anonymous to
            // allow this login to attempt a "conversion" of the anonymous user.  We also
            // wait for the LoginUserOperation operation to execute, which can take a *very* long
            // time if network conditions prevent the operation to succeed.  This allows us to
            // provide a callback to the caller when we can absolutely say the user is logged
            // in, so they may take action on their own backend.
            val result =
                operationRepo!!.enqueueAndWait(
                    LoginUserOperation(
                        configModel!!.appId,
                        newIdentityOneSignalId,
                        externalId,
                        if (currentIdentityExternalId == null) currentIdentityOneSignalId else null,
                    ),
                )

            if (!result) {
                Logging.log(LogLevel.ERROR, "Could not login user")
            }
        }
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "logout()")

        if (!initState.isSDKAccessible()) {
            throw IllegalStateException("Must call 'initWithContext' before 'logout'")
        }

        waitForInit()

        // only allow one login/logout at a time
        synchronized(loginLock) {
            if (identityModelStore!!.model.externalId == null) {
                return
            }

            createAndSwitchToNewUser()
            operationRepo!!.enqueue(
                LoginUserOperation(
                    configModel!!.appId,
                    identityModelStore!!.model.onesignalId,
                    identityModelStore!!.model.externalId,
                ),
            )

            // TODO: remove JWT Token for all future requests.
        }
    }

    /**
     * Returns the cached app ID from v4 of the SDK, if available.
     */
    private fun getLegacyAppId(): String? {
        return preferencesService.getString(
            PreferenceStores.ONESIGNAL,
            PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID,
        )
    }

    private fun createAndSwitchToNewUser(
        suppressBackendOperation: Boolean = false,
        modify: (
            (identityModel: IdentityModel, propertiesModel: PropertiesModel) -> Unit
        )? = null,
    ) {
        Logging.debug("createAndSwitchToNewUser()")

        // create a new identity and properties model locally
        val sdkId = IDManager.createLocalId()

        val identityModel = IdentityModel()
        identityModel.onesignalId = sdkId

        val propertiesModel = PropertiesModel()
        propertiesModel.onesignalId = sdkId

        if (modify != null) {
            modify(identityModel, propertiesModel)
        }

        val subscriptions = mutableListOf<SubscriptionModel>()

        // Create the push subscription for this device under the new user, copying the current
        // user's push subscription if one exists.  We also copy the ID. If the ID is local there
        // will already be a CreateSubscriptionOperation on the queue.  If the ID is remote the subscription
        // will be automatically transferred over to this new user being created.  If there is no
        // current push subscription we do a "normal" replace which will drive adding a CreateSubscriptionOperation
        // to the queue.
        val currentPushSubscription = subscriptionModelStore!!.list().firstOrNull { it.id == configModel!!.pushSubscriptionId }
        val newPushSubscription = SubscriptionModel()

        newPushSubscription.id = currentPushSubscription?.id ?: IDManager.createLocalId()
        newPushSubscription.type = SubscriptionType.PUSH
        newPushSubscription.optedIn = currentPushSubscription?.optedIn ?: true
        newPushSubscription.address = currentPushSubscription?.address ?: ""
        newPushSubscription.status = currentPushSubscription?.status ?: SubscriptionStatus.NO_PERMISSION
        newPushSubscription.sdk = OneSignalUtils.sdkVersion
        newPushSubscription.deviceOS = Build.VERSION.RELEASE
        newPushSubscription.carrier = DeviceUtils.getCarrierName(services.getService<IApplicationService>().appContext) ?: ""
        newPushSubscription.appVersion = AndroidUtils.getAppVersion(services.getService<IApplicationService>().appContext) ?: ""

        // ensure we always know this devices push subscription ID
        configModel!!.pushSubscriptionId = newPushSubscription.id

        subscriptions.add(newPushSubscription)

        // The next 4 lines makes this user the effective user locally.  We clear the subscriptions
        // first as a `NO_PROPOGATE` change because we don't want to drive deleting the cleared subscriptions
        // on the backend.  Once cleared we can then setup the new identity/properties model, and add
        // the new user's subscriptions as a `NORMAL` change, which will drive changes to the backend.
        subscriptionModelStore!!.clear(ModelChangeTags.NO_PROPOGATE)
        identityModelStore!!.replace(identityModel)
        propertiesModelStore!!.replace(propertiesModel)

        if (suppressBackendOperation) {
            subscriptionModelStore!!.replaceAll(subscriptions, ModelChangeTags.NO_PROPOGATE)
        } else {
            subscriptionModelStore!!.replaceAll(subscriptions)
        }
    }

    override fun <T> hasService(c: Class<T>): Boolean = services.hasService(c)

    override fun <T> getService(c: Class<T>): T = services.getService(c)

    override fun <T> getServiceOrNull(c: Class<T>): T? = services.getServiceOrNull(c)

    override fun <T> getAllServices(c: Class<T>): List<T> = services.getAllServices(c)

    private fun waitForInit() {
        latchAwaiter.await()
    }

    private fun <T> waitAndReturn(getter: () -> T): T {
        when (initState) {
            InitState.NOT_STARTED -> {
                throw IllegalStateException("Must call 'initWithContext' before use")
            }
            InitState.IN_PROGRESS -> {
                Logging.debug("Waiting for init to complete...")
                waitForInit()
            }
            InitState.FAILED -> {
                throw IllegalStateException("Initialization failed. Cannot proceed.")
            }
            else -> {
                // SUCCESS
                waitForInit()
            }
        }

        return getter()
    }
}
