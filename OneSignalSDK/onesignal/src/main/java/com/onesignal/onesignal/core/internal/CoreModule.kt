package com.onesignal.onesignal.core.internal

import com.onesignal.onesignal.core.internal.application.ApplicationService
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.api.IApiService
import com.onesignal.onesignal.core.internal.backend.http.HttpClient
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.core.internal.common.time.Time
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.DatabaseProvider
import com.onesignal.onesignal.core.internal.device.DeviceService
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.listeners.IdentityModelStoreListener
import com.onesignal.onesignal.core.internal.listeners.PropertiesModelStoreListener
import com.onesignal.onesignal.core.internal.listeners.SubscriptionModelStoreListener
import com.onesignal.onesignal.core.internal.modeling.IModelStore
import com.onesignal.onesignal.core.internal.modeling.ISingletonModelStore
import com.onesignal.onesignal.core.internal.modeling.ModelStore
import com.onesignal.onesignal.core.internal.modeling.SingletonModelStore
import com.onesignal.onesignal.core.internal.models.*
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.operations.OperationRepo
import com.onesignal.onesignal.core.internal.operations.executors.ConfigOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.PropertyOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.UserOperationExecutor
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.params.ParamsService
import com.onesignal.onesignal.core.internal.service.IStartableService
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.session.ISessionService
import com.onesignal.onesignal.core.internal.session.SessionService
import com.onesignal.onesignal.core.internal.user.IUserSwitcher
import com.onesignal.onesignal.core.internal.user.UserManager
import com.onesignal.onesignal.core.user.IUserManager

object CoreModule {
    fun register(builder: ServiceBuilder) {
        builder.register<ModelStore<IdentityModel>>().provides<IModelStore<IdentityModel>>()
        builder.register<ModelStore<PropertiesModel>>().provides<IModelStore<PropertiesModel>>()
        builder.register<ModelStore<SubscriptionModel>>().provides<IModelStore<SubscriptionModel>>()
        builder.register<SingletonModelStore<ConfigModel>>().provides<ISingletonModelStore<ConfigModel>>()
        builder.register<SingletonModelStore<SessionModel>>().provides<ISingletonModelStore<SessionModel>>()

        // Operations
        builder.register<OperationRepo>().provides<IOperationRepo>()
        builder.register<ConfigOperationExecutor>().provides<IOperationExecutor>()
        builder.register<PropertyOperationExecutor>().provides<IOperationExecutor>()
        builder.register<SubscriptionOperationExecutor>().provides<IOperationExecutor>()
        builder.register<UserOperationExecutor>().provides<IOperationExecutor>()

        builder.register<HttpClient>().provides<IHttpClient>()
        builder.register<IApiService>().provides<IApiService>()

        builder.register<IdentityModelStoreListener>().provides<IStartableService>()
        builder.register<SubscriptionModelStoreListener>().provides<IStartableService>()
        builder.register<PropertiesModelStoreListener>().provides<IStartableService>()

        builder.register<ApplicationService>().provides<IApplicationService>()
        builder.register<DeviceService>().provides<IDeviceService>()

        builder.register<SessionService>().provides<ISessionService>()
        builder.register<ParamsService>().provides<IParamsService>()

        builder.register<Time>().provides<ITime>()
        builder.register<DatabaseProvider>().provides<IDatabaseProvider>()

        builder.register<UserManager>()
               .provides<IUserManager>()
               .provides<IUserSwitcher>()
    }
}