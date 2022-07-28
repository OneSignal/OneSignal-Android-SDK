package com.onesignal.onesignal.core.internal

import android.content.Context
import com.onesignal.onesignal.core.IOneSignal
import com.onesignal.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.onesignal.core.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.core.internal.models.*
import com.onesignal.onesignal.core.internal.operations.BootstrapOperation
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.service.*
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.user.IUserSwitcher
import com.onesignal.onesignal.location.internal.LocationModule
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.internal.NotificationModule
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.iam.internal.IAMModule
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.core.user.IUserManager
import kotlinx.coroutines.delay
import java.util.*

class OneSignalImp() : IOneSignal, IServiceProvider {
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
    override val user: IUserManager get() = if(isInitialized) _user!! else throw Exception("Must call 'initWithContext' before use")
    override val iam: IIAMManager get() = if(isInitialized) _iam!! else throw Exception("Must call 'initWithContext' before use")

    // Services required by this class
    private var _user: IUserManager? = null
    private var _userSwitcher: IUserSwitcher? = null
    private var _iam: IIAMManager? = null
    private var _location: ILocationManager? = null
    private var _notifications: INotificationsManager? = null
    private var _identityModelStore: IdentityModelStore? = null
    private var _propertiesModelStore: PropertiesModelStore? = null
    private var _operationRepo: IOperationRepo? = null

    // Other State
    private val _services: ServiceProvider
    private var _userConflictResolverNotifier: ICallbackProducer<IUserIdentityConflictResolver> = CallbackProducer()
    private var _configModel: ConfigModel? = null
    private var _sessionModel: SessionModel? = null
    private var _requiresPrivacyConsent: Boolean? = null

    init {
        var serviceBuilder = ServiceBuilder()

        CoreModule.register(serviceBuilder)
        NotificationModule.register(serviceBuilder)
        LocationModule.register(serviceBuilder)
        IAMModule.register(serviceBuilder)

        _services = serviceBuilder.build()
    }

    override fun initWithContext(context: Context) {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)");

        // start the application service. This is called explicitly first because we want
        // to make sure it has the context provided on input, for all other startable services
        // to depend on if needed.
        (_services.getService<IApplicationService>() as ApplicationService).start(context)

        // get the current config model, if there is one
        _configModel = _services.getService<ConfigModelStore>().get()
        _sessionModel = _services.getService<SessionModelStore>().get()

        // if privacy consent was set prior to init, set it in the model now
        if(_requiresPrivacyConsent != null)
            _configModel!!.requiresPrivacyConsent = _requiresPrivacyConsent!!

        for(bootstrapService in _services.getAllServices<IBootstrapService>()) {
            bootstrapService.bootstrap()
        }

        // "Inject" the services required by this main class
        _location = _services.getService()
        _user = _services.getService()
        _userSwitcher = _services.getService()
        _iam = _services.getService()
        _notifications = _services.getService()
        _propertiesModelStore = _services.getService()
        _identityModelStore = _services.getService()
        _operationRepo = _services.getService()

        isInitialized = true

        if(_appId != null) {
            setupApplication()
        }
    }

    private var _appId: String? = null

    override fun setAppId(appId: String) {
        Logging.log(LogLevel.DEBUG, "setAppId(appId: $appId)");

        _appId = appId

        if(isInitialized) {
            setupApplication()
        }
    }

    private fun setupApplication() {
        _configModel!!.appId = _appId!!;

        // enqueue an operation to retrieve the config from the backend and refresh the config if necessary
        _operationRepo!!.enqueue(BootstrapOperation(_appId!!))

        isInitialized = true
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun login(externalId: String, externalIdHash: String?): IUserManager {
        Logging.log(
            LogLevel.DEBUG,
            "login(externalId: $externalId, externalIdHash: $externalIdHash)"
        );

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

        if(identityModel == null) {
            identityModel = IdentityModel()
            identityModel.id = UUID.randomUUID().toString()
            identityModel.userId = externalId
            identityModel.userIdAuthHash = externalIdHash
            _identityModelStore!!.add(externalId, identityModel)
        }

        if(propertiesModel == null) {
            propertiesModel = PropertiesModel()
            propertiesModel.id = identityModel.id
            _propertiesModelStore!!.add(externalId, propertiesModel)
        }

        retIdentityModel = identityModel
        retPropertiesModel = propertiesModel
        // TODO: Add repo op to create user on the backend.

        if(retIdentityModel != null && retPropertiesModel != null) {
            // TODO: Clear anything else out?
            // TODO: Unhook the old models from the operation store, any changes to them are no longer applicable.
            // TODO: Hook the new models to the operation store, any changes to these are applicable.
            _userSwitcher!!.setUser(retIdentityModel, retPropertiesModel)
        }

        return user
    }

    override suspend fun loginGuest(): IUserManager {
        Logging.log(LogLevel.DEBUG, "loginGuest()");

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

        return user;
    }

    override fun setUserConflictResolver(handler: IUserIdentityConflictResolver?) {
        Logging.log(LogLevel.DEBUG, "setUserConflictResolver(handler: $handler)");
        _userConflictResolverNotifier.set(handler)
    }

    override fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) {
        Logging.log(LogLevel.DEBUG, "setUserConflictResolver(handler: $handler)");
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