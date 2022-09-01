package com.onesignal.core.internal

import android.content.Context
import com.onesignal.core.debug.IDebugManager
import com.onesignal.core.IOneSignal
import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.core.internal.common.events.ICallbackProducer
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
import com.onesignal.core.internal.service.IServiceProvider
import com.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.core.internal.service.ServiceProvider
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.core.internal.user.IUserSwitcher
import com.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.core.user.IUserManager
import com.onesignal.iam.IIAMManager
import com.onesignal.iam.internal.IAMModule
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.LocationModule
import com.onesignal.notification.INotificationsManager
import com.onesignal.notification.internal.NotificationModule
import java.util.UUID
import kotlinx.coroutines.delay

internal class OneSignalImp() : IOneSignal, IServiceProvider {
    override val sdkVersion: String = OneSignalUtils.sdkVersion
    override var isInitialized: Boolean = false

    override var requiresPrivacyConsent: Boolean
        get() = _requiresPrivacyConsent == true
        set(value) {
            _requiresPrivacyConsent = value
            _configModel?.requiresPrivacyConsent = value
        }

    override var privacyConsent: Boolean = false

    override val notifications: INotificationsManager get() = if (isInitialized) _notifications!! else throw Exception("Must call 'initWithContext' before use")
    override val location: ILocationManager get() = if (isInitialized) _location!! else throw Exception("Must call 'initWithContext' before use")
    override val user: IUserManager get() = if (isInitialized) _user!! else throw Exception("Must call 'initWithContext' before use")
    override val iam: IIAMManager get() = if (isInitialized) _iam!! else throw Exception("Must call 'initWithContext' before use")
    override val debug: IDebugManager = DebugManager()

    // Services required by this class
    private var _user: IUserManager? = null
    private var _userSwitcher: IUserSwitcher? = null
    private var _iam: IIAMManager? = null
    private var _location: ILocationManager? = null
    private var _notifications: INotificationsManager? = null
    private var _identityModelStore: IdentityModelStore? = null
    private var _propertiesModelStore: PropertiesModelStore? = null
    private var _startupService: StartupService? = null

    // Other State
    private val _services: ServiceProvider
    private var _userConflictResolverNotifier: ICallbackProducer<IUserIdentityConflictResolver> = CallbackProducer()
    private var _configModel: ConfigModel? = null
    private var _sessionModel: SessionModel? = null
    private var _requiresPrivacyConsent: Boolean? = null
    private var _haveServicesStarted: Boolean = false

    init {
        var serviceBuilder = ServiceBuilder()

        CoreModule.register(serviceBuilder)
        NotificationModule.register(serviceBuilder)
        LocationModule.register(serviceBuilder)
        IAMModule.register(serviceBuilder)

        _services = serviceBuilder.build()
    }

    override fun initWithContext(context: Context) {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)")

        // do not do this again if already initialized
        if (isInitialized)
            return

        // start the application service. This is called explicitly first because we want
        // to make sure it has the context provided on input, for all other startable services
        // to depend on if needed.
        (_services.getService<IApplicationService>() as ApplicationService).start(context)

        // get the current config model, if there is one
        _configModel = _services.getService<ConfigModelStore>().get()
        _sessionModel = _services.getService<SessionModelStore>().get()

        // if privacy consent was set prior to init, set it in the model now
        if (_requiresPrivacyConsent != null)
            _configModel!!.requiresPrivacyConsent = _requiresPrivacyConsent!!

        // "Inject" the services required by this main class
        _location = _services.getService()
        _user = _services.getService()
        _userSwitcher = _services.getService()
        _iam = _services.getService()
        _notifications = _services.getService()
        _propertiesModelStore = _services.getService()
        _identityModelStore = _services.getService()

        isInitialized = true

        if (_appId != null) {
            _configModel!!.appId = _appId!!
            startServices()
        }
    }

    private var _appId: String? = null

    override fun setAppId(appId: String) {
        Logging.log(LogLevel.DEBUG, "setAppId(appId: $appId)")

        if (!isInitialized) {
            _appId = appId
        } else {
            _configModel!!.appId = _appId!!
            startServices()
        }
    }

    private fun startServices() {
        if (!_haveServicesStarted) {
            _haveServicesStarted = true
            // Instantiate and call the IStartableServices
            _startupService = _services.getService()
            _startupService!!.start()
        }
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun login(externalId: String, externalIdHash: String?): IUserManager {
        Logging.log(
            LogLevel.DEBUG,
            "login(externalId: $externalId, externalIdHash: $externalIdHash)"
        )

        if (!isInitialized)
            throw Exception("Must call 'initWithContext' before use")

        var retIdentityModel: IdentityModel? = null
        var retPropertiesModel: PropertiesModel? = null

        // TODO: Attempt to retrieve user from backend,
        //       * if exists set identityModel aliases and oneSignalId, and all of properties model.
        //       * If doesn't exist create user?
        //       * If invalid auth hash/user throw exception?
        delay(1000L) // Simulate call to drive suspension

        // ASSUME BACKEND DOESN'T HAVE USER FOR NOW, CREATE LOCALLY
        // TODO: Attempt to retrieve user from model stores. Should lookup be by external ID or onesignal id?
        //       Feels more right to use the onesignal ID as that cannot change.  But the user provides an
        //       externalID so we would need to keep a cache of externalID->onesignalID mappings in the event
        //       there is no connectivity and we can't pull the user from the backend.
        var identityModel = _identityModelStore!!.get(externalId)
        var propertiesModel = _propertiesModelStore!!.get(externalId)

        if (identityModel == null) {
            identityModel = IdentityModel()
            identityModel.id = UUID.randomUUID().toString()
            identityModel.userId = externalId
            identityModel.userIdAuthHash = externalIdHash
            _identityModelStore!!.add(externalId, identityModel)
        }

        if (propertiesModel == null) {
            propertiesModel = PropertiesModel()
            propertiesModel.id = identityModel.id
            _propertiesModelStore!!.add(externalId, propertiesModel)
        }

        retIdentityModel = identityModel
        retPropertiesModel = propertiesModel
        // TODO: Add repo op to create user on the backend.

        if (retIdentityModel != null && retPropertiesModel != null) {
            // TODO: Clear anything else out?
            // TODO: Unhook the old models from the operation store, any changes to them are no longer applicable.
            // TODO: Hook the new models to the operation store, any changes to these are applicable.
            _userSwitcher!!.setUser(retIdentityModel, retPropertiesModel)
        }

        return user
    }

    override suspend fun loginGuest(): IUserManager {
        Logging.log(LogLevel.DEBUG, "loginGuest()")

        if (!isInitialized)
            throw Exception("Must call 'initWithContext' before use")

        var retIdentityModel = IdentityModel()
        var retPropertiesModel = PropertiesModel()

        // TODO: Add repo op to create user on the backend.
        // TODO: Do we do anything else?  Save the anonymous user in the store?  Save the anonymous user in the backend?
        delay(1000L) // Simulate call to drive suspension

        // TODO: Clear anything else out?
        // TODO: Unhook the old models from the operation store, any changes to them are no longer applicable.
        // TODO: Hook the new models to the operation store, any changes to these are applicable.
        _userSwitcher!!.setUser(retIdentityModel, retPropertiesModel)

        return user
    }

    override fun setUserConflictResolver(handler: IUserIdentityConflictResolver?) {
        Logging.log(LogLevel.DEBUG, "setUserConflictResolver(handler: $handler)")
        _userConflictResolverNotifier.set(handler)
    }

    override fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) {
        Logging.log(LogLevel.DEBUG, "setUserConflictResolver(handler: $handler)")
        _userConflictResolverNotifier.set(object : IUserIdentityConflictResolver {
            override fun resolve(local: IUserManager, remote: IUserManager): IUserManager {
                return handler(local, remote)
            }
        })
    }

    override fun <T> hasService(c: Class<T>): Boolean = _services.hasService(c)
    override fun <T> getService(c: Class<T>): T = _services.getService(c)
    override fun <T> getServiceOrNull(c: Class<T>): T? = _services.getServiceOrNull(c)
    override fun <T> getAllServices(c: Class<T>): List<T> = _services.getAllServices(c)
}
