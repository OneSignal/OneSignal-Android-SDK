package com.onesignal.onesignal.core.internal

import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.onesignal.core.internal.backend.ParamsBackendService
import com.onesignal.onesignal.core.internal.backend.http.HttpClient
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.onesignal.core.internal.background.impl.BackgroundManager
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.DatabaseProvider
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.device.impl.DeviceService
import com.onesignal.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.onesignal.core.internal.influence.impl.InfluenceManager
import com.onesignal.onesignal.core.internal.listeners.IdentityModelStoreListener
import com.onesignal.onesignal.core.internal.listeners.PropertiesModelStoreListener
import com.onesignal.onesignal.core.internal.listeners.SubscriptionModelStoreListener
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.onesignal.core.internal.models.SessionModelStore
import com.onesignal.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.onesignal.core.internal.models.TriggerModelStore
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.operations.OperationRepo
import com.onesignal.onesignal.core.internal.operations.executors.PropertyOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor
import com.onesignal.onesignal.core.internal.operations.executors.UserOperationExecutor
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsCache
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsFactory
import com.onesignal.onesignal.core.internal.outcomes.OutcomeEventsController
import com.onesignal.onesignal.core.internal.outcomes.impl.OSOutcomeEventsCache
import com.onesignal.onesignal.core.internal.outcomes.impl.OSOutcomeEventsFactory
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.params.IWriteableParamsService
import com.onesignal.onesignal.core.internal.params.ParamsService
import com.onesignal.onesignal.core.internal.params.RefreshParamsService
import com.onesignal.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferencesService
import com.onesignal.onesignal.core.internal.purchases.TrackAmazonPurchase
import com.onesignal.onesignal.core.internal.purchases.TrackGooglePurchase
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.session.ISessionService
import com.onesignal.onesignal.core.internal.session.SessionService
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.core.internal.startup.StartupService
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.core.internal.time.Time
import com.onesignal.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.onesignal.core.internal.user.IUserSwitcher
import com.onesignal.onesignal.core.internal.user.SubscriptionManager
import com.onesignal.onesignal.core.internal.user.UserManager
import com.onesignal.onesignal.core.user.IUserManager

object CoreModule {
    fun register(builder: ServiceBuilder) {
        // Low Level Services
        builder.register<PreferencesService>()
            .provides<IPreferencesService>()
            .provides<IStartableService>()
        builder.register<HttpClient>().provides<IHttpClient>()
        builder.register<ApplicationService>().provides<IApplicationService>()
        builder.register<DeviceService>().provides<IDeviceService>()
        builder.register<Time>().provides<ITime>()
        builder.register<DatabaseProvider>().provides<IDatabaseProvider>()
        builder.register<StartupService>().provides<StartupService>()

        // Params (Config)
        builder.register<ParamsService>()
            .provides<IParamsService>()
            .provides<IWriteableParamsService>()
        builder.register<ParamsBackendService>().provides<IParamsBackendService>()
        builder.register<RefreshParamsService>().provides<IStartableService>()

        // Operations
        builder.register<OperationRepo>()
            .provides<IOperationRepo>()
            .provides<IStartableService>()
        builder.register<PropertyOperationExecutor>().provides<IOperationExecutor>()
        builder.register<SubscriptionOperationExecutor>().provides<IOperationExecutor>()
        builder.register<UserOperationExecutor>().provides<IOperationExecutor>()

        // Permissions
        builder.register<RequestPermissionService>()
            .provides<RequestPermissionService>()
            .provides<IRequestPermissionService>()

        // Outcomes
        builder.register<OutcomeEventsController>().provides<OutcomeEventsController>()
        builder.register<OSOutcomeEventsFactory>().provides<IOutcomeEventsFactory>()
        builder.register<OSOutcomeEventsCache>().provides<IOutcomeEventsCache>()

        // Influence
        builder.register<InfluenceManager>().provides<IInfluenceManager>()

        // Session
        builder.register<SessionService>()
            .provides<ISessionService>()
            .provides<IStartableService>()

        // Background
        builder.register<BackgroundManager>()
            .provides<IBackgroundManager>()
            .provides<IStartableService>()

        // Model Stores
        builder.register<IdentityModelStore>().provides<IdentityModelStore>()
        builder.register<PropertiesModelStore>().provides<PropertiesModelStore>()
        builder.register<SubscriptionModelStore>().provides<SubscriptionModelStore>()
        builder.register<TriggerModelStore>().provides<TriggerModelStore>()
        builder.register<ConfigModelStore>().provides<ConfigModelStore>()
        builder.register<SessionModelStore>().provides<SessionModelStore>()

        // Model Store -> Operation Listeners
        builder.register<IdentityModelStoreListener>().provides<IStartableService>()
        builder.register<SubscriptionModelStoreListener>().provides<IStartableService>()
        builder.register<PropertiesModelStoreListener>().provides<IStartableService>()

        // Purchase Tracking
        builder.register<TrackAmazonPurchase>().provides<IStartableService>()
        builder.register<TrackGooglePurchase>().provides<IStartableService>()

        // User
        builder.register<SubscriptionManager>().provides<ISubscriptionManager>()
        builder.register<UserManager>()
            .provides<IUserManager>()
            .provides<IUserSwitcher>()
    }
}
