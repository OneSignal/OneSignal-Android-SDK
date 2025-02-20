package com.onesignal.inAppMessages

import android.os.Build
import com.onesignal.common.modules.IModule
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.core.internal.startup.IBootstrapService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.inAppMessages.internal.DummyInAppMessagesManager
import com.onesignal.inAppMessages.internal.InAppMessagesManager
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.backend.impl.InAppBackendService
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.display.impl.InAppDisplayer
import com.onesignal.inAppMessages.internal.hydrators.InAppHydrator
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.lifecycle.impl.IAMLifecycleService
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.preferences.impl.InAppPreferencesController
import com.onesignal.inAppMessages.internal.preview.InAppMessagePreviewHandler
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePromptFactory
import com.onesignal.inAppMessages.internal.repositories.IInAppRepository
import com.onesignal.inAppMessages.internal.repositories.impl.InAppRepository
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerController
import com.onesignal.inAppMessages.internal.triggers.TriggerModelStore
import com.onesignal.inAppMessages.internal.triggers.impl.DynamicTriggerController
import com.onesignal.inAppMessages.internal.triggers.impl.TriggerController

internal class InAppMessagesModule : IModule {
    override fun register(builder: ServiceBuilder) {
        // Make sure only Android 4.4 devices and higher can use IAMs
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            builder.register<DummyInAppMessagesManager>().provides<IInAppMessagesManager>()
        } else {
            // Low level services
            builder.register<InAppStateService>().provides<InAppStateService>()
            builder.register<InAppHydrator>().provides<InAppHydrator>()
            builder.register<InAppPreferencesController>().provides<IInAppPreferencesController>()
            builder.register<InAppRepository>().provides<IInAppRepository>()
            builder.register<InAppBackendService>().provides<IInAppBackendService>()
            builder.register<IAMLifecycleService>().provides<IInAppLifecycleService>()

            // Triggers
            builder.register<TriggerModelStore>().provides<TriggerModelStore>()
            builder.register<TriggerController>().provides<ITriggerController>()
            builder.register<DynamicTriggerController>()
                .provides<DynamicTriggerController>() // observer

            // Display
            builder.register<InAppDisplayer>().provides<IInAppDisplayer>()

            // Previews
            builder.register<InAppMessagePreviewHandler>().provides<IBootstrapService>()

            // Prompts
            builder.register<InAppMessagePromptFactory>().provides<IInAppMessagePromptFactory>()

            builder.register<InAppMessagesManager>()
                .provides<IInAppMessagesManager>()
                .provides<IStartableService>()
        }
    }
}
