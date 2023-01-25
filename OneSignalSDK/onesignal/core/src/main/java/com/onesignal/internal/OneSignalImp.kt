package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modules.IModule
import com.onesignal.common.safeString
import com.onesignal.common.services.IServiceProvider
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.CoreModule
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
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
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.TransferSubscriptionOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class OneSignalImp : IOneSignal, IServiceProvider {
    override val sdkVersion: String = OneSignalUtils.sdkVersion
    override var isInitialized: Boolean = false

    override var requiresPrivacyConsent: Boolean
        get() = _configModel?.requiresPrivacyConsent ?: (_requiresPrivacyConsent == true)
        set(value) {
            _requiresPrivacyConsent = value
            _configModel?.requiresPrivacyConsent = value
        }

    override var privacyConsent: Boolean
        get() = _configModel?.givenPrivacyConsent ?: (_givenPrivacyConsent == true)
        set(value) {
            _givenPrivacyConsent = value
            _configModel?.givenPrivacyConsent = value
        }

    override var disableGMSMissingPrompt: Boolean
        get() = _configModel?.disableGMSMissingPrompt ?: (_disableGMSMissingPrompt == true)
        set(value) {
            _disableGMSMissingPrompt = value
            _configModel?.disableGMSMissingPrompt = value
        }

    // we hardcode the DebugManager implementation so it can be used prior to calling `initWithContext`
    override val debug: IDebugManager = DebugManager()
    override val session: ISessionManager get() = if (isInitialized) _session!! else throw Exception("Must call 'initWithContext' before use")
    override val notifications: INotificationsManager get() = if (isInitialized) _notifications!! else throw Exception("Must call 'initWithContext' before use")
    override val location: ILocationManager get() = if (isInitialized) _location!! else throw Exception("Must call 'initWithContext' before use")
    override val inAppMessages: IInAppMessagesManager get() = if (isInitialized) _iam!! else throw Exception("Must call 'initWithContext' before use")
    override val User: IUserManager get() = if (isInitialized) _user!! else throw Exception("Must call 'initWithContext' before use")

    // Services required by this class
    private var _user: IUserManager? = null
    private var _session: ISessionManager? = null
    private var _iam: IInAppMessagesManager? = null
    private var _location: ILocationManager? = null
    private var _notifications: INotificationsManager? = null
    private var _operationRepo: IOperationRepo? = null
    private var _identityModelStore: IdentityModelStore? = null
    private var _propertiesModelStore: PropertiesModelStore? = null
    private var _subscriptionModelStore: SubscriptionModelStore? = null
    private var _startupService: StartupService? = null
    private var _preferencesService: IPreferencesService? = null

    // Other State
    private val _services: ServiceProvider
    private var _configModel: ConfigModel? = null
    private var _sessionModel: SessionModel? = null
    private var _requiresPrivacyConsent: Boolean? = null
    private var _givenPrivacyConsent: Boolean? = null
    private var _disableGMSMissingPrompt: Boolean? = null
    private val _loginLock: Any = Any()

    private val _listOfModules = listOf(
        "com.onesignal.notifications.NotificationsModule",
        "com.onesignal.inAppMessages.InAppMessagesModule",
        "com.onesignal.location.LocationModule"
    )

    init {
        val serviceBuilder = ServiceBuilder()

        val modules = mutableListOf<IModule>()

        modules.add(CoreModule())
        modules.add(SessionModule())
        modules.add(UserModule())
        for (moduleClassName in _listOfModules) {
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

        _services = serviceBuilder.build()
    }

    override fun initWithContext(context: Context, appId: String?) {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context, appId: $appId)")

        // do not do this again if already initialized
        if (isInitialized) {
            return
        }

        // start the application service. This is called explicitly first because we want
        // to make sure it has the context provided on input, for all other startable services
        // to depend on if needed.
        val applicationService = _services.getService<IApplicationService>()
        (applicationService as ApplicationService).start(context)

        // Give the logging singleton access to the application service to support visual logging.
        Logging.applicationService = applicationService

        // get the current config model, if there is one
        _configModel = _services.getService<ConfigModelStore>().model
        _sessionModel = _services.getService<SessionModelStore>().model

        var forceCreateUser = false
        // if the app id was specified as input, update the config model with it
        if (appId != null) {
            if (!_configModel!!.hasProperty(ConfigModel::appId.name) || _configModel!!.appId != appId) {
                forceCreateUser = true
            }
            _configModel!!.appId = appId
        }

        // if requires privacy consent was set prior to init, set it in the model now
        if (_requiresPrivacyConsent != null) {
            _configModel!!.requiresPrivacyConsent = _requiresPrivacyConsent!!
        }

        // if privacy consent was set prior to init, set it in the model now
        if (_givenPrivacyConsent != null) {
            _configModel!!.givenPrivacyConsent = _givenPrivacyConsent!!
        }

        if (_disableGMSMissingPrompt != null) {
            _configModel!!.disableGMSMissingPrompt = _disableGMSMissingPrompt!!
        }

        // "Inject" the services required by this main class
        _location = _services.getService()
        _user = _services.getService()
        _session = _services.getService()
        _iam = _services.getService()
        _notifications = _services.getService()
        _operationRepo = _services.getService()
        _propertiesModelStore = _services.getService()
        _identityModelStore = _services.getService()
        _subscriptionModelStore = _services.getService()
        _preferencesService = _services.getService()

        // Instantiate and call the IStartableServices
        _startupService = _services.getService()
        _startupService!!.bootstrap()

        if (forceCreateUser || !_identityModelStore!!.model.hasProperty(IdentityConstants.ONESIGNAL_ID)) {
            val legacyPlayerId = _preferencesService!!.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID)
            if(legacyPlayerId == null) {
                Logging.debug("initWithContext: creating new device-scoped user")
                createAndSwitchToNewUser()
                _operationRepo!!.enqueue(LoginUserOperation(_configModel!!.appId, _identityModelStore!!.model.onesignalId, _identityModelStore!!.model.externalId))
            }
            else {
                Logging.debug("initWithContext: creating user linked to subscription $legacyPlayerId")

                // Converting a 4.x SDK to the 5.x SDK.  We pull the legacy user sync values to create the subscription model, then enqueue
                // a specialized `LoginUserFromSubscriptionOperation`, which will drive fetching/refreshing of the local user
                // based on the subscription ID we do have.
                val legacyUserSyncString = _preferencesService!!.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_USER_SYNCVALUES)
                var suppressBackendOperation = false

                if(legacyUserSyncString != null) {
                    val legacyUserSyncJSON = JSONObject(legacyUserSyncString)
                    val notificationTypes = legacyUserSyncJSON.getInt("notification_types")

                    val pushSubscriptionModel = SubscriptionModel()
                    pushSubscriptionModel.id = legacyPlayerId
                    pushSubscriptionModel.type = SubscriptionType.PUSH
                    pushSubscriptionModel.optedIn = notificationTypes != SubscriptionStatus.NO_PERMISSION.value && notificationTypes != SubscriptionStatus.UNSUBSCRIBE.value
                    pushSubscriptionModel.address = legacyUserSyncJSON.safeString("identifier") ?: ""
                    pushSubscriptionModel.status = SubscriptionStatus.fromInt(notificationTypes) ?: SubscriptionStatus.NO_PERMISSION
                    _configModel!!.pushSubscriptionId = legacyPlayerId
                    _subscriptionModelStore!!.add(pushSubscriptionModel, ModelChangeTags.NO_PROPOGATE)
                    suppressBackendOperation = true
                }

                createAndSwitchToNewUser(suppressBackendOperation = suppressBackendOperation)

                _operationRepo!!.enqueue(LoginUserFromSubscriptionOperation(_configModel!!.appId, _identityModelStore!!.model.onesignalId, legacyPlayerId))
                _preferencesService!!.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID, null)
            }
        } else {
            Logging.debug("initWithContext: using cached user ${_identityModelStore!!.model.onesignalId}")
            _operationRepo!!.enqueue(RefreshUserOperation(_configModel!!.appId, _identityModelStore!!.model.onesignalId))
        }

        _startupService!!.start()

        isInitialized = true
    }

    override fun login(externalId: String, jwtBearerToken: String?) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: $jwtBearerToken)")

        if (!isInitialized) {
            throw Exception("Must call 'initWithContext' before use")
        }

        var currentIdentityExternalId: String? = null
        var currentIdentityOneSignalId: String? = null
        var newIdentityOneSignalId: String = ""

        // only allow one login/logout at a time
        synchronized(_loginLock) {
            currentIdentityExternalId = _identityModelStore!!.model.externalId
            currentIdentityOneSignalId = _identityModelStore!!.model.onesignalId

            if (currentIdentityExternalId == externalId) {
                // login is for same user that is already logged in, fetch (refresh)
                // the current user.
                _operationRepo!!.enqueue(
                    RefreshUserOperation(
                        _configModel!!.appId,
                        _identityModelStore!!.model.onesignalId
                    ),
                    true
                )
                return
            }

            // TODO: Set JWT Token for all future requests.
            createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            newIdentityOneSignalId = _identityModelStore!!.model.onesignalId
        }

        // on a background thread enqueue the login/fetch of the new user
        suspendifyOnThread {
            // We specify an "existingOneSignalId" here when the current user is anonymous to
            // allow this login to attempt a "conversion" of the anonymous user.  We also
            // wait for the LoginUserOperation operation to execute, which can take a *very* long
            // time if network conditions prevent the operation to succeed.  This allows us to
            // provide a callback to the caller when we can absolutely say the user is logged
            // in, so they may take action on their own backend.
            val result = _operationRepo!!.enqueueAndWait(
                LoginUserOperation(
                    _configModel!!.appId,
                    newIdentityOneSignalId,
                    externalId,
                    if (currentIdentityExternalId == null) currentIdentityOneSignalId else null
                ),
                true
            )

            if (!result) {
                throw Exception("Could not login user")
            }

            // enqueue a RefreshUserOperation to pull the user from the backend and refresh the models.
            // This is a separate enqueue operation to ensure any outstanding operations that happened
            // after the createAndSwitchToNewUser have been executed, and the retrieval will be the
            // most up to date reflection of the user.
            _operationRepo!!.enqueueAndWait(
                RefreshUserOperation(
                    _configModel!!.appId,
                    _identityModelStore!!.model.onesignalId
                ),
                true
            )
        }
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "logout()")

        if (!isInitialized) {
            throw Exception("Must call 'initWithContext' before use")
        }

        // only allow one login/logout at a time
        synchronized(_loginLock) {
            if (_identityModelStore!!.model.externalId == null) {
                return
            }

            createAndSwitchToNewUser()
            _operationRepo!!.enqueue(
                LoginUserOperation(
                    _configModel!!.appId,
                    _identityModelStore!!.model.onesignalId,
                    _identityModelStore!!.model.externalId
                )
            )

            // TODO: remove JWT Token for all future requests.
        }
    }

    private fun createAndSwitchToNewUser(suppressBackendOperation: Boolean = false, modify: ((identityModel: IdentityModel, propertiesModel: PropertiesModel) -> Unit)? = null) {
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
        val currentPushSubscription = _subscriptionModelStore!!.list().firstOrNull { it.id == _configModel!!.pushSubscriptionId }
        val newPushSubscription = SubscriptionModel()

        newPushSubscription.id = currentPushSubscription?.id ?: IDManager.createLocalId()
        newPushSubscription.type = SubscriptionType.PUSH
        newPushSubscription.optedIn = currentPushSubscription?.optedIn ?: true
        newPushSubscription.address = currentPushSubscription?.address ?: ""
        newPushSubscription.status = currentPushSubscription?.status ?: SubscriptionStatus.NO_PERMISSION

        // ensure we always know this devices push subscription ID
        _configModel!!.pushSubscriptionId = newPushSubscription.id

        subscriptions.add(newPushSubscription)

        // The next 4 lines makes this user the effective user locally.  We clear the subscriptions
        // first as a `NO_PROPOGATE` change because we don't want to drive deleting the cleared subscriptions
        // on the backend.  Once cleared we can then setup the new identity/properties model, and add
        // the new user's subscriptions as a `NORMAL` change, which will drive changes to the backend.
        _subscriptionModelStore!!.clear(ModelChangeTags.NO_PROPOGATE)
        _identityModelStore!!.replace(identityModel)
        _propertiesModelStore!!.replace(propertiesModel)

        if (currentPushSubscription != null) {
            _operationRepo!!.enqueue(TransferSubscriptionOperation(_configModel!!.appId, currentPushSubscription.id, sdkId))
            _subscriptionModelStore!!.replaceAll(subscriptions, ModelChangeTags.NO_PROPOGATE)
        } else {
            _subscriptionModelStore!!.replaceAll(subscriptions)
        }
    }

    override fun <T> hasService(c: Class<T>): Boolean = _services.hasService(c)
    override fun <T> getService(c: Class<T>): T = _services.getService(c)
    override fun <T> getServiceOrNull(c: Class<T>): T? = _services.getServiceOrNull(c)
    override fun <T> getAllServices(c: Class<T>): List<T> = _services.getAllServices(c)
}
