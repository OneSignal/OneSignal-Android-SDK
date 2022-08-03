package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.internal.backend.InAppBackendController
import com.onesignal.onesignal.iam.internal.data.InAppDataController
import com.onesignal.onesignal.iam.internal.display.IAMDisplayer
import com.onesignal.onesignal.iam.internal.lifecycle.IIAMLifecycleService
import com.onesignal.onesignal.iam.internal.lifecycle.impl.IAMLifecycleService
import com.onesignal.onesignal.iam.internal.preferences.InAppPreferencesController
import com.onesignal.onesignal.iam.internal.preview.InAppMessagePreviewHandler
import com.onesignal.onesignal.iam.internal.triggers.DynamicTriggerController
import com.onesignal.onesignal.iam.internal.triggers.TriggerController

object IAMModule {
    fun register(builder: ServiceBuilder) {
        builder.register<InAppPreferencesController>().provides<InAppPreferencesController>()
        builder.register<InAppDataController>().provides<InAppDataController>()
        builder.register<InAppBackendController>().provides<InAppBackendController>()
        builder.register<TriggerController>().provides<TriggerController>()
        builder.register<DynamicTriggerController>().provides<DynamicTriggerController>() // observer
        builder.register<IAMDisplayer>().provides<IAMDisplayer>()

        builder.register<IAMLifecycleService>().provides<IIAMLifecycleService>()

        builder.register<InAppMessagePreviewHandler>().provides<IStartableService>()

        builder.register<IAMManager>()
               .provides<IIAMManager>()
               .provides<IIAMDisplayer>()
               .provides<IStartableService>()

        // TODO: Only Android 4.4 can use IAMS (see OSInAppMessageDummyController)
    }
}