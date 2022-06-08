package com.onesignal.onesignal.internal

import android.content.Context
import com.onesignal.onesignal.internal.models.ConfigModel
import com.onesignal.models.modeling.*
import com.onesignal.models.modeling.CachingModelStore
import com.onesignal.models.modeling.FileModelStore
import com.onesignal.models.modeling.MemoryModelStore
import com.onesignal.models.modeling.SingletonModelStore
import com.onesignal.onesignal.AppEntryAction
import com.onesignal.onesignal.IOneSignal
import com.onesignal.onesignal.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.internal.iam.IAMManager
import com.onesignal.onesignal.internal.location.LocationManager
import com.onesignal.onesignal.internal.models.SessionModel
import com.onesignal.onesignal.internal.models.UserModel
import com.onesignal.onesignal.internal.push.NotificationsManager
import com.onesignal.onesignal.internal.user.UserManager
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.onesignal.user.Identity
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.INotificationsManager

class OneSignalImp() : IOneSignal {
    override val sdkVersion: String = "050000"
    override val notifications: INotificationsManager = NotificationsManager()
    override val location: ILocationManager = LocationManager()
    override val user: IUserManager = UserManager()
    override val inForeground: Boolean = false
    override val appEntryState: AppEntryAction = AppEntryAction.APP_CLOSE
    override var iam: IIAMManager = IAMManager()
    override var requiresPrivacyConsent: Boolean = false
    override var userConflictResolver: IUserIdentityConflictResolver? = null

    private val userModelStore: IModelStore<UserModel> = CachingModelStore<UserModel>(MemoryModelStore<UserModel>(), FileModelStore<UserModel>())
    private val configModelStore: ISingletonModelStore<ConfigModel> = SingletonModelStore("config", CachingModelStore<ConfigModel>(MemoryModelStore<ConfigModel>(), FileModelStore<ConfigModel>()))
    private val sessionModelStore: ISingletonModelStore<SessionModel> = SingletonModelStore("session", CachingModelStore<SessionModel>(MemoryModelStore<SessionModel>(), FileModelStore<SessionModel>()))

    override suspend fun initWithContext(context: Context) {
        TODO("Not yet implemented")
    }

    override suspend fun setAppId(appId: String) {
        TODO("Not yet implemented")
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override suspend fun loginAsync(identity: Identity): IUserManager {
        TODO("Not yet implemented")
    }
}
