package com.onesignal.onesignal.core.internal

import com.onesignal.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.api.ApiService
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
import com.onesignal.onesignal.core.internal.models.*
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.operations.OperationRepo
import com.onesignal.onesignal.core.internal.operations.executors.BootstrapExecutor
import com.onesignal.onesignal.core.internal.operations.executors.PropertyOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.UserOperationExecutor
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.params.IWriteableParamsService
import com.onesignal.onesignal.core.internal.params.ParamsService
import com.onesignal.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferencesService
import com.onesignal.onesignal.core.internal.service.IBootstrapService
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.session.ISessionService
import com.onesignal.onesignal.core.internal.session.SessionService
import com.onesignal.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.onesignal.core.internal.user.IUserSwitcher
import com.onesignal.onesignal.core.internal.user.UserManager
import com.onesignal.onesignal.core.user.IUserManager

object CoreModule {
    fun register(builder: ServiceBuilder) {
        // Low Level Services
        builder.register<PreferencesService>()
               .provides<IPreferencesService>()
               .provides<IBootstrapService>()
        builder.register<HttpClient>().provides<IHttpClient>()
        builder.register<ApiService>().provides<IApiService>()
        builder.register<ApplicationService>().provides<IApplicationService>()
        builder.register<DeviceService>().provides<IDeviceService>()
        builder.register<ParamsService>()
               .provides<IParamsService>()
               .provides<IWriteableParamsService>()
        builder.register<Time>().provides<ITime>()

        // Database
        builder.register<DatabaseProvider>().provides<IDatabaseProvider>()

        // Operations
        builder.register<OperationRepo>()
               .provides<IOperationRepo>()
               .provides<IBootstrapService>()
        builder.register<BootstrapExecutor>().provides<IOperationExecutor>()
        builder.register<PropertyOperationExecutor>().provides<IOperationExecutor>()
        builder.register<SubscriptionOperationExecutor>().provides<IOperationExecutor>()
        builder.register<UserOperationExecutor>().provides<IOperationExecutor>()

        // Permissions
        builder.register<RequestPermissionService>()
               .provides<RequestPermissionService>()
               .provides<IRequestPermissionService>()

        // Session
        builder.register<SessionService>().provides<ISessionService>()

        // Model Stores
        builder.register<IdentityModelStore>().provides<IdentityModelStore>()
        builder.register<PropertiesModelStore>().provides<PropertiesModelStore>()
        builder.register<SubscriptionModelStore>().provides<SubscriptionModelStore>()
        builder.register<ConfigModelStore>().provides<ConfigModelStore>()
        builder.register<SessionModelStore>().provides<SessionModelStore>()

        builder.register<IdentityModelStoreListener>().provides<IBootstrapService>()
        builder.register<SubscriptionModelStoreListener>().provides<IBootstrapService>()
        builder.register<PropertiesModelStoreListener>().provides<IBootstrapService>()

        builder.register<UserManager>()
               .provides<IUserManager>()
               .provides<ISubscriptionManager>()
               .provides<IUserSwitcher>()
    }
}