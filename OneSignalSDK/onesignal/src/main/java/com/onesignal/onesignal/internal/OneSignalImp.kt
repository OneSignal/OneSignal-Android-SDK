package com.onesignal.onesignal.internal

import android.content.Context
import com.onesignal.onesignal.IOneSignal
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.internal.application.ApplicationService
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.backend.api.ApiService
import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.backend.http.HttpClient
import com.onesignal.onesignal.internal.backend.http.IHttpClient
import com.onesignal.onesignal.internal.common.events.CallbackProducer
import com.onesignal.onesignal.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.internal.device.DeviceService
import com.onesignal.onesignal.internal.device.IDeviceService
import com.onesignal.onesignal.internal.iam.IAMManager
import com.onesignal.onesignal.internal.listeners.IdentityModelStoreListener
import com.onesignal.onesignal.internal.listeners.PropertiesModelStoreListener
import com.onesignal.onesignal.internal.listeners.SubscriptionModelStoreListener
import com.onesignal.onesignal.internal.location.LocationManager
import com.onesignal.onesignal.internal.modeling.*
import com.onesignal.onesignal.internal.models.*
import com.onesignal.onesignal.internal.notification.NotificationsManager
import com.onesignal.onesignal.internal.operations.GetConfigOperation
import com.onesignal.onesignal.internal.operations.IOperationRepo
import com.onesignal.onesignal.internal.operations.OperationRepo
import com.onesignal.onesignal.internal.params.IParamsService
import com.onesignal.onesignal.internal.params.ParamsService
import com.onesignal.onesignal.internal.session.ISessionService
import com.onesignal.onesignal.internal.session.SessionService
import com.onesignal.onesignal.internal.user.UserManager
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.onesignal.user.Identity
import kotlinx.coroutines.delay
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class OneSignalImp() : IOneSignal, IServiceProvider {
    override val sdkVersion: String = "050000"

    override var requiresPrivacyConsent: Boolean
        get() = _configModel?.requiresPrivacyConsent == true
        set(value) { _configModel?.requiresPrivacyConsent = value }

    override val notifications: INotificationsManager get() = _notifications
    override val location: ILocationManager get() = _location
    override val user: IUserManager get() = _user
    override val iam: IIAMManager get() = _iam

    // Services
    private val _serviceMap: MutableMap<Class<*>, Any> = mutableMapOf()

    private val _user: UserManager
    private val _iam: IAMManager
    private val _location: LocationManager
    private val _notifications: NotificationsManager
    private val _operationRepo: OperationRepo
    private val _api: IApiService
    private val _httpClient: IHttpClient
    private val _sessionService: SessionService
    private val _identityModelListener: IdentityModelStoreListener
    private val _subscriptionModelListener: SubscriptionModelStoreListener
    private val _propertiesModelListener: PropertiesModelStoreListener
    private val _identityModelStore: IModelStore<IdentityModel>
    private val _propertiesModelStore: IModelStore<PropertiesModel>
    private val _subscriptionModelStore: IModelStore<SubscriptionModel>
    private val _configModelStore: ISingletonModelStore<ConfigModel>
    private val _sessionModelStore: ISingletonModelStore<SessionModel>
    private val _applicationService: ApplicationService
    private val _deviceService: DeviceService
    private val _paramsService: ParamsService

    // Other State

    private var _appEntryState: AppEntryAction = AppEntryAction.APP_CLOSE
    private var _userConflictResolverNotifier: ICallbackProducer<IUserIdentityConflictResolver> = CallbackProducer()
    private var _configModel: ConfigModel? = null
    private var _sessionModel: SessionModel? = null

    init {
        _identityModelStore = ModelStore()
        _serviceMap[typeOf<IModelStore<IdentityModel>>().javaClass] = _identityModelStore

        _propertiesModelStore = ModelStore()
        _serviceMap[typeOf<IModelStore<PropertiesModel>>().javaClass] = _propertiesModelStore

        _configModelStore = SingletonModelStore("config", { ConfigModel() }, ModelStore())
        _serviceMap[typeOf<ISingletonModelStore<ConfigModel>>().javaClass] = _configModelStore

        _sessionModelStore = SingletonModelStore("session", { SessionModel() }, ModelStore())
        _serviceMap[typeOf<ISingletonModelStore<SessionModel>>().javaClass] = _sessionModelStore

        _subscriptionModelStore = ModelStore()
        _serviceMap[typeOf<IModelStore<SubscriptionModel>>().javaClass] = _subscriptionModelStore

        _operationRepo = OperationRepo()
        _serviceMap[typeOf<IOperationRepo>().javaClass] = _operationRepo

        _httpClient = HttpClient()
        _serviceMap[typeOf<IHttpClient>().javaClass] = _httpClient

        _api = ApiService(_httpClient)
        _serviceMap[typeOf<IApiService>().javaClass] = _api

        _identityModelListener = IdentityModelStoreListener(_identityModelStore, _operationRepo, _api)
        _subscriptionModelListener = SubscriptionModelStoreListener(_subscriptionModelStore, _operationRepo, _api)
        _propertiesModelListener = PropertiesModelStoreListener(_propertiesModelStore, _operationRepo, _api)

        _applicationService = ApplicationService()
        _serviceMap[typeOf<IApplicationService>().javaClass] = _applicationService

        _deviceService = DeviceService(_applicationService)
        _serviceMap[typeOf<IDeviceService>().javaClass] = _deviceService

        _sessionService = SessionService(_applicationService)
        _serviceMap[typeOf<ISessionService>().javaClass] = _sessionService

        _paramsService = ParamsService()
        _serviceMap[typeOf<IParamsService>().javaClass] = _paramsService

        // These are already accessible via IOneSignal, no need to add them to the service map.
        _location = LocationManager(_applicationService, _deviceService)
        _user = UserManager(_subscriptionModelStore)
        _iam = IAMManager()
        _notifications = NotificationsManager(_deviceService, _applicationService, _paramsService)
    }

    override suspend fun initWithContext(context: Context) {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)");

        // get the current config model, if there is one
        _configModel = _configModelStore.get()
        _sessionModel = _sessionModelStore.get()

        // start services
        _applicationService.start(context)
        _operationRepo.start()
        _sessionService.start(_configModel!!, _sessionModel!!)
        _location.start()

        // enqueue an operation to retrieve the config from the backend and refresh the config if necessary
        // TODO: Should this happen within the call?
        _operationRepo.enqueue(GetConfigOperation(_api))

        delay(1000L) // TODO: temporary to simulate suspension call
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
                var identityModel = _identityModelStore.get(identity.externalId)
                var propertiesModel = _propertiesModelStore.get(identity.externalId)

                if(identityModel == null) {
                    identityModel = IdentityModel()
                    identityModel.id = UUID.randomUUID().toString()
                    identityModel.userId = identity.externalId
                    identityModel.userIdAuthHash = identity.authHash
                    _identityModelStore.add(identity.externalId, identityModel)
                }

                if(propertiesModel == null) {
                    propertiesModel = PropertiesModel()
                    propertiesModel.id = identityModel.id
                    _propertiesModelStore.add(identity.externalId, propertiesModel)
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
            _user.setUser(retIdentityModel, retPropertiesModel)
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

    override fun <T> getService(c: Class<T>): T {
        return _serviceMap[c] as T
    }

    override fun <T> getServiceOrNull(c: Class<T>): T? {
        if(_serviceMap.containsKey(c))
            return _serviceMap[c] as T

        return null
    }
}