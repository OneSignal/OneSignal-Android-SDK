package com.onesignal.onesignal.core.internal

import android.content.Context
import com.onesignal.onesignal.core.IOneSignal
import com.onesignal.onesignal.core.internal.application.ApplicationService
import com.onesignal.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.onesignal.core.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.core.internal.modeling.*
import com.onesignal.onesignal.core.internal.models.*
import com.onesignal.onesignal.core.internal.operations.GetConfigOperation
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
import com.onesignal.onesignal.core.user.Identity
import kotlinx.coroutines.delay
import java.util.*

class OneSignalImp() : IOneSignal, IServiceProvider {
    override val sdkVersion: String = "050000"
    override var isInitialized: Boolean = false

    override var requiresPrivacyConsent: Boolean
        get() = _requiresPrivacyConsent == true
        set(value) {
            _requiresPrivacyConsent = value
            _configModel?.requiresPrivacyConsent = value
        }

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
    private var _identityModelStore: IModelStore<IdentityModel>? = null
    private var _propertiesModelStore: IModelStore<PropertiesModel>? = null
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
        _services.getService<ApplicationService>().start(context)

        // Start any startable services
        for(startableService in _services.getAllServices<IStartableService>()) {
            startableService.start()
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

        // get the current config model, if there is one
        _configModel = _services.getService<ISingletonModelStore<ConfigModel>>().get()
        _sessionModel = _services.getService<ISingletonModelStore<SessionModel>>().get()

        // if privacy consent was set prior to init, set it in the model now
        if(_requiresPrivacyConsent != null)
            _configModel!!.requiresPrivacyConsent = _requiresPrivacyConsent!!

        // enqueue an operation to retrieve the config from the backend and refresh the config if necessary
        // TODO: Should this happen within the call?
        _operationRepo!!.enqueue(GetConfigOperation())

        isInitialized = true
    }

    override suspend fun setAppId(appId: String) {
        Logging.log(LogLevel.DEBUG, "setAppId(appId: $appId)");

        //TODO:  Retrieve appId settings from backend
        //       * if exists switch context to new AppId. This can happen at any time, there could be outstanding operations from the previous app.
        //       * If doesn't exist, throw exception?
        delay(1000L) // Simulate call to drive suspension

        _configModel?.appId = appId;
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun login(identity: Identity): IUserManager {
        Logging.log(LogLevel.DEBUG, "login(identity: $identity)");

        if (!isInitialized)
            throw Exception("Must call 'initWithContext' before use")

        var retIdentityModel: IdentityModel? = null
        var retPropertiesModel: PropertiesModel? = null

        when (identity) {
            is Identity.Known -> {
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
                var identityModel = _identityModelStore!!.get(identity.externalId)
                var propertiesModel = _propertiesModelStore!!.get(identity.externalId)

                if(identityModel == null) {
                    identityModel = IdentityModel()
                    identityModel.id = UUID.randomUUID().toString()
                    identityModel.userId = identity.externalId
                    identityModel.userIdAuthHash = identity.authHash
                    _identityModelStore!!.add(identity.externalId, identityModel)
                }

                if(propertiesModel == null) {
                    propertiesModel = PropertiesModel()
                    propertiesModel.id = identityModel.id
                    _propertiesModelStore!!.add(identity.externalId, propertiesModel)
                }

                retIdentityModel = identityModel
                retPropertiesModel = propertiesModel
                // TODO: Add repo op to create user on the backend.
            }
            is Identity.Anonymous -> {
                retIdentityModel = IdentityModel()
                retPropertiesModel = PropertiesModel()

                // TODO: Do we do anything else?  Save the anonymous user in the store?  Save the anonymous user in the backend?
                delay(1000L) // Simulate call to drive suspension
            }
            else -> {
                throw Exception("Unrecognized identity type: " + identity.javaClass.name)
            }
        }

        if(retIdentityModel != null && retPropertiesModel != null) {
            // TODO: Clear anything else out?
            // TODO: Unhook the old models from the operation store, any changes to them are no longer applicable.
            // TODO: Hook the new models to the operation store, any changes to these are applicable.
            _userSwitcher!!.setUser(retIdentityModel, retPropertiesModel)
        }
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