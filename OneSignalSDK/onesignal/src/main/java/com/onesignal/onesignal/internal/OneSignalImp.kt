package com.onesignal.onesignal.internal

import android.content.Context
import kotlinx.coroutines.*

import com.onesignal.onesignal.AppEntryAction
import com.onesignal.onesignal.IOneSignal
import com.onesignal.onesignal.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.internal.iam.IAMManager
import com.onesignal.onesignal.internal.location.LocationManager
import com.onesignal.onesignal.internal.modeling.*
import com.onesignal.onesignal.internal.modeling.CachingModelStore
import com.onesignal.onesignal.internal.modeling.FileModelStore
import com.onesignal.onesignal.internal.modeling.MemoryModelStore
import com.onesignal.onesignal.internal.models.*
import com.onesignal.onesignal.internal.push.NotificationsManager
import com.onesignal.onesignal.internal.user.UserManager
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.onesignal.user.Identity
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging
import com.onesignal.onesignal.notification.INotificationsManager

class OneSignalImp() : IOneSignal {
    override val sdkVersion: String = "050000"

    // SDK state that is persisted by the SDK
    override var requiresPrivacyConsent: Boolean
        get() = _configModel.requiresPrivacyConsent
        set(value) { _configModel.requiresPrivacyConsent = value }

    // Component Services
    override val notifications: INotificationsManager get() = _notifications
    override val location: ILocationManager get() = _location
    override val user: IUserManager get() = _user
    override val iam: IIAMManager get() = _iam

    override val inForeground: Boolean = false
    override val appEntryState: AppEntryAction = AppEntryAction.APP_CLOSE

    private var _userConflictResolver: IUserIdentityConflictResolver? = null

    private val _user: UserManager
    private val _iam: IAMManager
    private val _location: LocationManager
    private val _notifications: NotificationsManager

//    private val userModelStore: IModelStore<UserModel> = CachingModelStore<UserModel>(
//        MemoryModelStore<UserModel>(), FileModelStore<UserModel>()
//    )
//    private val configModelStore: ISingletonModelStore<ConfigModel> = SingletonModelStore("config", CachingModelStore<ConfigModel>(MemoryModelStore<ConfigModel>(), FileModelStore<ConfigModel>()))
//    private val sessionModelStore: ISingletonModelStore<SessionModel> = SingletonModelStore("session", CachingModelStore<SessionModel>(MemoryModelStore<SessionModel>(), FileModelStore<SessionModel>()))

    private var _configModel: ConfigModel = ConfigModel()

    init {
        _user = UserManager()
        _iam = IAMManager()
        _notifications = NotificationsManager()
        _location = LocationManager()
    }
    override suspend fun initWithContext(context: Context) {
        Logging.log(LogLevel.DEBUG, "initWithContext(context: $context)");
        //TODO:  Attempt to load config model from disk
        //       * if there is one, load config from disk (and potentially merge what may have already been done?)
        //       * if there isn't one, build from scratch
        //       Start all the things
    }

    override suspend fun setAppId(appId: String) {
        Logging.log(LogLevel.DEBUG, "setAppId(appId: $appId)");
        //TODO:  Retrieve appId settings from backend
        //       * if exists switch context to new AppId. This can happen at any time, there could be outstanding operations from the previous app.
        //       * If doesn't exist, throw exception?
        _configModel.appId = appId;
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun login(identity: Identity): IUserManager {
        Logging.log(LogLevel.DEBUG, "login(identity: $identity)");
        val identityModel = IdentityModel()
        val propertiesModel = PropertiesModel()

        if(identity is Identity.Known)
        {
            // TODO: Attempt to retrieve user from backend,
            //       * if exists set identityModel aliases and oneSignalId, and all of properties model.
            //       * If doesn't exist create user?
            //       * If invalid auth hash/user throw exception?
            delay(1000L) // Simulate call to drive suspension

            identityModel.userId = identity.externalId
            identityModel.userIdAuthHash = identity.authHash
        }
        else if(identity is Identity.Anonymous)
        {
            // TODO: Is there anything specific to do here?
        }

        // TODO: Clear anything else out?
        // TODO: Unhook the old models from the operation store, any changes to them are no longer applicable.
        // TODO: Hook the new models to the operation store, any changes to these are applicable.
        _user.setUser(identityModel, propertiesModel)
        return user;
    }

    override fun setUserConflictResolver(handler: IUserIdentityConflictResolver?) {
        Logging.log(LogLevel.DEBUG, "setUserConflictResolver(handler: $handler)");
        //TODO("Not yet implemented")
    }

    override fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) {
        Logging.log(LogLevel.DEBUG, "setUserConflictResolver(handler: $handler)");
        //TODO("Not yet implemented")
    }
}
