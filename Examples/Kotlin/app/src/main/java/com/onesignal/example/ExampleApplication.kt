package com.onesignal.example

import android.app.Application
import android.content.Context
import com.onesignal.OneSignal

class ExampleApplication : Application() {

    companion object {
        lateinit var instance: Context private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this;

        // Logging set to help debug issues, remove before releasing your app.
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.WARN)

        OneSignal.startInit(this)
                .setNotificationOpenedHandler(ExampleNotificationOpenedHandler())
                .autoPromptLocation(true)
                .init()
    }

}
