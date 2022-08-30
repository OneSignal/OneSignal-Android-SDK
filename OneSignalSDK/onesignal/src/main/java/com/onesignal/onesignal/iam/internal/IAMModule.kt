package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.internal.backend.IInAppBackendService
import com.onesignal.onesignal.iam.internal.backend.impl.InAppBackendService
import com.onesignal.onesignal.iam.internal.display.IInAppDisplayer
import com.onesignal.onesignal.iam.internal.display.impl.InAppDisplayer
import com.onesignal.onesignal.iam.internal.hydrators.InAppHydrator
import com.onesignal.onesignal.iam.internal.lifecycle.IInAppLifecycleService
import com.onesignal.onesignal.iam.internal.lifecycle.impl.IAMLifecycleService
import com.onesignal.onesignal.iam.internal.preferences.IInAppPreferencesController
import com.onesignal.onesignal.iam.internal.preferences.impl.InAppPreferencesController
import com.onesignal.onesignal.iam.internal.preview.InAppMessagePreviewHandler
import com.onesignal.onesignal.iam.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.onesignal.iam.internal.prompt.impl.InAppMessagePromptFactory
import com.onesignal.onesignal.iam.internal.repositories.IInAppRepository
import com.onesignal.onesignal.iam.internal.repositories.impl.InAppRepository
import com.onesignal.onesignal.iam.internal.state.InAppStateService
import com.onesignal.onesignal.iam.internal.triggers.ITriggerController
import com.onesignal.onesignal.iam.internal.triggers.impl.DynamicTriggerController
import com.onesignal.onesignal.iam.internal.triggers.impl.TriggerController

object IAMModule {
    fun register(builder: ServiceBuilder) {
        // Low level services
        builder.register<InAppStateService>().provides<InAppStateService>()
        builder.register<InAppHydrator>().provides<InAppHydrator>()
        builder.register<InAppPreferencesController>().provides<IInAppPreferencesController>()
        builder.register<InAppRepository>().provides<IInAppRepository>()
        builder.register<InAppBackendService>().provides<IInAppBackendService>()
        builder.register<IAMLifecycleService>().provides<IInAppLifecycleService>()

        // Triggers
        builder.register<TriggerController>().provides<ITriggerController>()
        builder.register<DynamicTriggerController>().provides<DynamicTriggerController>() // observer

        // Display
        builder.register<InAppDisplayer>().provides<IInAppDisplayer>()

        // Previews
        builder.register<InAppMessagePreviewHandler>().provides<IStartableService>()

        // Prompts
        builder.register<InAppMessagePromptFactory>().provides<IInAppMessagePromptFactory>()

        builder.register<IAMManager>()
            .provides<IIAMManager>()
            .provides<IStartableService>()

        // TODO: Only Android 4.4 can use IAMS (see OSInAppMessageDummyController)
    }
}
