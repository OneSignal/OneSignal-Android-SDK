package com.onesignal.core.internal

import android.content.Context
import com.onesignal.core.IOneSignal
import com.onesignal.core.debug.IDebugManager
import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.core.internal.debug.DebugManager
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.IdentityModel
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.models.PropertiesModel
import com.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.core.internal.models.SessionModel
import com.onesignal.core.internal.models.SessionModelStore
import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.CreateUserOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.service.IServiceProvider
import com.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.core.internal.service.ServiceProvider
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.core.user.IUserManager
import com.onesignal.iam.IIAMManager
import com.onesignal.iam.internal.IAMModule
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.LocationModule
import com.onesignal.notification.INotificationsManager
import com.onesignal.notification.internal.NotificationModule

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

    override val debug: IDebugManager = DebugManager()
    override val notifications: INotificationsManager get() = if (isInitialized) _notifications!! else throw Exception("Must call 'initWithContext' before use")
    override val location: ILocationManager get() = if (isInitialized) _location!! else throw Exception("Must call 'initWithContext' before use")
    override val iam: IIAMManager get() = if (isInitialized) _iam!! else throw Exception("Must call 'initWithContext' before use")
    override val user: IUserManager
        get() {
            if (!isInitialized) {
                throw Exception("Must call 'initWithContext' before use")
            }

            if (!_hasCreatedBackendUser) {
                synchronized(_hasCreatedBackendUser) {
                    // check again now that we are synchronized.
                    if (!_hasCreatedBackendUser) {
                        // Create the new user in the backend as an operation
                        _operationRepo!!.enqueue(CreateUserOperation(_configModel!!.appId, _identityModelStore!!.get().onesignalId, _identityModelStore!!.get().externalId))
                        _hasCreatedBackendUser = true
                    }
                }
            }

            return _user!!
        }

    // Services required by this class
    private var _user: IUserManager? = null
    private var _hasCreatedBackendUser: Boolean = false
    private var _iam: IIAMManager? = null
    private var _location: ILocationManager? = null
    private var _notifications: INotificationsManager? = null
    private var _operationRepo: IOperationRepo? = null
    private var _identityModelStore: IdentityModelStore? = null
    private var _propertiesModelStore: PropertiesModelStore? = null
    private var _subscriptionModelStore: SubscriptionModelStore? = null
    private var _startupService: StartupService? = null

    // Other State
    private val _services: ServiceProvider
    private var _configModel: ConfigModel? = null
    private var _sessionModel: SessionModel? = null
    private var _requiresPrivacyConsent: Boolean? = null
    private var _givenPrivacyConsent: Boolean? = null

    init {
        var serviceBuilder = ServiceBuilder()

        CoreModule.register(serviceBuilder)
        NotificationModule.register(serviceBuilder)
        LocationModule.register(serviceBuilder)
        IAMModule.register(serviceBuilder)

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
        (_services.getService<IApplicationService>() as ApplicationService).start(context)

        // get the current config model, if there is one
        _configModel = _services.getService<ConfigModelStore>().get()
        _sessionModel = _services.getService<SessionModelStore>().get()

        // if the app id was specified as input, update the config model with it
        if (appId != null) {
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

        // "Inject" the services required by this main class
        _location = _services.getService()
        _user = _services.getService()
        _iam = _services.getService()
        _notifications = _services.getService()
        _operationRepo = _services.getService()
        _propertiesModelStore = _services.getService()
        _identityModelStore = _services.getService()
        _subscriptionModelStore = _services.getService()

        // Instantiate and call the IStartableServices
        _startupService = _services.getService()
        _startupService!!.bootstrap()

        // TODO: Only do this if nothing persisted
        createAndSwitchToNewUser()

        _startupService!!.start()

        isInitialized = true
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun login(externalId: String, jwtBearerToken: String?) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: $jwtBearerToken)")

        if (!isInitialized) {
            throw Exception("Must call 'initWithContext' before use")
        }

        synchronized(_hasCreatedBackendUser) {
            createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            _hasCreatedBackendUser = true
        }

        // TODO: Set JWT Token for all future requests.

        // Create the new user in the backend as an operation
        _operationRepo!!.execute(CreateUserOperation(_configModel!!.appId, _identityModelStore!!.get().onesignalId, _identityModelStore!!.get().externalId))
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "logout()")

        if (!isInitialized) {
            throw Exception("Must call 'initWithContext' before use")
        }

        synchronized(_hasCreatedBackendUser) {
            if (!_hasCreatedBackendUser) {
                return
            }

            createAndSwitchToNewUser()
            _hasCreatedBackendUser = false
        }

        // TODO: remove JWT Token for all future requests.
    }

    private fun createAndSwitchToNewUser(modify: ((identityModel: IdentityModel, propertiesModel: PropertiesModel) -> Unit)? = null) {
        // create a new identity and properties model locally
        val sdkId = IDManager.createLocalId()
        var identityModel = IdentityModel()
        identityModel.onesignalId = sdkId

        var propertiesModel = PropertiesModel()
        propertiesModel.onesignalId = sdkId

        if (modify != null) {
            modify(identityModel, propertiesModel)
        }

        val subscriptions = mutableListOf<SubscriptionModel>()

        // Create the push subscription for this device under the new user, copying the current
        // user's push subscription if one exists.
        val currentPushSubscription = _subscriptionModelStore!!.list().firstOrNull { it.type == SubscriptionType.PUSH }
        val newPushSubscription = SubscriptionModel()

        newPushSubscription.id = IDManager.createLocalId()
        newPushSubscription.type = SubscriptionType.PUSH
        newPushSubscription.enabled = currentPushSubscription?.enabled ?: true
        newPushSubscription.address = currentPushSubscription?.address ?: ""

        subscriptions.add(newPushSubscription)

        // The next 3 lines makes this user the effective user locally.
        _identityModelStore!!.replace(identityModel)
        _propertiesModelStore!!.replace(propertiesModel)
        _subscriptionModelStore!!.replaceAll(subscriptions)
    }

    override fun <T> hasService(c: Class<T>): Boolean = _services.hasService(c)
    override fun <T> getService(c: Class<T>): T = _services.getService(c)
    override fun <T> getServiceOrNull(c: Class<T>): T? = _services.getServiceOrNull(c)
    override fun <T> getAllServices(c: Class<T>): List<T> = _services.getAllServices(c)
}
