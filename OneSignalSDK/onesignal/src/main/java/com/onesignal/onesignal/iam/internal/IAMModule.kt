package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.iam.IIAMManager

object IAMModule {
    fun register(builder: ServiceBuilder) {
        builder.register<IAMManager>().provides<IIAMManager>()
    }
}