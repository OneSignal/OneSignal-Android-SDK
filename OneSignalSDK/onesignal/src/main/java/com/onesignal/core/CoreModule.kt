package com.onesignal.core

import com.onesignal.common.modules.IModule
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.backend.impl.ParamsBackendService
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.core.internal.background.impl.BackgroundManager
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.config.impl.ConfigModelStoreListener
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.database.impl.DatabaseProvider
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.device.impl.DeviceService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.http.impl.HttpClient
import com.onesignal.core.internal.http.impl.HttpConnectionFactory
import com.onesignal.core.internal.http.impl.IHttpConnectionFactory
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.language.impl.LanguageContext
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.impl.PreferencesService
import com.onesignal.core.internal.purchases.impl.TrackAmazonPurchase
import com.onesignal.core.internal.purchases.impl.TrackGooglePurchase
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.startup.StartupService
import com.onesignal.core.internal.time.ITime
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.iam.IIAMManager
import com.onesignal.iam.internal.MisconfiguredIAMManager
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.MisconfiguredLocationManager
import com.onesignal.notification.INotificationsManager
import com.onesignal.notification.internal.MisconfiguredNotificationsManager

internal class CoreModule : IModule {
    override fun register(builder: ServiceBuilder) {
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

        // Language
        builder.register<LanguageContext>().provides<ILanguageContext>()

        // Background
        builder.register<BackgroundManager>()
            .provides<IBackgroundManager>()
            .provides<IStartableService>()

        // Purchase Tracking
        builder.register<TrackAmazonPurchase>().provides<IStartableService>()
        builder.register<TrackGooglePurchase>().provides<IStartableService>()

        // Register dummy services in the event they are not configured. These dummy services
        // will throw an error message if the associated functionality is attempted to be used.
        builder.register<MisconfiguredNotificationsManager>().provides<INotificationsManager>()
        builder.register<MisconfiguredIAMManager>().provides<IIAMManager>()
        builder.register<MisconfiguredLocationManager>().provides<ILocationManager>()
    }
}
