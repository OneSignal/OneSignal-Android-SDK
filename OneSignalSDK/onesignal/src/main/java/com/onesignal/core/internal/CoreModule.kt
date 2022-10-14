package com.onesignal.core.internal

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.backend.IIdentityBackendService
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.backend.ISubscriptionBackendService
import com.onesignal.core.internal.backend.IUserBackendService
import com.onesignal.core.internal.backend.impl.IdentityBackendService
import com.onesignal.core.internal.backend.impl.ParamsBackendService
import com.onesignal.core.internal.backend.impl.SubscriptionBackendService
import com.onesignal.core.internal.backend.impl.UserBackendService
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.background.impl.BackgroundManager
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.database.impl.DatabaseProvider
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.device.impl.DeviceService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.http.impl.HttpClient
import com.onesignal.core.internal.http.impl.HttpConnectionFactory
import com.onesignal.core.internal.http.impl.IHttpConnectionFactory
import com.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.core.internal.influence.impl.InfluenceManager
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.language.impl.LanguageContext
import com.onesignal.core.internal.listeners.ConfigModelStoreListener
import com.onesignal.core.internal.listeners.IdentityModelStoreListener
import com.onesignal.core.internal.listeners.PropertiesModelStoreListener
import com.onesignal.core.internal.listeners.SessionListener
import com.onesignal.core.internal.listeners.SubscriptionModelStoreListener
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.core.internal.models.SessionModelStore
import com.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.core.internal.models.TriggerModelStore
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.impl.IdentityOperationExecutor
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.operations.impl.SubscriptionOperationExecutor
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import com.onesignal.core.internal.outcomes.IOutcomeEventsController
import com.onesignal.core.internal.outcomes.impl.IOutcomeEventsBackend
import com.onesignal.core.internal.outcomes.impl.IOutcomeEventsPreferences
import com.onesignal.core.internal.outcomes.impl.IOutcomeEventsRepository
import com.onesignal.core.internal.outcomes.impl.OutcomeEventsBackend
import com.onesignal.core.internal.outcomes.impl.OutcomeEventsController
import com.onesignal.core.internal.outcomes.impl.OutcomeEventsPreferences
import com.onesignal.core.internal.outcomes.impl.OutcomeEventsRepository
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferencesService
import com.onesignal.core.internal.purchases.TrackAmazonPurchase
import com.onesignal.core.internal.purchases.TrackGooglePurchase
import com.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.core.internal.session.ISessionService
import com.onesignal.core.internal.session.impl.SessionService
import com.onesignal.core.internal.startup.IBootstrapService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.core.internal.time.ITime
import com.onesignal.core.internal.time.Time
import com.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.core.internal.user.SubscriptionManager
import com.onesignal.core.internal.user.UserManager
import com.onesignal.core.user.IUserManager

internal object CoreModule {
    fun register(builder: ServiceBuilder) {
        // Low Level Services
        builder.register<PreferencesService>()
            .provides<IPreferencesService>()
            .provides<IStartableService>()
        builder.register<HttpConnectionFactory>().provides<IHttpConnectionFactory>()
        builder.register<HttpClient>().provides<IHttpClient>()
        builder.register<ApplicationService>().provides<IApplicationService>()
        builder.register<DeviceService>().provides<IDeviceService>()
        builder.register<Time>().provides<ITime>()
        builder.register<DatabaseProvider>().provides<IDatabaseProvider>()
        builder.register<StartupService>().provides<StartupService>()

        // Params (Config)
        builder.register<ConfigModelStore>().provides<ConfigModelStore>()
        builder.register<ParamsBackendService>().provides<IParamsBackendService>()
        builder.register<ConfigModelStoreListener>().provides<IStartableService>()

        // Operations
        builder.register<OperationModelStore>().provides<OperationModelStore>()
        builder.register<OperationRepo>()
            .provides<IOperationRepo>()
            .provides<IStartableService>()

        // Permissions
        builder.register<RequestPermissionService>()
            .provides<RequestPermissionService>()
            .provides<IRequestPermissionService>()

        // Outcomes
        builder.register<OutcomeEventsPreferences>().provides<IOutcomeEventsPreferences>()
        builder.register<OutcomeEventsRepository>().provides<IOutcomeEventsRepository>()
        builder.register<OutcomeEventsBackend>().provides<IOutcomeEventsBackend>()
        builder.register<OutcomeEventsController>()
            .provides<IOutcomeEventsController>()
            .provides<IStartableService>()

        // Influence
        builder.register<InfluenceManager>().provides<IInfluenceManager>()

        // Language
        builder.register<LanguageContext>().provides<ILanguageContext>()

        // Session
        builder.register<SessionModelStore>().provides<SessionModelStore>()
        builder.register<SessionService>()
            .provides<ISessionService>()
            .provides<IStartableService>()
            .provides<IBackgroundService>()
        builder.register<SessionListener>().provides<IStartableService>()

        // Background
        builder.register<BackgroundManager>()
            .provides<IBackgroundManager>()
            .provides<IStartableService>()

        // Triggers
        builder.register<TriggerModelStore>().provides<TriggerModelStore>()

        // Purchase Tracking
        builder.register<TrackAmazonPurchase>().provides<IStartableService>()
        builder.register<TrackGooglePurchase>().provides<IStartableService>()

        // Properties
        builder.register<PropertiesModelStore>().provides<PropertiesModelStore>()
        builder.register<PropertiesModelStoreListener>().provides<IBootstrapService>()

        // Identity
        builder.register<IdentityModelStore>().provides<IdentityModelStore>()
        builder.register<IdentityModelStoreListener>().provides<IBootstrapService>()
        builder.register<IdentityBackendService>().provides<IIdentityBackendService>()
        builder.register<IdentityOperationExecutor>().provides<IOperationExecutor>()

        // Subscriptions
        builder.register<SubscriptionModelStore>().provides<SubscriptionModelStore>()
        builder.register<SubscriptionModelStoreListener>().provides<IBootstrapService>()
        builder.register<SubscriptionBackendService>().provides<ISubscriptionBackendService>()
        builder.register<SubscriptionOperationExecutor>().provides<IOperationExecutor>()
        builder.register<SubscriptionManager>().provides<ISubscriptionManager>()

        // User
        builder.register<UserBackendService>().provides<IUserBackendService>()
        builder.register<UserOperationExecutor>().provides<IOperationExecutor>()
        builder.register<UserManager>().provides<IUserManager>()
    }
}
