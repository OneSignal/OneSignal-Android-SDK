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
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.device.impl.DeviceService
import com.onesignal.core.internal.device.impl.InstallIdService
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
import com.onesignal.core.internal.purchases.impl.TrackGooglePurchase
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.debug.internal.crash.IOneSignalCrashReporter
import com.onesignal.debug.internal.crash.OneSignalCrashHandler
import com.onesignal.debug.internal.logging.otel.crash.IOneSignalCrashConfigProvider
import com.onesignal.debug.internal.logging.otel.IOneSignalOpenTelemetry
import com.onesignal.debug.internal.logging.otel.IOneSignalOpenTelemetryCrash
import com.onesignal.debug.internal.logging.otel.IOneSignalOpenTelemetryRemote
import com.onesignal.debug.internal.logging.otel.crash.OneSignalCrashConfigProvider
import com.onesignal.debug.internal.logging.otel.crash.OneSignalCrashReporterOtel
import com.onesignal.debug.internal.logging.otel.crash.OneSignalCrashUploader
import com.onesignal.debug.internal.logging.otel.OneSignalOpenTelemetryCrashLocal
import com.onesignal.debug.internal.logging.otel.OneSignalOpenTelemetryRemote
import com.onesignal.debug.internal.logging.otel.attributes.OneSignalOtelFieldsPerEvent
import com.onesignal.debug.internal.logging.otel.attributes.OneSignalOtelFieldsTopLevel
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.inAppMessages.internal.MisconfiguredIAMManager
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.MisconfiguredLocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.internal.MisconfiguredNotificationsManager

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
        builder.register<InstallIdService>().provides<IInstallIdService>()

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
        builder.register<TrackGooglePurchase>().provides<IStartableService>()

        // Remote Crash and error logging
        builder.register<OneSignalOpenTelemetryRemote>().provides<IOneSignalOpenTelemetry>()
        builder.register<OneSignalCrashReporterOtel>().provides<IOneSignalCrashReporter>()
        builder.register<OneSignalOpenTelemetryRemote>().provides<IOneSignalOpenTelemetryRemote>()

        builder.register<OneSignalOpenTelemetryCrashLocal>().provides<IOneSignalOpenTelemetryCrash>()
        builder.register<OneSignalCrashConfigProvider>().provides<IOneSignalCrashConfigProvider>()

        builder.register<OneSignalCrashHandler>().provides<OneSignalCrashHandler>()
        builder.register<OneSignalCrashUploader>().provides<IStartableService>()

        builder.register<OneSignalOtelFieldsTopLevel>().provides<OneSignalOtelFieldsTopLevel>()
        builder.register<OneSignalOtelFieldsPerEvent>().provides<OneSignalOtelFieldsPerEvent>()

        // Register dummy services in the event they are not configured. These dummy services
        // will throw an error message if the associated functionality is attempted to be used.
        builder.register<MisconfiguredNotificationsManager>().provides<INotificationsManager>()
        builder.register<MisconfiguredIAMManager>().provides<IInAppMessagesManager>()
        builder.register<MisconfiguredLocationManager>().provides<ILocationManager>()
    }
}
