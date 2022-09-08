package com.onesignal.core.internal

import android.content.Context
import com.onesignal.core.debug.IDebugManager
import com.onesignal.core.IOneSignal
import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
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
import com.onesignal.core.internal.operations.CreateUserOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.service.IServiceProvider
import com.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.core.internal.service.ServiceProvider
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.core.internal.user.IUserSwitcher
import com.onesignal.core.user.IUserManager
import com.onesignal.iam.IIAMManager
import com.onesignal.iam.internal.IAMModule
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.LocationModule
import com.onesignal.notification.INotificationsManager
import com.onesignal.notification.internal.NotificationModule
import com.onesignal.notification.internal.pushtoken.IPushTokenManager
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
    private var _pushTokenManager: IPushTokenManager? = null
    private var _operationRepo: IOperationRepo? = null
    private var _identityModelStore: IdentityModelStore? = null
    private var _propertiesModelStore: PropertiesModelStore? = null
    private var _startupService: StartupService? = null

    // Other State
    private val _services: ServiceProvider
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

    override fun initWithContext(context: Context, appId: String?) {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context, appId: $appId)")

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

        _configModel!!.appId = appId
        // if privacy consent was set prior to init, set it in the model now
        if (_requiresPrivacyConsent != null)
            _configModel!!.requiresPrivacyConsent = _requiresPrivacyConsent!!

        // "Inject" the services required by this main class
        _location = _services.getService()
        _user = _services.getService()
        _userSwitcher = _services.getService()
        _iam = _services.getService()
        _notifications = _services.getService()
        _pushTokenManager = _services.getService()
        _operationRepo = _services.getService()
        _propertiesModelStore = _services.getService()
        _identityModelStore = _services.getService()

        // Instantiate and call the IStartableServices
        _startupService = _services.getService()
        _startupService!!.start()

        isInitialized = true
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun login(externalId: String, jwtBearerToken: String?) {
        Logging.log(LogLevel.DEBUG, "login(externalId: $externalId, jwtBearerToken: $jwtBearerToken)")

        if (!isInitialized)
            throw Exception("Must call 'initWithContext' before use")

        val sdkId = UUID.randomUUID().toString()
        val identityModel = IdentityModel()
        identityModel.id = sdkId
        identityModel.userId = externalId
        identityModel.jwtBearerToken = jwtBearerToken
        _identityModelStore!!.add(externalId, identityModel, fireEvent = false)

        val propertiesModel = PropertiesModel()
        propertiesModel.id = sdkId
        _propertiesModelStore!!.add(externalId, propertiesModel, fireEvent = false)

        // This makes the user effective even though we haven't made the backend call yet. This is
        // to cover a window where the developer (or user) might not wait for this login method to return
        // before using the `.user` property.
        _userSwitcher!!.setUser(identityModel, propertiesModel)

        // TODO: Attempt to retrieve/create user from backend,
        //       * Update the identityModel and propertyModel with the response in a way that won't
        //         drive a change being pushed back up.
        delay(1000L) // Simulate call to drive suspension

        //TODO: Remove this dummy code which mimics what we'll do -- set the models from the backend
        identityModel.set(IdentityModel::oneSignalId.name, UUID.randomUUID(), notify = false)
        propertiesModel.set(PropertiesModel::tags.name, mapOf("foo" to "bar"), notify = false)
    }

    override fun logout() {
        Logging.log(LogLevel.DEBUG, "logout()")

        if (!isInitialized)
            throw Exception("Must call 'initWithContext' before use")

        val sdkId = UUID.randomUUID().toString()
        var identityModel = IdentityModel()
        identityModel.id = sdkId
        _identityModelStore!!.add(sdkId, identityModel, fireEvent = false)

        var propertiesModel = PropertiesModel()
        propertiesModel.id = sdkId
        _propertiesModelStore!!.add(sdkId, propertiesModel, fireEvent = false)

        _userSwitcher!!.setUser(identityModel, propertiesModel)

        // Send the user create along with the device push token up so the new device-scoped user can
        // be created and claim this device and "steal" the push subscription.
        // TODO: Do we still need to create the subscription locally to represent that this
        //       user it.  But we don't know the subscription ID until *after* it has been sent up (will be returned
        //       as part of the response).
        _operationRepo!!.enqueue(CreateUserOperation(_configModel!!.appId!!, sdkId, _pushTokenManager!!.pushToken))
    }

    override fun <T> hasService(c: Class<T>): Boolean = _services.hasService(c)
    override fun <T> getService(c: Class<T>): T = _services.getService(c)
    override fun <T> getServiceOrNull(c: Class<T>): T? = _services.getServiceOrNull(c)
    override fun <T> getAllServices(c: Class<T>): List<T> = _services.getAllServices(c)
}
